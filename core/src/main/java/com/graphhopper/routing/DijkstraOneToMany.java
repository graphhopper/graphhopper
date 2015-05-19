/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing;

import com.graphhopper.coll.IntDoubleBinHeap;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.util.Arrays;

/**
 * A simple dijkstra tuned to perform one to many queries more efficient than Dijkstra. Old data
 * structures are cached between requests and potentially reused. Useful for CH preparation.
 * <p/>
 * @author Peter Karich
 */
public class DijkstraOneToMany extends AbstractRoutingAlgorithm
{
    private static final int EMPTY_PARENT = -1;
    private static final int NOT_FOUND = -1;

    private Container container;
    private IntDoubleBinHeap heap;
    private TIntSet visitedNodes;
    private boolean doClear = true;
    private int limitVisitedNodes = Integer.MAX_VALUE;
    private int currId, endId;
    private int fromNode, endNode, to;

    public DijkstraOneToMany( Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode )
    {
        super(graph, encoder, weighting, tMode);

        heap = new IntDoubleBinHeap();
        visitedNodes = new TIntHashSet();

        if(tMode.isEdgeBased())
            container = new EdgeBasedContainer(graph, tMode);
        else
            container = new NodeBasedContainer(graph);
    }

    public DijkstraOneToMany setLimitVisitedNodes( int nodes )
    {
        this.limitVisitedNodes = nodes;
        return this;
    }

    @Override
    public Path calcPath( int from, int to )
    {
        fromNode = from;
        endId = findEndId(from, to);
        if(endId != NOT_FOUND)
            endNode = container.node(endId);
        return extractPath();
    }

    @Override
    public Path extractPath()
    {
        OneToManyPath path = new OneToManyPath(graph, flagEncoder, container);
        if(endId == NOT_FOUND)
            return path;

        path.setFromNode(fromNode);
        path.setEndNode(endNode);
        path.setWeight(container.weight(endId));
        return path.setEndId(endId).extract();
    }

    /**
     * Call clear if you have a different start node and need to clear the cache.
     */
    public DijkstraOneToMany clear()
    {
        doClear = true;
        return this;
    }

    public double getWeight( int endNode )
    {
        int traversalId = container.getTraversalIdByNode(endNode);
        if(traversalId == -1)
            return Double.MAX_VALUE;
        return container.weight(traversalId);
    }

    public int findEndNode(int from, int to)
    {
        int endId = findEndId(from, to);
        if(endId == NOT_FOUND)
            return NOT_FOUND;
        return container.node(endId);
    }

    public int findEndId( int from, int to )
    {
        if(graph.getNodes() < 2)
            return NOT_FOUND;

        this.to = to;
        if (doClear)
        {
            doClear = false;
            container.clear();
            heap.clear();

            currId = traversalMode.createTraversalId(from, -1, EdgeIterator.NO_EDGE, false);
            container.put(from, -1, EdgeIterator.NO_EDGE, 0, EMPTY_PARENT);

        } else
        {
            int endId = container.getTraversalIdByNode(to);
            if(endId != -1 && container.parentId(endId) != EMPTY_PARENT && container.weight(endId) <= container.weight(currId))
                return endId;

            if (heap.isEmpty() || visitedNodes.size() >= limitVisitedNodes)
                return NOT_FOUND;

            currId = heap.poll_element();
        }

        visitedNodes.clear();
        if (finished())
            return currId;

        while (true)
        {
            int currNode = container.node(currId);
            visitedNodes.add(currNode);
            EdgeIterator iter = outEdgeExplorer.setBaseNode(currNode);
            while (iter.next())
            {
                int prevEdgeId = container.edge(currId);
                if (!accept(iter, prevEdgeId))
                    continue;
                double tmpWeight = weighting.calcWeight(iter, false, prevEdgeId) + container.weight(currId);
                if (Double.isInfinite(tmpWeight))
                    continue;

                int traversalId = traversalMode.createTraversalId(iter, false);
                double w = container.weight(traversalId);
                if (w == Double.MAX_VALUE)
                {
                    container.put(iter, tmpWeight, currId);
                    heap.insert_(tmpWeight, traversalId);

                } else if (w > tmpWeight)
                {
                    container.put(iter, tmpWeight, currId);
                    heap.update_(tmpWeight, traversalId);
                }
            }

            if (heap.isEmpty() || visitedNodes.size() >= limitVisitedNodes || isWeightLimitExceeded())
            {
                return NOT_FOUND;
            }

            // calling just peek and not poll is important if the next query is cached
            currId = heap.peek_element();
            if (finished())
                return currId;

            heap.poll_element();
        }
    }

    @Override
    public boolean finished()
    {
        return container.node(currId) == to;
    }

    @Override
    protected boolean isWeightLimitExceeded()
    {
        return container.weight(currId) > weightLimit;
    }

    public void close()
    {
        container = null;
        heap = null;
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedNodes.size();
    }

    @Override
    public String getName()
    {
        return AlgorithmOptions.DIJKSTRA_ONE_TO_MANY;
    }

    /**
     * List currently used memory in MB (approximatively)
     */
    public String getMemoryUsageAsString()
    {
        return container.getMemoryUsage()
                + (heap.getCapacity() * (4L + 4L))/ Helper.MB
                + "MB";
    }

    private static class TIntArrayListWithCap extends TIntArrayList
    {
        public int getCapacity()
        {
            return _data.length;
        }
    }

    /*
        We should use different structures for node/edge based modes.
        Edge-based mode requires much more space than node-based.
     */
    private interface Container
    {
        void put(EdgeIteratorState iter, double weight, int parentId);

        void put(int adjNode, int baseNode, int edgeId, double weight, int parentId);

        double weight(int traversalId);

        int edge(int traversalId);

        int node(int traversalId);

        int parentId(int traversalId);

        int getTraversalIdByNode(int node);

        void clear();

        /**
         * List currently used memory in MB (approximatively)
         */
        long getMemoryUsage();

    }

    private static class NodeBasedContainer implements Container
    {
        private final TraversalMode traversalMode = TraversalMode.NODE_BASED;

        private double[] weights;
        private int[] parents;
        private int[] edgeIds;

        private TIntArrayListWithCap changedNodes = new TIntArrayListWithCap();

        public NodeBasedContainer(Graph graph)
        {
            parents = new int[graph.getNodes()];
            Arrays.fill(parents, EMPTY_PARENT);

            edgeIds = new int[graph.getNodes()];
            Arrays.fill(edgeIds, EdgeIterator.NO_EDGE);

            weights = new double[graph.getNodes()];
            Arrays.fill(weights, Double.MAX_VALUE);
        }

        @Override
        public void put(EdgeIteratorState iter, double weight, int parentId)
        {
            put(iter.getAdjNode(), iter.getBaseNode(), iter.getEdge(), weight, parentId);
        }

        @Override
        public void put(int adjNode, int baseNode, int edgeId, double weight, int parentId)
        {
            int traversalId = traversalMode.createTraversalId(adjNode, baseNode, edgeId, false);
            parents[traversalId] = parentId;
            weights[traversalId] = weight;
            edgeIds[traversalId] = edgeId;
            changedNodes.add(traversalId);
        }

        @Override
        public int edge(int nodeId)
        {
           return edgeIds[nodeId];
        }

        @Override
        public int node(int traversalId)
        {
            return traversalId;
        }

        @Override
        public int parentId(int traversalId)
        {
            return parents[traversalId];
        }

        @Override
        public void clear()
        {
            int vn = changedNodes.size();
            for (int i = 0; i < vn; i++)
            {
                int n = changedNodes.get(i);
                weights[n] = Double.MAX_VALUE;
                parents[n] = EMPTY_PARENT;
                edgeIds[n] = EdgeIterator.NO_EDGE;
            }
            changedNodes.reset();
        }

        @Override
        public double weight(int nodeId)
        {
            return weights[nodeId];
        }

        @Override
        public long getMemoryUsage()
        {
            long len = weights.length;
            return ((8L + 4L + 4L) * len
                    + changedNodes.getCapacity() * 4L / Helper.MB);
        }

        @Override
        public int getTraversalIdByNode(int nodeId)
        {
            return nodeId;
        }
    }

    private static class EdgeBasedContainer implements Container
    {
        private final TraversalMode traversalMode;

        private double[] weights;
        private int[] parents;
        private int[] edgeIds;
        private int[] nodeIds;

        private int[] bestIdToNode;

        private TIntArrayListWithCap changedIds = new TIntArrayListWithCap();
        private TIntArrayListWithCap changedNodes = new TIntArrayListWithCap();


        public EdgeBasedContainer(Graph graph, TraversalMode trMode)
        {
            if(!trMode.isEdgeBased())
                throw new IllegalArgumentException("Traversal mode must be edgeBased");

            traversalMode = trMode;

            bestIdToNode = new int[graph.getNodes()];
            Arrays.fill(bestIdToNode, -1);

            int capacity = getInitialCapacity(graph);

            weights = new double[capacity];
            Arrays.fill(weights, Double.MAX_VALUE);

            parents = new int[capacity];
            Arrays.fill(parents, EMPTY_PARENT);

            edgeIds = new int[capacity];
            Arrays.fill(edgeIds, EdgeIterator.NO_EDGE);

            nodeIds = new int[capacity];
            Arrays.fill(nodeIds, -1);
        }

        private int getInitialCapacity(Graph g)
        {
            //TODO: QueryGraph does not support getAllEdges()
            if(g instanceof QueryGraph)
                return g.getNodes();

            int edges = g.getAllEdges().getCount();
            edges += +1;
            if(traversalMode.getNoOfStates() == 1)
                return edges;

            return ((edges << 1) * 1);
        }

        private void sizeCheck(int index)
        {
            if(index >= weights.length)
                grow(index + 1);
        }

        public void grow(int newSize)
        {
            int length = weights.length;
            if(newSize <= length)
                return;

            weights = Arrays.copyOf(weights, newSize);
            Arrays.fill(weights, length, newSize, Double.MAX_VALUE);

            parents = Arrays.copyOf(parents, newSize);
            Arrays.fill(parents, length, newSize, EMPTY_PARENT);

            edgeIds = Arrays.copyOf(edgeIds, newSize);
            Arrays.fill(edgeIds, length, newSize, EdgeIterator.NO_EDGE);

            nodeIds = Arrays.copyOf(nodeIds, newSize);
            Arrays.fill(nodeIds, length, newSize, -1);
        }

        @Override
        public void put(EdgeIteratorState iter, double weight, int parentId)
        {
            put(iter.getAdjNode(), iter.getBaseNode(), iter.getEdge(), weight, parentId);
        }

        @Override
        public void put(int adjNode, int baseNode, int edgeId, double weight, int parentId)
        {
            int traversalId = traversalMode.createTraversalId(adjNode, baseNode, edgeId, false);
            sizeCheck(traversalId);

            parents[traversalId] = parentId;
            weights[traversalId] = weight;
            edgeIds[traversalId] = edgeId;
            nodeIds[traversalId] = adjNode;
            changedIds.add(traversalId);

            if(parentId == EMPTY_PARENT)
                return;

            int previousId = bestIdToNode[adjNode];
            if(previousId == -1)
            {
                bestIdToNode[adjNode] = traversalId;
                changedNodes.add(adjNode);
            } else if(weight < weight(previousId))
            {
                bestIdToNode[adjNode] = traversalId;
            }
        }

        @Override
        public double weight(int traversalId)
        {
            sizeCheck(traversalId);
            return weights[traversalId];
        }

        @Override
        public int edge(int traversalId)
        {
            return edgeIds[traversalId];
        }

        @Override
        public int node(int travesalId)
        {
            return nodeIds[travesalId];
        }

        @Override
        public int parentId(int traversalId)
        {
            return parents[traversalId];
        }

        @Override
        public void clear()
        {
            int vn = changedIds.size();
            for (int i = 0; i < vn; i++)
            {
                int n = changedIds.get(i);
                weights[n] = Double.MAX_VALUE;
                parents[n] = EMPTY_PARENT;
                edgeIds[n] = EdgeIterator.NO_EDGE;
                nodeIds[n] = -1;
            }
            changedIds.reset();

            vn = changedNodes.size();
            for(int i = 0; i < vn; i++)
            {
                int n = changedNodes.get(i);
                bestIdToNode[n] = -1;
            }
            changedNodes.reset();
        }

        @Override
        public long getMemoryUsage()
        {
            int len = weights.length;
            //weights, parents, edgeIds, nodeIds
            long usage = (8L + 4L + 4L + 4L) * len;

            usage += 4L * bestIdToNode.length;

            usage += 4L * changedNodes.getCapacity();
            usage += 4L * changedIds.getCapacity();

            return usage / Helper.MB;
        }

        @Override
        public int getTraversalIdByNode(int nodeId)
        {
            return bestIdToNode[nodeId];
        }
    }

    private static class OneToManyPath extends Path
    {
        private final Container container;
        private int endId = -1;

        public OneToManyPath(Graph graph, FlagEncoder encoder, Container container)
        {
            super(graph, encoder);
            this.container = container;
        }

        public OneToManyPath setEndId(int endId)
        {
            this.endId = endId;
            return this;
        }

        @Override
        public Path extract()
        {
            if(endId < 0)
                return this;

            int currentId = endId;
            while(true)
            {
                int edgeId = container.edge(currentId);
                int nodeId = container.node(currentId);

                if (!EdgeIterator.Edge.isValid(edgeId))
                    break;

                processEdge(edgeId, nodeId);
                currentId = container.parentId(currentId);
            }
            reverseOrder();
            return setFound(true);
        }
    }
}
