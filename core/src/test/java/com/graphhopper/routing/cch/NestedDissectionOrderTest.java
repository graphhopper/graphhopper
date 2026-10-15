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

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedDissectionOrderTest {
    @Test
    void orderIsADeterministicPermutation() {
        BaseGraph graph = path(9);
        NodeOrderingProvider first = NestedDissectionOrder.compute(graph, EdgeFilter.ALL_EDGES);
        NodeOrderingProvider second = NestedDissectionOrder.compute(graph, EdgeFilter.ALL_EDGES);
        assertPermutation(graph.getNodes(), first);
        int[] order = new int[graph.getNodes()];
        for (int level = 0; level < order.length; level++) {
            assertEquals(first.getNodeIdForLevel(level), second.getNodeIdForLevel(level));
            order[level] = first.getNodeIdForLevel(level);
        }
        int top = order[order.length - 1];
        assertTrue(top > 0 && top < graph.getNodes() - 1, "top separator should be an interior node, order=" + Arrays.toString(order));
    }

    @Test
    void bridgeSeparatorIsContractedLate() {
        BaseGraph graph = new BaseGraph.Builder(EncodingManager.start().build()).create();
        NodeAccess nodes = graph.getNodeAccess();
        // Two cliques far apart, joined by 3-4-5. The geographic min cut crosses that bridge,
        // so a bridge node is placed after every node that only touches its own clique.
        for (int node = 0; node < 4; node++)
            nodes.setNode(node, 0, node * 0.01);
        nodes.setNode(4, 0, 0.5);
        for (int node = 5; node < 9; node++)
            nodes.setNode(node, 0, 1 + (node - 5) * 0.01);
        for (int left = 0; left < 4; left++)
            for (int right = left + 1; right < 4; right++)
                graph.edge(left, right).setDistance(1);
        for (int left = 5; left < 9; left++)
            for (int right = left + 1; right < 9; right++)
                graph.edge(left, right).setDistance(1);
        graph.edge(3, 4).setDistance(1);
        graph.edge(4, 5).setDistance(1);

        NodeOrderingProvider order = NestedDissectionOrder.compute(graph, EdgeFilter.ALL_EDGES);
        assertPermutation(9, order);
        assertEquals(4, order.getNodeIdForLevel(8), "the bridge node is the top separator");
    }

    @Test
    void disconnectedGraphStillOrdersEveryNode() {
        BaseGraph graph = new BaseGraph.Builder(EncodingManager.start().build()).create();
        NodeAccess nodes = graph.getNodeAccess();
        nodes.setNode(0, 0, 0);
        nodes.setNode(1, 0, 0.01);
        nodes.setNode(2, 1, 0);
        nodes.setNode(3, 1, 0.01);
        graph.edge(0, 1).setDistance(1);
        graph.edge(2, 3).setDistance(1);
        assertPermutation(4, NestedDissectionOrder.compute(graph, EdgeFilter.ALL_EDGES));
    }

    @Test
    void cchWithThisOrderMatchesDijkstra() {
        BaseGraph graph = path(6);
        ArrayWeighting weighting = new ArrayWeighting();
        for (int edge = 0; edge < graph.getEdges(); edge++)
            weighting.set(edge, 1 + edge, 1 + edge);
        NodeOrderingProvider order = NestedDissectionOrder.compute(graph, EdgeFilter.ALL_EDGES);
        CustomizableContractionHierarchy cch = CustomizableContractionHierarchy.build(graph, order, EdgeFilter.ALL_EDGES);
        CustomizableContractionHierarchyMetric metric = new CustomizableContractionHierarchyMetric(cch).customize(weighting);
        CustomizableContractionHierarchyQuery query = new CustomizableContractionHierarchyQuery(metric);
        for (int from = 0; from < graph.getNodes(); from++) {
            for (int to = 0; to < graph.getNodes(); to++) {
                Path dijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(from, to);
                Path cchPath = query.run(from, to);
                assertEquals(dijkstra.isFound(), cchPath.isFound());
                assertEquals(dijkstra.getWeight(), cchPath.getWeight(), 0);
                if (dijkstra.isFound())
                    assertEquals(dijkstra.calcNodes(), cchPath.calcNodes());
            }
        }
    }

    private static BaseGraph path(int nodes) {
        BaseGraph graph = new BaseGraph.Builder(EncodingManager.start().build()).create();
        NodeAccess access = graph.getNodeAccess();
        for (int node = 0; node < nodes; node++)
            access.setNode(node, 48, 11 + node * 0.05);
        for (int node = 0; node < nodes - 1; node++)
            graph.edge(node, node + 1).setDistance(1);
        return graph;
    }

    private static void assertPermutation(int nodes, NodeOrderingProvider order) {
        assertEquals(nodes, order.getNumNodes());
        boolean[] seen = new boolean[nodes];
        for (int level = 0; level < nodes; level++) {
            int node = order.getNodeIdForLevel(level);
            assertTrue(node >= 0 && node < nodes && !seen[node], "duplicate or invalid node " + node);
            seen[node] = true;
        }
    }

    private static final class ArrayWeighting implements Weighting {
        private final List<Long> forward = new ArrayList<>();
        private final List<Long> backward = new ArrayList<>();

        void set(int edgeId, long storageDirection, long againstStorage) {
            while (forward.size() <= edgeId) {
                forward.add(Long.MAX_VALUE);
                backward.add(Long.MAX_VALUE);
            }
            forward.set(edgeId, storageDirection);
            backward.set(edgeId, againstStorage);
        }

        @Override
        public double calcMinWeightPerDistance() {
            return 0;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            boolean againstStorage = ((edgeState.getEdgeKey() & 1) == 1) ^ reverse;
            List<Long> weights = againstStorage ? backward : forward;
            int edgeId = edgeState.getEdge();
            long weight = edgeId < weights.size() ? weights.get(edgeId) : Long.MAX_VALUE;
            if (weight == Long.MAX_VALUE)
                return Double.POSITIVE_INFINITY;
            return weight;
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            return 0;
        }

        @Override
        public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
            return 0;
        }

        @Override
        public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
            return 0;
        }

        @Override
        public boolean hasTurnCosts() {
            return false;
        }

        @Override
        public String getName() {
            return "array";
        }
    }
}
