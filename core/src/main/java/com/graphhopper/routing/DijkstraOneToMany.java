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

import com.graphhopper.core.util.Parameters;
import com.graphhopper.core.util.Helper;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.apache.commons.collections.IntFloatBinaryHeap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.ArrayUtil;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.Arrays;

/**
 * A simple dijkstra tuned to perform multiple one to many queries with the same source and different target nodes
 * more efficiently than {@link Dijkstra}. Old data structures are cached between requests and potentially reused and
 * the shortest path tree is stored in (large as the graph) arrays instead of hash maps.
 * <p>
 *
 * @author Peter Karich
 */
public class DijkstraOneToMany extends AbstractRoutingAlgorithm {
    private static final int EMPTY_PARENT = -1;
    private static final int NOT_FOUND = -1;
    private final IntArrayListWithCap changedNodes;
    protected double[] weights;
    private int[] parents;
    private int[] edgeIds;
    private IntFloatBinaryHeap heap;
    private int visitedNodes;
    private boolean doClear = true;
    private int endNode;
    private int currNode, fromNode, to;
    private double weightLimit = Double.MAX_VALUE;

    public DijkstraOneToMany(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);

        parents = new int[graph.getNodes()];
        Arrays.fill(parents, EMPTY_PARENT);

        edgeIds = new int[graph.getNodes()];
        Arrays.fill(edgeIds, EdgeIterator.NO_EDGE);

        weights = new double[graph.getNodes()];

        Arrays.fill(weights, Double.MAX_VALUE);

        heap = new IntFloatBinaryHeap(1000);
        changedNodes = new IntArrayListWithCap();
    }

    @Override
    public Path calcPath(int from, int to) {
        fromNode = from;
        endNode = findEndNode(from, to);
        if (endNode < 0 || isWeightLimitExceeded()) {
            Path path = createEmptyPath();
            path.setFromNode(fromNode);
            path.setEndNode(endNode);
            return path;
        }

        Path path = new Path(graph);
        int node = endNode;
        while (true) {
            int edge = edgeIds[node];
            if (!EdgeIterator.Edge.isValid(edge)) {
                break;
            }
            EdgeIteratorState edgeState = graph.getEdgeIteratorState(edge, node);
            path.addDistance(edgeState.getDistance());
            // todo: we do not yet account for turn times here!
            path.addTime(weighting.calcEdgeMillis(edgeState, false));
            path.addEdge(edge);
            node = parents[node];
        }
        ArrayUtil.reverse(path.getEdges());
        path.setFromNode(fromNode);
        path.setEndNode(endNode);
        path.setFound(true);
        path.setWeight(weights[endNode]);
        return path;
    }

    /**
     * Call clear if you have a different start node and need to clear the cache.
     */
    public DijkstraOneToMany clear() {
        doClear = true;
        return this;
    }

    public double getWeight(int endNode) {
        return weights[endNode];
    }

    public int findEndNode(int from, int to) {
        if (weights.length < 2)
            return NOT_FOUND;

        this.to = to;
        if (doClear) {
            doClear = false;
            int vn = changedNodes.size();
            for (int i = 0; i < vn; i++) {
                int n = changedNodes.get(i);
                weights[n] = Double.MAX_VALUE;
                parents[n] = EMPTY_PARENT;
                edgeIds[n] = EdgeIterator.NO_EDGE;
            }

            heap.clear();

            // changedNodes.clear();
            changedNodes.elementsCount = 0;

            currNode = from;
            if (!traversalMode.isEdgeBased()) {
                weights[currNode] = 0;
                changedNodes.add(currNode);
            }
        } else {
            // Cached! Re-use existing data structures
            int parentNode = parents[to];
            if (parentNode != EMPTY_PARENT && weights[to] <= weights[currNode])
                return to;

            if (heap.isEmpty() || isMaxVisitedNodesExceeded())
                return NOT_FOUND;

            currNode = heap.poll();
        }

        visitedNodes = 0;

        // we call 'finished' before heap.peekElement but this would add unnecessary overhead for this special case so we do it outside of the loop
        if (finished()) {
            // then we need a small workaround for special cases see #707
            if (heap.isEmpty())
                doClear = true;
            return currNode;
        }

        while (true) {
            visitedNodes++;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            while (iter.next()) {
                int adjNode = iter.getAdjNode();
                int prevEdgeId = edgeIds[adjNode];
                if (!accept(iter, prevEdgeId))
                    continue;

                double tmpWeight = GHUtility.calcWeightWithTurnWeightWithAccess(weighting, iter, false, prevEdgeId) + weights[currNode];
                if (Double.isInfinite(tmpWeight))
                    continue;

                double w = weights[adjNode];
                if (w == Double.MAX_VALUE) {
                    parents[adjNode] = currNode;
                    weights[adjNode] = tmpWeight;
                    heap.insert(tmpWeight, adjNode);
                    changedNodes.add(adjNode);
                    edgeIds[adjNode] = iter.getEdge();

                } else if (w > tmpWeight) {
                    parents[adjNode] = currNode;
                    weights[adjNode] = tmpWeight;
                    heap.update(tmpWeight, adjNode);
                    changedNodes.add(adjNode);
                    edgeIds[adjNode] = iter.getEdge();
                }
            }

            if (heap.isEmpty() || isMaxVisitedNodesExceeded() || isWeightLimitExceeded())
                return NOT_FOUND;

            // calling just peek and not poll is important if the next query is cached
            currNode = heap.peekElement();
            if (finished())
                return currNode;

            heap.poll();
        }
    }

    private boolean finished() {
        return currNode == to;
    }

    public void setWeightLimit(double weightLimit) {
        this.weightLimit = weightLimit;
    }

    protected boolean isWeightLimitExceeded() {
        return weights[currNode] > weightLimit;
    }

    public void close() {
        weights = null;
        parents = null;
        edgeIds = null;
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
        long len = weights.length;
        return ((8L + 4L + 4L) * len
                + changedNodes.getCapacity() * 4L
                + heap.getCapacity() * (4L + 4L)) / Helper.MB
                + "MB";
    }

    private static class IntArrayListWithCap extends IntArrayList {
        public IntArrayListWithCap() {
        }

        public int getCapacity() {
            return buffer.length;
        }
    }
}
