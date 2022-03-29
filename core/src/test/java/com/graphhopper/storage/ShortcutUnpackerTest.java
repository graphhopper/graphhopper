package com.graphhopper.storage;

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.ch.ShortcutUnpacker;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.stream.Stream;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ShortcutUnpackerTest {
    private final static int PREV_EDGE = 12;
    private final static int NEXT_EDGE = 13;

    private static final class Fixture {
        private final boolean edgeBased;
        private final EncodingManager encodingManager;
        private final CarFlagEncoder encoder;
        private final BaseGraph graph;
        private CHStorageBuilder chBuilder;
        private RoutingCHGraph routingCHGraph;

        Fixture(boolean edgeBased) {
            this.edgeBased = edgeBased;
            encoder = new CarFlagEncoder(5, 5, 10, true);
            encodingManager = EncodingManager.create(encoder);
            graph = new BaseGraph.Builder(encodingManager).create();
        }

        @Override
        public String toString() {
            return "edge_based=" + edgeBased;
        }

        private void freeze() {
            graph.freeze();
            TurnCostProvider turnCostProvider = edgeBased ? new DefaultTurnCostProvider(encoder, graph.getTurnCostStorage()) : NO_TURN_COST_PROVIDER;
            CHConfig chConfig = new CHConfig("profile", new FastestWeighting(encoder, turnCostProvider), edgeBased);
            CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
            chBuilder = new CHStorageBuilder(chStore);
            routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);
        }

        private void setCHLevels(int... order) {
            for (int i = 0; i < order.length; i++) {
                chBuilder.setLevel(order[i], i);
            }
        }

        private void visitFwd(int edge, int adj, boolean reverseOrder, ShortcutUnpacker.Visitor visitor) {
            createShortcutUnpacker(visitor).visitOriginalEdgesFwd(edge, adj, reverseOrder, PREV_EDGE);
        }

        private void visitBwd(int edge, int adjNode, boolean reverseOrder, ShortcutUnpacker.Visitor visitor) {
            createShortcutUnpacker(visitor).visitOriginalEdgesBwd(edge, adjNode, reverseOrder, NEXT_EDGE);
        }

        private ShortcutUnpacker createShortcutUnpacker(ShortcutUnpacker.Visitor visitor) {
            return new ShortcutUnpacker(routingCHGraph, visitor, edgeBased);
        }

        private void setTurnCost(int fromEdge, int viaNode, int toEdge, double cost) {
            graph.getTurnCostStorage().set(((EncodedValueLookup) encodingManager).getDecimalEncodedValue(TurnCost.key(encoder.toString())), fromEdge, viaNode, toEdge, cost);
        }

        private void shortcut(int baseNode, int adjNode, int skip1, int skip2, int origFirst, int origLast, boolean reverse) {
            // shortcut weight/distance is not important for us here
            double weight = 1;
            int flags = reverse ? PrepareEncoder.getScFwdDir() : PrepareEncoder.getScBwdDir();
            if (edgeBased) {
                chBuilder.addShortcutEdgeBased(baseNode, adjNode, flags, weight, skip1, skip2, origFirst, origLast);
            } else {
                chBuilder.addShortcutNodeBased(baseNode, adjNode, flags, weight, skip1, skip2);
            }
        }
    }

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    new Fixture(false),
                    new Fixture(true)
            ).map(Arguments::of);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testUnpacking(Fixture f) {
        // 0-1-2-3-4-5-6
        GHUtility.setSpeed(60, 30, f.encoder,
                f.graph.edge(0, 1).setDistance(1),
                f.graph.edge(1, 2).setDistance(1),
                f.graph.edge(2, 3).setDistance(1),
                f.graph.edge(3, 4).setDistance(1),
                f.graph.edge(4, 5).setDistance(1),
                f.graph.edge(5, 6).setDistance(1) // edge 5
        );
        f.freeze();

        f.setCHLevels(1, 3, 5, 4, 2, 0, 6);
        f.shortcut(4, 2, 2, 3, 2, 3, true);
        f.shortcut(4, 6, 4, 5, 4, 5, false);
        f.shortcut(2, 0, 0, 1, 0, 1, true);
        f.shortcut(2, 6, 6, 7, 2, 5, false);
        f.shortcut(0, 6, 8, 9, 0, 5, false);

        {
            // unpack the shortcut 0->6, traverse original edges in 'forward' order (from node 0 to 6)
            TestVisitor visitor = new TestVisitor(f.routingCHGraph);
            f.visitFwd(10, 6, false, visitor);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.edgeIds);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.baseNodes);
            assertEquals(IntArrayList.from(1, 2, 3, 4, 5, 6), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            if (f.edgeBased) {
                assertEquals(IntArrayList.from(PREV_EDGE, 0, 1, 2, 3, 4), visitor.prevOrNextEdgeIds);
            }
        }

        {
            // unpack the shortcut 0->6, traverse original edges in 'backward' order (from node 6 to 0)
            // note that traversing in backward order does not mean the original edges are read in reverse (e.g. fwd speed still applies)
            // -> only the order of the original edges is reversed
            TestVisitor visitor = new TestVisitor(f.routingCHGraph);
            f.visitFwd(10, 6, true, visitor);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.edgeIds);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.baseNodes);
            assertEquals(IntArrayList.from(6, 5, 4, 3, 2, 1), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            if (f.edgeBased) {
                assertEquals(IntArrayList.from(4, 3, 2, 1, 0, PREV_EDGE), visitor.prevOrNextEdgeIds);
            }
        }

        {
            // unpack the shortcut 6<-0 in reverse, i.e. with 6 as base node. traverse original edges in 'forward' order (from node 6 to 0)
            TestVisitor visitor = new TestVisitor(f.routingCHGraph);
            f.visitBwd(10, 0, false, visitor);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.edgeIds);
            assertEquals(IntArrayList.from(6, 5, 4, 3, 2, 1), visitor.baseNodes);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            if (f.edgeBased) {
                assertEquals(IntArrayList.from(NEXT_EDGE, 5, 4, 3, 2, 1), visitor.prevOrNextEdgeIds);
            }
        }

        {
            // unpack the shortcut 6<-0 in reverse, i.e. with 60as base node. traverse original edges in 'backward' order (from node 0 to 6)
            TestVisitor visitor = new TestVisitor(f.routingCHGraph);
            f.visitBwd(10, 0, true, visitor);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.edgeIds);
            assertEquals(IntArrayList.from(1, 2, 3, 4, 5, 6), visitor.baseNodes);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            if (f.edgeBased) {
                assertEquals(IntArrayList.from(1, 2, 3, 4, 5, NEXT_EDGE), visitor.prevOrNextEdgeIds);
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void loopShortcut(Fixture f) {
        assumeTrue(f.edgeBased, "loop shortcuts only exist for edge-based CH");
        //     3
        //    / \
        //   2   4
        //    \ /
        // 0 - 1 - 5
        GHUtility.setSpeed(60, 30, f.encoder,
                f.graph.edge(0, 1).setDistance(1),
                f.graph.edge(1, 2).setDistance(1),
                f.graph.edge(2, 3).setDistance(1),
                f.graph.edge(3, 4).setDistance(1),
                f.graph.edge(4, 1).setDistance(1),
                f.graph.edge(1, 5).setDistance(1));
        f.freeze();

        f.setCHLevels(2, 4, 3, 1, 5, 0);
        f.shortcut(3, 1, 1, 2, 1, 2, true);
        f.shortcut(3, 1, 3, 4, 3, 4, false);
        f.shortcut(1, 1, 6, 7, 1, 4, false);
        f.shortcut(1, 0, 0, 8, 0, 4, true);
        f.shortcut(5, 0, 9, 5, 0, 5, true);

        {
            // unpack the shortcut 0->5, traverse original edges in 'forward' order (from node 0 to 5)
            TestVisitor visitor = new TestVisitor(f.routingCHGraph);
            f.visitFwd(10, 5, false, visitor);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.edgeIds);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 1), visitor.baseNodes);
            assertEquals(IntArrayList.from(1, 2, 3, 4, 1, 5), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            assertEquals(IntArrayList.from(PREV_EDGE, 0, 1, 2, 3, 4), visitor.prevOrNextEdgeIds);
        }

        {
            // unpack the shortcut 0->5, traverse original edges in 'backward' order (from node 5 to 0)
            TestVisitor visitor = new TestVisitor(f.routingCHGraph);
            f.visitFwd(10, 5, true, visitor);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.edgeIds);
            assertEquals(IntArrayList.from(1, 4, 3, 2, 1, 0), visitor.baseNodes);
            assertEquals(IntArrayList.from(5, 1, 4, 3, 2, 1), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            assertEquals(IntArrayList.from(4, 3, 2, 1, 0, PREV_EDGE), visitor.prevOrNextEdgeIds);
        }

        {
            // unpack the shortcut 5<-0, traverse original edges in 'forward' order (from node 5 to 0)
            TestVisitor visitor = new TestVisitor(f.routingCHGraph);
            f.visitBwd(10, 0, false, visitor);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.edgeIds);
            assertEquals(IntArrayList.from(5, 1, 4, 3, 2, 1), visitor.baseNodes);
            assertEquals(IntArrayList.from(1, 4, 3, 2, 1, 0), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            assertEquals(IntArrayList.from(NEXT_EDGE, 5, 4, 3, 2, 1), visitor.prevOrNextEdgeIds);
        }

        {
            // unpack the shortcut 5<-0, traverse original edges in 'backward' order (from node 0 to 5)
            TestVisitor visitor = new TestVisitor(f.routingCHGraph);
            f.visitBwd(10, 0, true, visitor);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.edgeIds);
            assertEquals(IntArrayList.from(1, 2, 3, 4, 1, 5), visitor.baseNodes);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 1), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            assertEquals(IntArrayList.from(1, 2, 3, 4, 5, NEXT_EDGE), visitor.prevOrNextEdgeIds);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void withTurnWeighting(Fixture f) {
        assumeTrue(f.edgeBased);
        //      2 5 3 2 1 4 6      turn costs ->
        // prev 0-1-2-3-4-5-6 next
        //      1 0 1 4 2 3 2      turn costs <-
        EdgeIteratorState edge0, edge1, edge2, edge3, edge4, edge5;
        GHUtility.setSpeed(60, 30, f.encoder,
                edge0 = f.graph.edge(0, 1).setDistance(1),
                edge1 = f.graph.edge(1, 2).setDistance(1),
                edge2 = f.graph.edge(2, 3).setDistance(1),
                edge3 = f.graph.edge(3, 4).setDistance(1),
                edge4 = f.graph.edge(4, 5).setDistance(1),
                edge5 = f.graph.edge(5, 6).setDistance(1));
        f.freeze();

        // turn costs ->
        f.setTurnCost(PREV_EDGE, 0, edge0.getEdge(), 2.0);
        f.setTurnCost(edge0.getEdge(), 1, edge1.getEdge(), 5.0);
        f.setTurnCost(edge1.getEdge(), 2, edge2.getEdge(), 3);
        f.setTurnCost(edge2.getEdge(), 3, edge3.getEdge(), 2.0);
        f.setTurnCost(edge3.getEdge(), 4, edge4.getEdge(), 1.0);
        f.setTurnCost(edge4.getEdge(), 5, edge5.getEdge(), 4.0);
        f.setTurnCost(edge5.getEdge(), 6, NEXT_EDGE, 6.0);
        // turn costs <-
        f.setTurnCost(NEXT_EDGE, 6, edge5.getEdge(), 2.0);
        f.setTurnCost(edge5.getEdge(), 5, edge4.getEdge(), 3.0);
        f.setTurnCost(edge4.getEdge(), 4, edge3.getEdge(), 2.0);
        f.setTurnCost(edge3.getEdge(), 3, edge2.getEdge(), 4.0);
        f.setTurnCost(edge2.getEdge(), 2, edge1.getEdge(), 1.0);
        f.setTurnCost(edge1.getEdge(), 1, edge0.getEdge(), 0.0);
        f.setTurnCost(edge0.getEdge(), 0, PREV_EDGE, 1.0);

        f.setCHLevels(1, 3, 5, 4, 2, 0, 6);
        f.shortcut(4, 2, 2, 3, 2, 3, true);
        f.shortcut(4, 6, 4, 5, 4, 5, false);
        f.shortcut(2, 0, 0, 1, 0, 1, true);
        f.shortcut(2, 6, 6, 7, 2, 5, false);
        f.shortcut(0, 6, 8, 9, 0, 5, false);

        {
            // unpack the shortcut 0->6, traverse original edges in 'forward' order (from node 0 to 6)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor(f.routingCHGraph);
            f.visitFwd(10, 6, false, visitor);
            assertEquals(6 * 0.06 + 17, visitor.weight, 1.e-3, "wrong weight");
            assertEquals((6 * 60 + 17000), visitor.time, "wrong time");
        }

        {
            // unpack the shortcut 0->6, traverse original edges in 'backward' order (from node 6 to 0)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor(f.routingCHGraph);
            f.visitFwd(10, 6, true, visitor);
            assertEquals(6 * 0.06 + 17, visitor.weight, 1.e-3, "wrong weight");
            assertEquals((6 * 60 + 17000), visitor.time, "wrong time");
        }

        {
            // unpack the shortcut 6<-0, traverse original edges in 'forward' order (from node 6 to 0)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor(f.routingCHGraph);
            f.visitBwd(10, 0, false, visitor);
            assertEquals(6 * 0.06 + 21, visitor.weight, 1.e-3, "wrong weight");
            assertEquals((6 * 60 + 21000), visitor.time, "wrong time");
        }

        {
            // unpack the shortcut 6<-0, traverse original edges in 'backward' order (from node 0 to 6)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor(f.routingCHGraph);
            f.visitBwd(10, 0, true, visitor);
            assertEquals(6 * 0.06 + 21, visitor.weight, 1.e-3, "wrong weight");
            assertEquals((6 * 60 + 21000), visitor.time, "wrong time");
        }
    }

    private static class TestVisitor implements ShortcutUnpacker.Visitor {
        private final RoutingCHGraph routingCHGraph;
        private final IntArrayList edgeIds = new IntArrayList();
        private final IntArrayList adjNodes = new IntArrayList();
        private final IntArrayList baseNodes = new IntArrayList();
        private final IntArrayList prevOrNextEdgeIds = new IntArrayList();
        private final DoubleArrayList weights = new DoubleArrayList();
        private final DoubleArrayList distances = new DoubleArrayList();
        private final DoubleArrayList times = new DoubleArrayList();

        TestVisitor(RoutingCHGraph routingCHGraph) {
            this.routingCHGraph = routingCHGraph;
        }

        @Override
        public void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
            edgeIds.add(edge.getEdge());
            baseNodes.add(edge.getBaseNode());
            adjNodes.add(edge.getAdjNode());
            weights.add(GHUtility.calcWeightWithTurnWeight(routingCHGraph.getWeighting(), edge, reverse, prevOrNextEdgeId));
            distances.add(edge.getDistance());
            times.add(GHUtility.calcMillisWithTurnMillis(routingCHGraph.getWeighting(), edge, reverse, prevOrNextEdgeId));
            prevOrNextEdgeIds.add(prevOrNextEdgeId);
        }
    }

    private static class TurnWeightingVisitor implements ShortcutUnpacker.Visitor {
        private final RoutingCHGraph routingCHGraph;
        private long time = 0;
        private double weight = 0;

        TurnWeightingVisitor(RoutingCHGraph routingCHGraph) {
            this.routingCHGraph = routingCHGraph;
        }

        @Override
        public void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
            time += GHUtility.calcMillisWithTurnMillis(routingCHGraph.getWeighting(), edge, reverse, prevOrNextEdgeId);
            weight += GHUtility.calcWeightWithTurnWeight(routingCHGraph.getWeighting(), edge, reverse, prevOrNextEdgeId);
        }
    }
}