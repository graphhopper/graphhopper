/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
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

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.apache.commons.collections.IntDoubleBinaryHeap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

import java.util.Arrays;

/**
 * A simple dijkstra tuned to perform one to many queries more efficient than Dijkstra. Old data
 * structures are cached between requests and potentially reused. Useful for CH preparation.
 * <p>
 *
 * @author Peter Karich
 */
public class DijkstraOneToMany extends AbstractRoutingAlgorithm {
    private static final int EMPTY_PARENT = -1;
    private static final int NOT_FOUND = -1;
    private StructuresContainer container;
    private IntDoubleBinaryHeap heap;
    private int visitedNodes;
    private boolean doClear = true;
    private int endNode, endId;
    private int currId, fromNode, to;
    private double weightLimit = Double.MAX_VALUE;

    public DijkstraOneToMany(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);

        int entityCount = graph.getNodes();
        if (tMode.isEdgeBased()) {
            entityCount = GHUtility.count(graph.getAllEdges()) * tMode.getNoOfStates();
        }

        if (tMode.isEdgeBased())
            container = new EdgeBasedStructuresContainer(graph, tMode);
        else
            container = new NodeBasedStructuresContainer(graph);

        heap = new IntDoubleBinaryHeap(1000);
    }

    @Override
    public Path calcPath(int from, int to) {
        fromNode = from;
        endId = findEndId(from, to);
        if (endId != NOT_FOUND)
            endNode = container.getNode(endId);
        return extractPath();
    }

    @Override
    public Path extractPath() {
        LocalPath path = new LocalPath(graph, weighting, container);
        if (endId == NOT_FOUND || isWeightLimitExceeded())
            return path;

        path.setFromNode(fromNode);
        path.setEndNode(endNode);
        path.setWeight(container.getWeight(endId));
        path.setEndId(endId);
        return path.extract();
    }

    /**
     * Call clear if you have a different start node and need to clear the cache.
     */
    public DijkstraOneToMany clear() {
        doClear = true;
        return this;
    }

    public double getWeight(int endNode) {
        return container.getBestWeight(endNode);
    }

    public int findEndNode(int from, int to) {
        int endId = findEndId(from, to);
        if (endId == NOT_FOUND)
            return NOT_FOUND;
        return container.getNode(endId);
    }

    public int findEndId(int from, int to) {
        if (graph.getNodes() < 2)
            return NOT_FOUND;

        this.to = to;
        if (doClear) {
            doClear = false;
            container.clear();
            heap.clear();

            currId = traversalMode.createTraversalId(-1, from, EdgeIterator.NO_EDGE, false);
            container.put(-1, from, EdgeIterator.NO_EDGE, 0, EMPTY_PARENT);
        } else {
            // Cached! Re-use existing data structures
            int endId = container.getTraversalIdByNode(to);
            if (endId != -1 && container.getParentId(endId) != EMPTY_PARENT && container.getWeight(endId) <= container.getWeight(currId))
                return endId;

            if (heap.isEmpty() || isMaxVisitedNodesExceeded())
                return NOT_FOUND;

            currId = heap.poll_element();
        }

        visitedNodes = 0;

        // we call 'finished' before heap.peek_element but this would add unnecessary overhead for this special case so we do it outside of the loop
        if (finished()) {
            // then we need a small workaround for special cases see #707
            if (heap.isEmpty())
                doClear = true;
            return currId;
        }

        while (true) {
            visitedNodes++;
            int currNode = container.getNode(currId);
            EdgeIterator iter = outEdgeExplorer.setBaseNode(currNode);

            while (iter.next()) {
                int prevEdgeId = container.getEdge(currId);
                if (!accept(iter, prevEdgeId))
                    continue;

                double tmpWeight = weighting.calcWeight(iter, false, prevEdgeId) + container.getWeight(currId);
                if (Double.isInfinite(tmpWeight))
                    continue;

                int traversalId = traversalMode.createTraversalId(iter, false);
                double w = container.getWeight(traversalId);
                if (w == Double.MAX_VALUE) {
                    container.put(iter, tmpWeight, currId);
                    heap.insert_(tmpWeight, traversalId);
                } else if (w > tmpWeight) {
                    container.put(iter, tmpWeight, currId);
                    heap.update_(tmpWeight, traversalId);
                }
            }

            if (heap.isEmpty() || isMaxVisitedNodesExceeded() || isWeightLimitExceeded())
                return NOT_FOUND;

            // calling just peek and not poll is important if the next query is cached
            currId = heap.peek_element();
            if (finished())
                return currId;

            heap.poll_element();
        }
    }

    @Override
    public boolean finished() {
        return container.getNode(currId) == to;
    }

    public void setWeightLimit(double weightLimit) {
        this.weightLimit = weightLimit;
    }

    protected boolean isWeightLimitExceeded() {
        return container.getWeight(currId) > weightLimit;
    }

    public void close() {
        container.close();
        heap = null;
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.DIJKSTRA_ONE_TO_MANY;
    }

    /**
     * List currently used memory in MB (approximately)
     */
    public String getMemoryUsageAsString() {
        return container.getMemoryUsage() + (heap.getCapacity() * (4L + 4L)) / Helper.MB + "MB";
    }

    private static class IntArrayListWithCap extends IntArrayList {
        public IntArrayListWithCap() {
        }

        public int getCapacity() {
            return buffer.length;
        }
    }



    private static interface StructuresContainer {
        void put(EdgeIteratorState iter, double weight, int parentId);

        void put(int baseNode, int adjNode, int edgeId, double weight, int parentId);

        double getWeight(int traversalId);

        int getEdge(int traversalId);

        int getNode(int travesalId);

        int getParentId(int traversalId);

        double getBestWeight(int nodeId);

        int getTraversalIdByNode(int traversalId);

        void clear();

        void close();

        /**
         * List currently used memory in MB (approximatively)
         */
        long getMemoryUsage();

    }

    private static class NodeBasedStructuresContainer implements StructuresContainer
    {
        private final TraversalMode traversalMode = TraversalMode.NODE_BASED;

        private double[] weights;
        private int[] parents;
        private int[] edgeIds;

        private IntArrayListWithCap changedNodes = new IntArrayListWithCap();

        public NodeBasedStructuresContainer(Graph graph)
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
            put(iter.getBaseNode(), iter.getAdjNode(), iter.getEdge(), weight, parentId);
        }

        @Override
        public void put(int baseNode, int adjNode, int edgeId, double weight, int parentId)
        {
            int traversalId = traversalMode.createTraversalId(baseNode, adjNode, edgeId, false);
            parents[traversalId] = parentId;
            weights[traversalId] = weight;
            edgeIds[traversalId] = edgeId;
            changedNodes.add(traversalId);
        }

        @Override
        public int getEdge(int nodeId)
        {
            return edgeIds[nodeId];
        }

        @Override
        public int getNode(int travesalId)
        {
            return travesalId;
        }

        @Override
        public int getParentId(int traversalId)
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
            changedNodes.elementsCount = 0;
        }

        @Override
        public void close()
        {
            weights = null;
            parents = null;
            edgeIds = null;
        }

        @Override
        public double getWeight(int nodeId)
        {
            return weights[nodeId];
        }

        @Override
        public double getBestWeight(int nodeId)
        {
            return getWeight(nodeId);
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

    /** The amount of edges may change during contraction. Hence, this container needs to be able to grow */
    private static class EdgeBasedStructuresContainer implements StructuresContainer {
        private final TraversalMode traversalMode;

        private DoubleArrayList weights;
        private IntArrayList parents;
        private IntArrayList edgeIds;
        private IntArrayList nodeIds;

        private int[] bestIdToNode;

        private IntArrayListWithCap changedIds = new IntArrayListWithCap();
        private IntArrayListWithCap changedNodes = new IntArrayListWithCap();


        public EdgeBasedStructuresContainer(Graph graph, TraversalMode trMode)
        {
            if(!trMode.isEdgeBased())
                throw new IllegalArgumentException("Traversal mode must be edgeBased");

            traversalMode = trMode;

            bestIdToNode = new int[graph.getNodes()];
            Arrays.fill(bestIdToNode, -1);

            // need +1 to handle the initial edge with id -1 (all traveral Ids will be shifted by 1 internally)
            int capacity = (graph.getAllEdges().getMaxId() + 2) * traversalMode.getNoOfStates();

            weights = new DoubleArrayList(0);
            parents = new IntArrayList(0);
            edgeIds = new IntArrayList(0);
            nodeIds = new IntArrayList(0);
            ensureIndex(capacity);
       }

        private int traversalIdToIndex(int traversalId) {
            return traversalId + 2;
        }

        private int indexToTraversalId(int index) {
            return index - 2;
        }

        private void ensureIndex(int index) {
            if (weights.size() > index)
                return;

            int addedStart = weights.size();
            int size = index + 1;
            if (index >= weights.buffer.length) {
                weights.resize(size);
                parents.resize(size);
                edgeIds.resize(size);
                nodeIds.resize(size);
            } else {
                weights.elementsCount = size;
                parents.elementsCount = size;
                edgeIds.elementsCount = size;
                nodeIds.elementsCount = size;
            }

            Arrays.fill(weights.buffer, addedStart, size, Double.MAX_VALUE);
            Arrays.fill(parents.buffer, addedStart, size, EMPTY_PARENT);
            Arrays.fill(edgeIds.buffer, addedStart, size, EdgeIterator.NO_EDGE);
            Arrays.fill(nodeIds.buffer, addedStart, size, -1);
        }

        @Override
        public void put(EdgeIteratorState iter, double weight, int parentId)
        {
            put(iter.getBaseNode(), iter.getAdjNode(), iter.getEdge(), weight, parentId);
        }

        @Override
        public void put(int baseNode, int adjNode, int edgeId, double weight, int parentId)
        {
            int traversalId = traversalMode.createTraversalId(baseNode, adjNode, edgeId, false);
            int index = traversalIdToIndex(traversalId);
            ensureIndex(index);

            parents.set(index, parentId);
            weights.set(index, weight);
            edgeIds.set(index, edgeId);
            nodeIds.set(index, adjNode);
            changedIds.add(index);

            if(parentId == EMPTY_PARENT)
                return;

            int previousId = bestIdToNode[adjNode];
            if(previousId == -1)
            {
                bestIdToNode[adjNode] = traversalId;
                changedNodes.add(adjNode);
            } else if(weight < getWeight(previousId))
            {
                bestIdToNode[adjNode] = traversalId;
            }
        }

        @Override
        public double getWeight(int traversalId)
        {
            int index = traversalIdToIndex(traversalId);
            ensureIndex(index);
            return weights.get(index);
        }

        @Override
        public int getEdge(int traversalId)
        {
            int index = traversalIdToIndex(traversalId);
            ensureIndex(index);
            return edgeIds.get(index);
        }

        @Override
        public int getNode(int traversalId)
        {
            int index = traversalIdToIndex(traversalId);
            ensureIndex(index);
            return nodeIds.get(index);
        }

        @Override
        public int getParentId(int traversalId)
        {
            int index = traversalIdToIndex(traversalId);
            ensureIndex(index);
            return parents.get(index);
        }

        @Override
        public double getBestWeight(int nodeId)
        {
            return getWeight(bestIdToNode[nodeId]);
        }

        @Override
        public void clear()
        {
            int vn = changedIds.size();
            for (int i = 0; i < vn; i++)
            {
                int n = changedIds.get(i);
                weights.set(n, Double.MAX_VALUE);
                parents.set(n, EMPTY_PARENT);
                edgeIds.set(n, EdgeIterator.NO_EDGE);
                nodeIds.set(n, -1);
            }
            changedIds.elementsCount = 0;

            vn = changedNodes.size();
            for(int i = 0; i < vn; i++)
            {
                int n = changedNodes.get(i);
                bestIdToNode[n] = -1;
            }
            changedNodes.clear();
        }

        @Override
        public void close()
        {
            weights = null;
            parents = null;
            edgeIds = null;
            nodeIds = null;
            bestIdToNode = null;
        }

        @Override
        public long getMemoryUsage()
        {
            int len = weights.buffer.length;
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

    private static class LocalPath extends Path
    {
        private final StructuresContainer container;
        private int endId = -1;

        public LocalPath(Graph graph, Weighting weighting, StructuresContainer container)
        {
            super(graph, weighting);
            this.container = container;
        }

        public LocalPath setEndId(int endId)
        {
            this.endId = endId;
            return this;
        }

        @Override
        public Path extract()
        {
            if(endId < 0)
                return this;

            int tailId = endId;
            while(true)
            {
                int edgeId = container.getEdge(tailId);
                int nodeId = container.getNode(tailId);

                if (!EdgeIterator.Edge.isValid(edgeId))
                    break;

                // TODO: tailId correct?
                processEdge(edgeId, nodeId, tailId);
                tailId = container.getParentId(tailId);
            }
            reverseOrder();
            return setFound(true);
        }
    }
}
