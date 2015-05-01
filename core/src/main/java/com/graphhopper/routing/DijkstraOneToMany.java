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
import com.graphhopper.util.Helper;
import gnu.trove.list.array.TIntArrayList;
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
    protected double[] weights;
    private final TIntArrayListWithCap changedNodes;
    private int[] parents;
    private int[] edgeIds;
    private IntDoubleBinHeap heap;
    private int visitedNodes;
    private boolean doClear = true;
    private int limitVisitedNodes = Integer.MAX_VALUE;
    private int endNode;
    private int currNode, fromNode, to;

    public DijkstraOneToMany( Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode )
    {
        super(graph, encoder, weighting, tMode);

        parents = new int[graph.getNodes()];
        Arrays.fill(parents, EMPTY_PARENT);

        edgeIds = new int[graph.getNodes()];
        Arrays.fill(edgeIds, EdgeIterator.NO_EDGE);

        weights = new double[graph.getNodes()];

        Arrays.fill(weights, Double.MAX_VALUE);

        heap = new IntDoubleBinHeap();
        changedNodes = new TIntArrayListWithCap();
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
        endNode = findEndNode(from, to);
        return extractPath();
    }

    @Override
    public Path extractPath()
    {
        PathNative p = new PathNative(graph, flagEncoder, parents, edgeIds);
        if (endNode >= 0)
            p.setWeight(weights[endNode]);
        p.setFromNode(fromNode);
        if (endNode < 0 || isWeightLimitExceeded())
            return p;

        return p.setEndNode(endNode).extract();
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
        return weights[endNode];
    }

    public int findEndNode( int from, int to )
    {
        if (weights.length < 2)
            return NOT_FOUND;

        this.to = to;
        if (doClear)
        {
            doClear = false;
            int vn = changedNodes.size();
            for (int i = 0; i < vn; i++)
            {
                int n = changedNodes.get(i);
                weights[n] = Double.MAX_VALUE;
                parents[n] = EMPTY_PARENT;
                edgeIds[n] = EdgeIterator.NO_EDGE;
            }

            heap.clear();
            changedNodes.reset();

            currNode = from;
            if (!traversalMode.isEdgeBased())
            {
                weights[currNode] = 0;
                changedNodes.add(currNode);
            }
        } else
        {
            // Cached! Re-use existing data structures
            int parentNode = parents[to];
            if (parentNode != EMPTY_PARENT && weights[to] <= weights[currNode])
                return to;

            if (heap.isEmpty() || visitedNodes >= limitVisitedNodes)
                return NOT_FOUND;

            currNode = heap.poll_element();
        }

        visitedNodes = 0;
        if (finished())
            return currNode;

        while (true)
        {
            visitedNodes++;
            EdgeIterator iter = outEdgeExplorer.setBaseNode(currNode);
            while (iter.next())
            {
                int adjNode = iter.getAdjNode();
                int prevEdgeId = edgeIds[adjNode];
                if (!accept(iter, prevEdgeId))
                    continue;

                double tmpWeight = weighting.calcWeight(iter, false, prevEdgeId) + weights[currNode];
                if (Double.isInfinite(tmpWeight))
                    continue;

                double w = weights[adjNode];
                if (w == Double.MAX_VALUE)
                {
                    parents[adjNode] = currNode;
                    weights[adjNode] = tmpWeight;
                    heap.insert_(tmpWeight, adjNode);
                    changedNodes.add(adjNode);
                    edgeIds[adjNode] = iter.getEdge();

                } else if (w > tmpWeight)
                {
                    parents[adjNode] = currNode;
                    weights[adjNode] = tmpWeight;
                    heap.update_(tmpWeight, adjNode);
                    changedNodes.add(adjNode);
                    edgeIds[adjNode] = iter.getEdge();
                }
            }

            if (heap.isEmpty() || visitedNodes >= limitVisitedNodes || isWeightLimitExceeded())
                return NOT_FOUND;

            // calling just peek and not poll is important if the next query is cached
            currNode = heap.peek_element();
            if (finished())
                return currNode;

            heap.poll_element();
        }
    }

    @Override
    public boolean finished()
    {
        return currNode == to;
    }

    @Override
    protected boolean isWeightLimitExceeded()
    {
        return weights[currNode] > weightLimit;
    }

    public void close()
    {
        weights = null;
        parents = null;
        edgeIds = null;
        heap = null;
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedNodes;
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
        long len = weights.length;
        return ((8L + 4L + 4L) * len
                + changedNodes.getCapacity() * 4L
                + heap.getCapacity() * (4L + 4L)) / Helper.MB
                + "MB";
    }

    private static class TIntArrayListWithCap extends TIntArrayList
    {
        public int getCapacity()
        {
            return _data.length;
        }
    }
}
