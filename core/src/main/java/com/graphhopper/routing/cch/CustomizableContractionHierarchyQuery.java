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
package com.graphhopper.routing.cch;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.Path;
import com.graphhopper.util.GHUtility;

import java.util.Arrays;

import static com.graphhopper.routing.cch.CustomizableContractionHierarchy.INF;
import static com.graphhopper.routing.cch.CustomizableContractionHierarchy.satAdd;

/**
 * Elimination-tree query on a customized CCH. Both searches walk parent pointers toward the root
 * and relax upward arcs. The meeting node is the ancestor where the forward and backward labels
 * sum to the shortest-path weight. Shortcuts are unpacked through the witness stored at customization.
 */
public final class CustomizableContractionHierarchyQuery {
    private static final int NO_WITNESS = -1;
    private static final int NO_NODE = -1;

    private final CustomizableContractionHierarchyMetric metric;

    public CustomizableContractionHierarchyQuery(CustomizableContractionHierarchyMetric metric) {
        if (metric == null)
            throw new IllegalArgumentException("CCH metric must not be null");
        this.metric = metric;
    }

    public Path run(int source, int target) {
        CustomizableContractionHierarchy cch = metric.cch;
        if (source < 0 || target < 0 || source >= cch.nodes || target >= cch.nodes)
            throw new IllegalArgumentException("Invalid node ids: " + source + " -> " + target + ", nodes: " + cch.nodes);
        if (metric.forward.length != cch.upHead.length)
            throw new IllegalStateException("Customize the CCH metric before querying");

        Path path = new Path(cch.graph);
        if (source == target) {
            path.setFromNode(source).setEndNode(target).setWeight(0).setFound(true);
            return path;
        }

        int nodes = cch.nodes;
        long[] forwardDist = new long[nodes];
        long[] backwardDist = new long[nodes];
        Arrays.fill(forwardDist, INF);
        Arrays.fill(backwardDist, INF);
        int[] predForward = new int[nodes];
        int[] predBackward = new int[nodes];
        Arrays.fill(predForward, NO_NODE);
        Arrays.fill(predBackward, NO_NODE);
        forwardDist[source] = 0;
        backwardDist[target] = 0;

        for (int node = source; node != NO_NODE; node = cch.eliminationTreeParent[node])
            relaxUpward(node, forwardDist, predForward, metric.forward);
        for (int node = target; node != NO_NODE; node = cch.eliminationTreeParent[node])
            relaxUpward(node, backwardDist, predBackward, metric.backward);

        long best = INF;
        int meet = NO_NODE;
        for (int node = source; node != NO_NODE; node = cch.eliminationTreeParent[node]) {
            if (forwardDist[node] == INF || backwardDist[node] == INF)
                continue;
            long sum = satAdd(forwardDist[node], backwardDist[node]);
            if (sum < best) {
                best = sum;
                meet = node;
            }
        }
        if (meet == NO_NODE)
            return path;

        IntArrayList edges = new IntArrayList();
        unpackUp(source, meet, predForward, edges);
        unpackDown(meet, target, predBackward, edges);
        path.setFromNode(source).setEndNode(target).setWeight(best).setFound(true);
        for (int i = 0; i < edges.size(); i++)
            path.addEdge(edges.get(i));
        return path;
    }

    private void relaxUpward(int node, long[] dist, int[] pred, long[] arcWeight) {
        long fromDist = dist[node];
        if (fromDist == INF)
            return;
        CustomizableContractionHierarchy cch = metric.cch;
        for (int arc = cch.upFirstOut[node]; arc < cch.upFirstOut[node + 1]; arc++) {
            long weight = arcWeight[arc];
            if (weight == INF)
                continue;
            int head = cch.upHead[arc];
            long next = satAdd(fromDist, weight);
            if (next < dist[head]) {
                dist[head] = next;
                pred[head] = node;
            }
        }
    }

    private void unpackUp(int source, int meet, int[] predForward, IntArrayList edges) {
        IntArrayList reversed = new IntArrayList();
        for (int node = meet; node != source; node = predForward[node]) {
            if (node == NO_NODE)
                throw new IllegalStateException("Broken forward CCH path to meeting node " + meet);
            reversed.add(node);
        }
        reversed.add(source);
        for (int i = reversed.size() - 1; i > 0; i--)
            unpackBetween(reversed.get(i), reversed.get(i - 1), edges);
    }

    private void unpackDown(int meet, int target, int[] predBackward, IntArrayList edges) {
        int node = meet;
        while (node != target) {
            int next = predBackward[node];
            if (next == NO_NODE)
                throw new IllegalStateException("Broken backward CCH path from meeting node " + meet);
            unpackBetween(node, next, edges);
            node = next;
        }
    }

    private void unpackBetween(int from, int to, IntArrayList edges) {
        int[] rank = metric.cch.rank;
        if (rank[from] < rank[to])
            unpackForward(from, to, edges);
        else if (rank[from] > rank[to])
            unpackBackward(to, from, edges);
        else
            throw new IllegalStateException("CCH arc endpoints have the same rank: " + from);
    }

    private void unpackForward(int low, int high, IntArrayList edges) {
        int arc = metric.cch.upArc(low, high);
        int witness = metric.witnessForward[arc];
        if (witness != NO_WITNESS) {
            checkWitness(witness, low, high);
            unpackBetween(low, witness, edges);
            unpackBetween(witness, high, edges);
            return;
        }
        edges.add(originalEdge(metric.forwardEdgeKey[arc], low, high));
    }

    private void unpackBackward(int low, int high, IntArrayList edges) {
        int arc = metric.cch.upArcFromDown(high, low);
        int witness = metric.witnessBackward[arc];
        if (witness != NO_WITNESS) {
            checkWitness(witness, low, high);
            unpackBetween(high, witness, edges);
            unpackBetween(witness, low, edges);
            return;
        }
        edges.add(originalEdge(metric.backwardEdgeKey[arc], high, low));
    }

    private void checkWitness(int witness, int low, int high) {
        int[] rank = metric.cch.rank;
        if (rank[witness] >= rank[low] || rank[witness] >= rank[high])
            throw new IllegalStateException("CCH witness " + witness + " is not below " + low + " and " + high);
    }

    private static int originalEdge(int edgeKey, int from, int to) {
        if (edgeKey < 0)
            throw new IllegalStateException("CCH arc " + from + " -> " + to + " has no original edge");
        return GHUtility.getEdgeFromEdgeKey(edgeKey);
    }
}
