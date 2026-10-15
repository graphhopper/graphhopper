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
import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Metric-independent topology of a customizable contraction hierarchy.
 * <p>
 * The construction follows Dibbelt, Strasser and Wagner: contract nodes in the given order and
 * complete the upward neighborhood to a clique. Edge weights are applied later by
 * {@link CustomizableContractionHierarchyMetric}. Any order is correct. The order only changes how
 * many shortcuts are inserted and how fast customization and queries are.
 * {@link NestedDissectionOrder} builds the inertial-flow nested dissection order used by RoutingKit.
 * <p>
 * {@code access} selects directed traversals. It is not a weighting. Every traversal that a later
 * weighting can treat as finite must be accepted here, otherwise customization cannot turn that
 * traversal back on.
 */
public final class CustomizableContractionHierarchy {
    static final long INF = Long.MAX_VALUE;

    final Graph graph;
    final int nodes;
    final int[] rank;
    final int[] order;
    final int[] eliminationTreeParent;
    final int[] upFirstOut;
    final int[] upHead;
    final int[] downFirstOut;
    final int[] downHead;
    final int[] downToUp;
    final IntArrayList[] forwardInputKeys;
    final IntArrayList[] backwardInputKeys;
    private final Map<Long, Integer> upArcByEndpoints;

    private CustomizableContractionHierarchy(Graph graph, int[] rank, int[] order, int[] eliminationTreeParent,
                                              int[] upFirstOut, int[] upHead, int[] downFirstOut, int[] downHead,
                                              int[] downToUp, IntArrayList[] forwardInputKeys,
                                              IntArrayList[] backwardInputKeys, Map<Long, Integer> upArcByEndpoints) {
        this.graph = graph;
        this.nodes = rank.length;
        this.rank = rank;
        this.order = order;
        this.eliminationTreeParent = eliminationTreeParent;
        this.upFirstOut = upFirstOut;
        this.upHead = upHead;
        this.downFirstOut = downFirstOut;
        this.downHead = downHead;
        this.downToUp = downToUp;
        this.forwardInputKeys = forwardInputKeys;
        this.backwardInputKeys = backwardInputKeys;
        this.upArcByEndpoints = upArcByEndpoints;
    }

    public static CustomizableContractionHierarchy build(Graph graph, NodeOrderingProvider order, EdgeFilter access) {
        if (graph == null)
            throw new IllegalArgumentException("graph must not be null");
        if (order == null)
            throw new IllegalArgumentException("order must not be null");
        if (access == null)
            throw new IllegalArgumentException("access filter must not be null");
        int nodes = graph.getNodes();
        if (order.getNumNodes() != nodes)
            throw new IllegalArgumentException("Node ordering has " + order.getNumNodes() + " nodes but the graph has " + nodes);

        int[] rank = new int[nodes];
        int[] orderNodes = new int[nodes];
        boolean[] seen = new boolean[nodes];
        for (int level = 0; level < nodes; level++) {
            int node = order.getNodeIdForLevel(level);
            if (node < 0 || node >= nodes || seen[node])
                throw new IllegalArgumentException("Node ordering must be a permutation of 0.." + (nodes - 1) + ", got node " + node + " at level " + level);
            seen[node] = true;
            rank[node] = level;
            orderNodes[level] = node;
        }

        IntArrayList inputFrom = new IntArrayList();
        IntArrayList inputTo = new IntArrayList();
        IntArrayList inputKey = new IntArrayList();
        EdgeExplorer explorer = graph.createEdgeExplorer(access);
        for (int node = 0; node < nodes; node++) {
            EdgeIterator iter = explorer.setBaseNode(node);
            while (iter.next()) {
                int adj = iter.getAdjNode();
                if (adj == node)
                    continue;
                inputFrom.add(node);
                inputTo.add(adj);
                inputKey.add(iter.getEdgeKey());
            }
        }

        IntHashSet[] neighbors = new IntHashSet[nodes];
        for (int node = 0; node < nodes; node++)
            neighbors[node] = new IntHashSet();
        for (int i = 0; i < inputFrom.size(); i++) {
            int from = inputFrom.get(i);
            int to = inputTo.get(i);
            neighbors[from].add(to);
            neighbors[to].add(from);
        }

        int[] parent = new int[nodes];
        Arrays.fill(parent, -1);
        int[][] upAdj = new int[nodes][];
        for (int level = 0; level < nodes; level++) {
            int node = orderNodes[level];
            IntHashSet adjacent = neighbors[node];
            int[] upward = new int[adjacent.size()];
            int upwardCount = 0;
            for (IntCursor cursor : adjacent)
                upward[upwardCount++] = cursor.value;
            sortByRank(upward, upwardCount, rank);
            if (upwardCount > 0)
                parent[node] = upward[0];
            for (int i = 0; i < upwardCount; i++) {
                int a = upward[i];
                for (int j = i + 1; j < upwardCount; j++) {
                    int b = upward[j];
                    neighbors[a].add(b);
                    neighbors[b].add(a);
                }
            }
            for (int i = 0; i < upwardCount; i++)
                neighbors[upward[i]].remove(node);
            neighbors[node].clear();
            upAdj[node] = upwardCount == upward.length ? upward : Arrays.copyOf(upward, upwardCount);
        }

        int[] upFirstOut = new int[nodes + 1];
        for (int node = 0; node < nodes; node++)
            upFirstOut[node + 1] = upFirstOut[node] + upAdj[node].length;
        int[] upHead = new int[upFirstOut[nodes]];
        Map<Long, Integer> upArcByEndpoints = new HashMap<>();
        int arc = 0;
        for (int node = 0; node < nodes; node++) {
            for (int head : upAdj[node]) {
                upHead[arc] = head;
                upArcByEndpoints.put(pairKey(node, head), arc);
                arc++;
            }
        }

        int[] downCount = new int[nodes];
        for (int head : upHead)
            downCount[head]++;
        int[] downFirstOut = new int[nodes + 1];
        for (int node = 0; node < nodes; node++)
            downFirstOut[node + 1] = downFirstOut[node] + downCount[node];
        int[] downHead = new int[upHead.length];
        int[] downToUp = new int[upHead.length];
        int[] downCursor = Arrays.copyOf(downFirstOut, nodes);
        for (int tail = 0; tail < nodes; tail++) {
            for (int upArc = upFirstOut[tail]; upArc < upFirstOut[tail + 1]; upArc++) {
                int head = upHead[upArc];
                int downArc = downCursor[head]++;
                downHead[downArc] = tail;
                downToUp[downArc] = upArc;
            }
        }

        IntArrayList[] forwardInputKeys = new IntArrayList[upHead.length];
        IntArrayList[] backwardInputKeys = new IntArrayList[upHead.length];
        for (int i = 0; i < inputFrom.size(); i++) {
            int from = inputFrom.get(i);
            int to = inputTo.get(i);
            boolean upward = rank[from] < rank[to];
            int low = upward ? from : to;
            int high = upward ? to : from;
            Integer upArc = upArcByEndpoints.get(pairKey(low, high));
            if (upArc == null)
                throw new IllegalStateException("Missing CCH arc for input traversal " + from + " -> " + to);
            addKey(upward ? forwardInputKeys : backwardInputKeys, upArc, inputKey.get(i));
        }

        return new CustomizableContractionHierarchy(graph, rank, orderNodes, parent, upFirstOut, upHead,
                downFirstOut, downHead, downToUp, forwardInputKeys, backwardInputKeys, upArcByEndpoints);
    }

    int upArc(int low, int high) {
        Integer arc = upArcByEndpoints.get(pairKey(low, high));
        if (arc == null)
            throw new IllegalStateException("Missing upward CCH arc " + low + " -> " + high);
        return arc;
    }

    /**
     * Upward arc id of the CCH arc that leaves {@code low} toward {@code high}, found via the downward
     * adjacency stored at {@code high}.
     */
    int upArcFromDown(int high, int low) {
        for (int downArc = downFirstOut[high]; downArc < downFirstOut[high + 1]; downArc++) {
            if (downHead[downArc] == low)
                return downToUp[downArc];
        }
        throw new IllegalStateException("Missing downward CCH arc " + high + " -> " + low);
    }

    static long satAdd(long a, long b) {
        if (a == INF || b == INF)
            return INF;
        long sum = a + b;
        if (sum < 0)
            return INF;
        return sum;
    }

    private static long pairKey(int tail, int head) {
        return ((long) tail << 32) | (head & 0xFFFFFFFFL);
    }

    private static void addKey(IntArrayList[] keys, int arc, int edgeKey) {
        IntArrayList list = keys[arc];
        if (list == null) {
            list = new IntArrayList(1);
            keys[arc] = list;
        }
        list.add(edgeKey);
    }

    private static void sortByRank(int[] nodes, int len, int[] rank) {
        for (int i = 1; i < len; i++) {
            int node = nodes[i];
            int nodeRank = rank[node];
            int j = i - 1;
            while (j >= 0 && rank[nodes[j]] > nodeRank) {
                nodes[j + 1] = nodes[j];
                j--;
            }
            nodes[j + 1] = node;
        }
    }
}
