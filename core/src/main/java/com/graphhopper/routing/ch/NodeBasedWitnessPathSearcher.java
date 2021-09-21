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
package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.apache.commons.collections.IntFloatBinaryHeap;
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.util.Helper;

import java.util.Arrays;

/**
 * Used to find witness paths during node-based CH preparation. Essentially this is like {@link DijkstraOneToMany},
 * i.e. its a Dijkstra search that allows re-using the shortest path tree for different searches with the same origin
 * node and uses large int/double arrays instead of hash maps to store the shortest path tree (higher memory consumption,
 * but faster query times -> better for CH preparation). Main reason we use this instead of {@link DijkstraOneToMany}
 * is that we can use this implementation with a {@link CHPreparationGraph} and we are only interested in checking for
 * witness paths (e.g. we do not need to find the actual path).
 */
public class NodeBasedWitnessPathSearcher {
    private final PrepareGraphEdgeExplorer outEdgeExplorer;
    private final double[] weights;
    private final IntArrayList changedNodes;
    private final IntFloatBinaryHeap heap;
    private int startNode = -1;
    private int ignoreNode = -1;
    private int settledNodes;

    public NodeBasedWitnessPathSearcher(CHPreparationGraph graph) {
        outEdgeExplorer = graph.createOutEdgeExplorer();

        weights = new double[graph.getNodes()];
        Arrays.fill(weights, Double.MAX_VALUE);

        heap = new IntFloatBinaryHeap(1000);
        changedNodes = new IntArrayList();
    }

    public void init(int startNode, int ignoreNode) {
        this.startNode = startNode;
        this.ignoreNode = ignoreNode;
        for (IntCursor c : changedNodes)
            weights[c.value] = Double.MAX_VALUE;
        changedNodes.elementsCount = 0;
        // todo
//        int vn = changedNodes.size();
//        for (int i = 0; i < vn; i++) {
//            int n = changedNodes.get(i);
//            weights[n] = Double.MAX_VALUE;
//        }
        heap.clear();
        weights[startNode] = 0;
        changedNodes.add(startNode);
        heap.insert(0, startNode);
    }

    /**
     * Runs a Dijkstra search from the startNode given in init() to the given targetNode and returns an upper bound
     * for the shortest path weight. The ignoreNode given in init() will be excluded from the search.
     * <p>
     * However, the search will stop as soon as *any* path from start to target has been found such that
     * the weight is below or equal to the accepted weight.
     *
     * @param targetNode
     * @param acceptedWeight
     * @param maxSettledNodes
     * @return
     */
    public double findUpperBoundShortestPathWeight(int targetNode, double acceptedWeight, int maxSettledNodes) {
        int visitedNodes = 0;
        while (!heap.isEmpty() && visitedNodes < maxSettledNodes && heap.peekKey() <= acceptedWeight) {
            if (weights[targetNode] <= acceptedWeight)
                // we found *a* path to the target node (not necessarily the shortest), and the weight is acceptable, so we stop
                return weights[targetNode];

            int node = heap.poll();
            PrepareGraphEdgeIterator iter = outEdgeExplorer.setBaseNode(node);
            while (iter.next()) {
                int adjNode = iter.getAdjNode();
                if (adjNode == ignoreNode)
                    continue;

                double weight = weights[node] + iter.getWeight();
                if (Double.isInfinite(weight))
                    continue;

                double adjWeight = weights[adjNode];
                if (adjWeight == Double.MAX_VALUE) {
                    weights[adjNode] = weight;
                    heap.insert(weight, adjNode);
                    changedNodes.add(adjNode);
                } else if (weight < adjWeight) {
                    weights[adjNode] = weight;
                    heap.update(weight, adjNode);
                }
            }

            visitedNodes++;
            if (node == targetNode)
                // we have settled the target node, we now know the exact weight of the shortest path and return
                return weights[node];
        }

        return weights[targetNode];
    }

    /**
     * List currently used memory in MB (approximately)
     */
    public String getMemoryUsageAsString() {
        long len = weights.length;
        return ((8L + 4L + 4L) * len
                + changedNodes.buffer.length * 4L
                + heap.getCapacity() * (4L + 4L)) / Helper.MB
                + "MB";
    }
}
