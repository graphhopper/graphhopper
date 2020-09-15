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
    private static final int NOT_FOUND = -1;
    private PrepareGraphEdgeExplorer outEdgeExplorer;
    private final IntArrayList changedNodes;
    private int maxVisitedNodes = Integer.MAX_VALUE;
    protected double[] weights;
    private IntFloatBinaryHeap heap;
    private int ignoreNode = -1;
    private int visitedNodes;
    private boolean doClear = true;
    private int currNode, to;
    private double weightLimit = Double.MAX_VALUE;

    public NodeBasedWitnessPathSearcher(CHPreparationGraph graph) {
        outEdgeExplorer = graph.createOutEdgeExplorer();

        weights = new double[graph.getNodes()];
        Arrays.fill(weights, Double.MAX_VALUE);

        heap = new IntFloatBinaryHeap(1000);
        changedNodes = new IntArrayList();
    }

    /**
     * Call clear if you have a different start node and need to clear the cache.
     */
    public NodeBasedWitnessPathSearcher clear() {
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
            }

            heap.clear();

            // changedNodes.clear();
            changedNodes.elementsCount = 0;

            currNode = from;
            weights[currNode] = 0;
            changedNodes.add(currNode);
        } else {
            // Cached! Re-use existing data structures
            if (weights[to] != Double.MAX_VALUE && weights[to] <= weights[currNode])
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
            PrepareGraphEdgeIterator iter = outEdgeExplorer.setBaseNode(currNode);
            while (iter.next()) {
                int adjNode = iter.getAdjNode();
                if (!accept(iter))
                    continue;

                double tmpWeight = iter.getWeight() + weights[currNode];
                if (Double.isInfinite(tmpWeight))
                    continue;

                double w = weights[adjNode];
                if (w == Double.MAX_VALUE) {
                    weights[adjNode] = tmpWeight;
                    heap.insert(tmpWeight, adjNode);
                    changedNodes.add(adjNode);
                } else if (w > tmpWeight) {
                    weights[adjNode] = tmpWeight;
                    heap.update(tmpWeight, adjNode);
                    changedNodes.add(adjNode);
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

    public boolean finished() {
        return currNode == to;
    }

    public void setWeightLimit(double weightLimit) {
        this.weightLimit = weightLimit;
    }

    protected boolean isWeightLimitExceeded() {
        return weights[currNode] > weightLimit;
    }

    public void close() {
        outEdgeExplorer = null;
        changedNodes.release();
        weights = null;
        heap = null;
    }

    public int getVisitedNodes() {
        return visitedNodes;
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

    public void setMaxVisitedNodes(int numberOfNodes) {
        this.maxVisitedNodes = numberOfNodes;
    }

    public void ignoreNode(int node) {
        ignoreNode = node;
    }

    private boolean accept(PrepareGraphEdgeIterator iter) {
        return ignoreNode < 0 || iter.getAdjNode() != ignoreNode;
    }

    private boolean isMaxVisitedNodesExceeded() {
        return maxVisitedNodes < getVisitedNodes();
    }
}
