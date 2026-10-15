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
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomizableContractionHierarchyTest {
    private static final long INF = Long.MAX_VALUE;

    @Test
    void diamondUsesShortcut() {
        // Contracting the middle nodes inserts the shortcut 0-3. The unique shortest path is 0-1-3.
        GraphFixture fixture = new GraphFixture();
        fixture.edge(0, 1, 1, INF);
        fixture.edge(0, 2, 100, INF);
        fixture.edge(1, 3, 1, INF);
        fixture.edge(2, 3, 1, INF);
        NodeOrderingProvider order = NodeOrderingProvider.fromArray(1, 2, 0, 3);
        assertSameRoute(fixture, order, 0, 3, IntArrayList.from(0, 1, 3), 2);
    }

    @Test
    void nestedShortcutUnpacksToOriginalEdges() {
        GraphFixture fixture = new GraphFixture();
        fixture.edge(0, 1, 4, INF);
        fixture.edge(1, 2, 5, INF);
        fixture.edge(2, 3, 6, INF);
        NodeOrderingProvider order = NodeOrderingProvider.fromArray(1, 2, 0, 3);
        assertSameRoute(fixture, order, 0, 3, IntArrayList.from(0, 1, 2, 3), 15);
        assertSameRoute(fixture, NodeOrderingProvider.identity(4), 0, 3, IntArrayList.from(0, 1, 2, 3), 15);
    }

    @Test
    void upDownPathMeetsAboveBothEndpoints() {
        GraphFixture fixture = new GraphFixture();
        fixture.edge(0, 2, 3, INF);
        fixture.edge(2, 1, 4, INF);
        fixture.edge(0, 1, 100, INF);
        // 0 and 1 are below 2, so the query goes up 0->2 and down 2->1.
        assertSameRoute(fixture, NodeOrderingProvider.fromArray(0, 1, 2), 0, 1, IntArrayList.from(0, 2, 1), 7);
    }

    @Test
    void oneWayEdgeIsNotTraversedBackward() {
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        EncodingManager encodingManager = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(encodingManager).create();
        graph.edge(0, 1).setDistance(100).set(speedEnc, 10, 0).set(accessEnc, true, false);
        graph.edge(1, 2).setDistance(100).set(speedEnc, 10, 0).set(accessEnc, true, false);
        Weighting weighting = new SpeedWeighting(speedEnc);
        NodeOrderingProvider order = NodeOrderingProvider.identity(graph.getNodes());
        EdgeFilter access = AccessFilter.outEdges(accessEnc);

        Path forward = route(graph, weighting, order, access, 0, 2);
        Path dijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(0, 2);
        assertTrue(forward.isFound());
        assertEquals(dijkstra.getWeight(), forward.getWeight(), 0);
        assertEquals(IntArrayList.from(0, 1, 2), forward.calcNodes());

        Path backward = route(graph, weighting, order, access, 2, 0);
        assertFalse(backward.isFound());
        assertFalse(new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(2, 0).isFound());
    }

    @Test
    void accessFilterDropsDirectionEvenIfWeightingAllowsIt() {
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        EncodingManager encodingManager = EncodingManager.start().add(accessEnc).build();
        BaseGraph graph = new BaseGraph.Builder(encodingManager).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1).set(accessEnc, true, false);
        ArrayWeighting weighting = new ArrayWeighting();
        // Both directions are finite for the weighting. The filter only accepts base -> adj.
        weighting.set(edge.getEdge(), 5, 1);
        Path reverse = route(graph, weighting, NodeOrderingProvider.identity(2), AccessFilter.outEdges(accessEnc), 1, 0);
        assertFalse(reverse.isFound());
        Path forward = route(graph, weighting, NodeOrderingProvider.identity(2), AccessFilter.outEdges(accessEnc), 0, 1);
        assertTrue(forward.isFound());
        assertEquals(5, forward.getWeight(), 0);
    }

    @Test
    void parallelEdgesFollowTheMinimumAndCanBeRecustomized() {
        GraphFixture fixture = new GraphFixture();
        int slow = fixture.edge(0, 1, 9, 9);
        int fast = fixture.edge(0, 1, 4, 4);
        CustomizableContractionHierarchy cch = CustomizableContractionHierarchy.build(
                fixture.graph, NodeOrderingProvider.identity(2), EdgeFilter.ALL_EDGES);
        CustomizableContractionHierarchyMetric metric = new CustomizableContractionHierarchyMetric(cch).customize(fixture.weighting);
        Path first = new CustomizableContractionHierarchyQuery(metric).run(0, 1);
        assertEquals(4, first.getWeight(), 0);
        assertEquals(fast, first.getEdges().get(0));

        fixture.weighting.set(slow, 2, 2);
        fixture.weighting.set(fast, 8, 8);
        metric.customize(fixture.weighting);
        Path second = new CustomizableContractionHierarchyQuery(metric).run(0, 1);
        assertEquals(2, second.getWeight(), 0);
        assertEquals(slow, second.getEdges().get(0));
        assertEquals(IntArrayList.from(0, 1), second.calcNodes());
    }

    @Test
    void recustomizeCanPreferDirectEdgeOverPreviousShortcut() {
        GraphFixture fixture = new GraphFixture();
        int viaFirst = fixture.edge(0, 1, 1, INF);
        int viaSecond = fixture.edge(1, 2, 1, INF);
        int direct = fixture.edge(0, 2, 10, INF);
        NodeOrderingProvider order = NodeOrderingProvider.fromArray(1, 0, 2);
        CustomizableContractionHierarchy cch = CustomizableContractionHierarchy.build(fixture.graph, order, EdgeFilter.ALL_EDGES);
        CustomizableContractionHierarchyMetric metric = new CustomizableContractionHierarchyMetric(cch).customize(fixture.weighting);
        Path via = new CustomizableContractionHierarchyQuery(metric).run(0, 2);
        assertEquals(IntArrayList.from(0, 1, 2), via.calcNodes());
        assertEquals(2, via.getWeight(), 0);

        fixture.weighting.set(direct, 1, INF);
        fixture.weighting.set(viaFirst, 10, INF);
        fixture.weighting.set(viaSecond, 10, INF);
        metric.customize(fixture.weighting);
        Path directPath = new CustomizableContractionHierarchyQuery(metric).run(0, 2);
        assertEquals(IntArrayList.from(0, 2), directPath.calcNodes());
        assertEquals(1, directPath.getWeight(), 0);
        assertEquals(direct, directPath.getEdges().get(0));
    }

    @Test
    void unreachableAndTrivialPaths() {
        GraphFixture fixture = new GraphFixture();
        fixture.edge(0, 2, 5, 5);
        NodeOrderingProvider order = NodeOrderingProvider.identity(fixture.graph.getNodes());
        Path unreachable = route(fixture, order, 0, 1);
        assertFalse(unreachable.isFound());
        assertEquals(Double.MAX_VALUE, unreachable.getWeight(), 0);
        assertEquals(0, unreachable.calcNodes().size());

        Path trivial = route(fixture, order, 1, 1);
        assertTrue(trivial.isFound());
        assertEquals(0, trivial.getWeight(), 0);
        assertEquals(IntArrayList.from(1), trivial.calcNodes());
    }

    @Test
    void turnCostsAreRejected() {
        GraphFixture fixture = new GraphFixture();
        fixture.edge(0, 1, 1, 1);
        CustomizableContractionHierarchy cch = CustomizableContractionHierarchy.build(
                fixture.graph, NodeOrderingProvider.identity(2), EdgeFilter.ALL_EDGES);
        Weighting turnWeighting = new ArrayWeighting() {
            @Override
            public boolean hasTurnCosts() {
                return true;
            }
        };
        CustomizableContractionHierarchyMetric metric = new CustomizableContractionHierarchyMetric(cch);
        assertThrows(IllegalArgumentException.class, () -> metric.customize(turnWeighting));
    }

    @Test
    void orderingMustBeAPermutation() {
        GraphFixture fixture = new GraphFixture();
        fixture.edge(0, 1, 1, 1);
        assertThrows(IllegalArgumentException.class,
                () -> CustomizableContractionHierarchy.build(fixture.graph, NodeOrderingProvider.fromArray(0, 0), EdgeFilter.ALL_EDGES));
        assertThrows(IllegalArgumentException.class,
                () -> CustomizableContractionHierarchy.build(fixture.graph, NodeOrderingProvider.identity(1), EdgeFilter.ALL_EDGES));
    }

    @Test
    void randomDigraphsMatchDijkstraForSeveralOrders() {
        Random random = new Random(7);
        for (int trial = 0; trial < 6; trial++) {
            int nodes = 8 + random.nextInt(6);
            GraphFixture fixture = new GraphFixture();
            for (int node = 1; node < nodes; node++) {
                int parent = random.nextInt(node);
                long forward = 1 + random.nextInt(20);
                long backward = random.nextBoolean() ? 1 + random.nextInt(20) : INF;
                if (random.nextBoolean())
                    fixture.edge(parent, node, forward, backward);
                else
                    fixture.edge(node, parent, forward, backward);
            }
            int extra = nodes + random.nextInt(nodes);
            for (int i = 0; i < extra; i++) {
                int from = random.nextInt(nodes);
                int to = random.nextInt(nodes);
                if (from == to)
                    continue;
                long forward = random.nextBoolean() ? 1 + random.nextInt(20) : INF;
                long backward = random.nextBoolean() ? 1 + random.nextInt(20) : INF;
                if (forward == INF && backward == INF)
                    forward = 1 + random.nextInt(20);
                fixture.edge(from, to, forward, backward);
            }
            List<NodeOrderingProvider> orders = Arrays.asList(
                    NodeOrderingProvider.identity(nodes),
                    reversed(nodes),
                    shuffled(nodes, random));
            for (NodeOrderingProvider order : orders) {
                CustomizableContractionHierarchy cch = CustomizableContractionHierarchy.build(
                        fixture.graph, order, EdgeFilter.ALL_EDGES);
                CustomizableContractionHierarchyMetric metric = new CustomizableContractionHierarchyMetric(cch).customize(fixture.weighting);
                CustomizableContractionHierarchyQuery query = new CustomizableContractionHierarchyQuery(metric);
                for (int from = 0; from < nodes; from++) {
                    for (int to = 0; to < nodes; to++) {
                        Path dijkstra = new Dijkstra(fixture.graph, fixture.weighting, TraversalMode.NODE_BASED).calcPath(from, to);
                        Path cchPath = query.run(from, to);
                        assertEquals(dijkstra.isFound(), cchPath.isFound(), "found " + from + " -> " + to);
                        assertEquals(dijkstra.getWeight(), cchPath.getWeight(), 0, "weight " + from + " -> " + to);
                        assertPathWeightMatchesEdges(fixture.weighting, cchPath);
                    }
                }
            }
        }
    }

    private static void assertSameRoute(GraphFixture fixture, NodeOrderingProvider order, int from, int to,
                                        IntArrayList expectedNodes, double expectedWeight) {
        Path cchPath = route(fixture, order, from, to);
        Path dijkstra = new Dijkstra(fixture.graph, fixture.weighting, TraversalMode.NODE_BASED).calcPath(from, to);
        assertEquals(expectedWeight, cchPath.getWeight(), 0);
        assertEquals(expectedWeight, dijkstra.getWeight(), 0);
        assertEquals(expectedNodes, cchPath.calcNodes());
        assertEquals(dijkstra.calcNodes(), cchPath.calcNodes());
        assertPathWeightMatchesEdges(fixture.weighting, cchPath);
    }

    private static Path route(GraphFixture fixture, NodeOrderingProvider order, int from, int to) {
        return route(fixture.graph, fixture.weighting, order, EdgeFilter.ALL_EDGES, from, to);
    }

    private static Path route(BaseGraph graph, Weighting weighting, NodeOrderingProvider order, EdgeFilter access,
                              int from, int to) {
        CustomizableContractionHierarchy cch = CustomizableContractionHierarchy.build(graph, order, access);
        CustomizableContractionHierarchyMetric metric = new CustomizableContractionHierarchyMetric(cch).customize(weighting);
        return new CustomizableContractionHierarchyQuery(metric).run(from, to);
    }

    private static void assertPathWeightMatchesEdges(Weighting weighting, Path path) {
        if (!path.isFound()) {
            assertEquals(Double.MAX_VALUE, path.getWeight(), 0);
            assertEquals(0, path.calcNodes().size());
            return;
        }
        if (path.getEdgeCount() == 0) {
            assertEquals(0, path.getWeight(), 0);
            assertEquals(path.getFromNode(), path.getEndNode());
            return;
        }
        double sum = 0;
        for (EdgeIteratorState edge : path.calcEdges()) {
            double weight = weighting.calcEdgeWeight(edge, false);
            assertTrue(Double.isFinite(weight));
            sum += weight;
        }
        assertEquals(sum, path.getWeight(), 0);
        assertEquals(path.getFromNode(), path.calcNodes().get(0));
        assertEquals(path.getEndNode(), path.calcNodes().get(path.calcNodes().size() - 1));
    }

    private static NodeOrderingProvider reversed(int nodes) {
        int[] order = new int[nodes];
        for (int i = 0; i < nodes; i++)
            order[i] = nodes - 1 - i;
        return NodeOrderingProvider.fromArray(order);
    }

    private static NodeOrderingProvider shuffled(int nodes, Random random) {
        int[] order = new int[nodes];
        for (int i = 0; i < nodes; i++)
            order[i] = i;
        for (int i = nodes - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[swap];
            order[swap] = tmp;
        }
        return NodeOrderingProvider.fromArray(order);
    }

    /**
     * Weights are indexed by edge id. {@code forward} is the storage direction of {@code graph.edge(a, b)},
     * {@code backward} is the opposite direction. {@link #INF} means the direction is not traversable.
     */
    private static class ArrayWeighting implements Weighting {
        private final List<Long> forward = new ArrayList<>();
        private final List<Long> backward = new ArrayList<>();

        void set(int edgeId, long storageDirection, long againstStorage) {
            while (forward.size() <= edgeId) {
                forward.add(INF);
                backward.add(INF);
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
            long weight = edgeId < weights.size() ? weights.get(edgeId) : INF;
            if (weight == INF)
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

    private static final class GraphFixture {
        final EncodingManager encodingManager = EncodingManager.start().build();
        final BaseGraph graph = new BaseGraph.Builder(encodingManager).create();
        final ArrayWeighting weighting = new ArrayWeighting();

        int edge(int from, int to, long forward, long backward) {
            int edgeId = graph.edge(from, to).setDistance(1).getEdge();
            weighting.set(edgeId, forward, backward);
            return edgeId;
        }
    }
}
