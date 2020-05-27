package com.graphhopper.storage;

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.ch.ShortcutUnpacker;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ShortcutUnpackerTest {
    private final static int PREV_EDGE = 12;
    private final static int NEXT_EDGE = 13;
    private final boolean edgeBased;
    private EncodingManager encodingManager;
    private FlagEncoder encoder;
    private GraphHopperStorage graph;
    private CHGraph chGraph;
    private RoutingCHGraph routingCHGraph;

    @Parameterized.Parameters(name = "{0}")
    public static Object[] params() {
        return new Object[]{
                TraversalMode.NODE_BASED,
                TraversalMode.EDGE_BASED
        };
    }

    public ShortcutUnpackerTest(TraversalMode traversalMode) {
        this.edgeBased = traversalMode.isEdgeBased();
    }

    @Before
    public void init() {
        encoder = new CarFlagEncoder(5, 5, 10).setSpeedTwoDirections(true);
        encodingManager = EncodingManager.create(encoder);
        graph = new GraphBuilder(encodingManager)
                .setCHConfigStrings("profile|car|fastest|" + (edgeBased ? "edge" : "node"))
                .create();
        chGraph = graph.getCHGraph();
        routingCHGraph = new RoutingCHGraphImpl(chGraph, chGraph.getCHConfig().getWeighting());
    }

    @Test
    public void testUnpacking() {
        // 0-1-2-3-4-5-6
        DecimalEncodedValue speedEnc = encoder.getAverageSpeedEnc();
        double fwdSpeed = 60;
        double bwdSpeed = 30;
        graph.edge(0, 1, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.edge(1, 2, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.edge(2, 3, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.edge(3, 4, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.edge(4, 5, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.edge(5, 6, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.freeze();
        shortcut(0, 2, 0, 1, 0, 1);
        shortcut(2, 4, 2, 3, 2, 3);
        shortcut(4, 6, 4, 5, 4, 5);
        shortcut(2, 6, 7, 8, 2, 5);
        shortcut(0, 6, 6, 9, 0, 5);

        {
            // unpack the shortcut 0->6, traverse original edges in 'forward' order (from node 0 to 6)
            TestVisitor visitor = new TestVisitor();
            visitFwd(10, 6, false, visitor);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.edgeIds);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.baseNodes);
            assertEquals(IntArrayList.from(1, 2, 3, 4, 5, 6), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            if (edgeBased) {
                assertEquals(IntArrayList.from(PREV_EDGE, 0, 1, 2, 3, 4), visitor.prevOrNextEdgeIds);
            }
        }

        {
            // unpack the shortcut 0->6, traverse original edges in 'backward' order (from node 6 to 0)
            // note that traversing in backward order does not mean the original edges are read in reverse (e.g. fwd speed still applies)
            // -> only the order of the original edges is reversed
            TestVisitor visitor = new TestVisitor();
            visitFwd(10, 6, true, visitor);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.edgeIds);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.baseNodes);
            assertEquals(IntArrayList.from(6, 5, 4, 3, 2, 1), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            if (edgeBased) {
                assertEquals(IntArrayList.from(4, 3, 2, 1, 0, PREV_EDGE), visitor.prevOrNextEdgeIds);
            }
        }

        {
            // unpack the shortcut 6<-0 in reverse, i.e. with 6 as base node. traverse original edges in 'forward' order (from node 6 to 0)
            TestVisitor visitor = new TestVisitor();
            visitBwd(10, 0, false, visitor);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.edgeIds);
            assertEquals(IntArrayList.from(6, 5, 4, 3, 2, 1), visitor.baseNodes);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            if (edgeBased) {
                assertEquals(IntArrayList.from(NEXT_EDGE, 5, 4, 3, 2, 1), visitor.prevOrNextEdgeIds);
            }
        }

        {
            // unpack the shortcut 6<-0 in reverse, i.e. with 60as base node. traverse original edges in 'backward' order (from node 0 to 6)
            TestVisitor visitor = new TestVisitor();
            visitBwd(10, 0, true, visitor);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.edgeIds);
            assertEquals(IntArrayList.from(1, 2, 3, 4, 5, 6), visitor.baseNodes);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            if (edgeBased) {
                assertEquals(IntArrayList.from(1, 2, 3, 4, 5, NEXT_EDGE), visitor.prevOrNextEdgeIds);
            }
        }
    }

    @Test
    public void loopShortcut() {
        Assume.assumeTrue("loop shortcuts only exist for edge-based CH", edgeBased);
        //     3
        //    / \
        //   2   4
        //    \ /
        // 0 - 1 - 5
        DecimalEncodedValue speedEnc = encoder.getAverageSpeedEnc();
        double fwdSpeed = 60;
        double bwdSpeed = 30;
        graph.edge(0, 1, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.edge(1, 2, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.edge(2, 3, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.edge(3, 4, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.edge(4, 1, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.edge(1, 5, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.freeze();
        shortcut(1, 3, 1, 2, 1, 2);
        shortcut(3, 1, 3, 4, 3, 4);
        shortcut(1, 1, 6, 7, 1, 4);
        shortcut(0, 1, 0, 8, 0, 4);
        shortcut(0, 5, 9, 5, 0, 5);

        {
            // unpack the shortcut 0->5, traverse original edges in 'forward' order (from node 0 to 5)
            TestVisitor visitor = new TestVisitor();
            visitFwd(10, 5, false, visitor);
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
            TestVisitor visitor = new TestVisitor();
            visitFwd(10, 5, true, visitor);
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
            TestVisitor visitor = new TestVisitor();
            visitBwd(10, 0, false, visitor);
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
            TestVisitor visitor = new TestVisitor();
            visitBwd(10, 0, true, visitor);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.edgeIds);
            assertEquals(IntArrayList.from(1, 2, 3, 4, 1, 5), visitor.baseNodes);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 1), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
            assertEquals(IntArrayList.from(1, 2, 3, 4, 5, NEXT_EDGE), visitor.prevOrNextEdgeIds);
        }
    }

    @Test
    public void withTurnWeighting() {
        Assume.assumeTrue(edgeBased);
        //      2 5 3 2 1 4 6      turn costs ->
        // prev 0-1-2-3-4-5-6 next
        //      1 0 1 4 2 3 2      turn costs <-
        DecimalEncodedValue speedEnc = encoder.getAverageSpeedEnc();
        double fwdSpeed = 60;
        double bwdSpeed = 30;
        EdgeIteratorState edge0 = graph.edge(0, 1, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        EdgeIteratorState edge1 = graph.edge(1, 2, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        EdgeIteratorState edge2 = graph.edge(2, 3, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        EdgeIteratorState edge3 = graph.edge(3, 4, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        EdgeIteratorState edge4 = graph.edge(4, 5, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        EdgeIteratorState edge5 = graph.edge(5, 6, 1, true).set(speedEnc, fwdSpeed).setReverse(speedEnc, bwdSpeed);
        graph.freeze();

        // turn costs ->
        setTurnCost(PREV_EDGE, 0, edge0.getEdge(), 2.0);
        setTurnCost(edge0.getEdge(), 1, edge1.getEdge(), 5.0);
        setTurnCost(edge1.getEdge(), 2, edge2.getEdge(), 3);
        setTurnCost(edge2.getEdge(), 3, edge3.getEdge(), 2.0);
        setTurnCost(edge3.getEdge(), 4, edge4.getEdge(), 1.0);
        setTurnCost(edge4.getEdge(), 5, edge5.getEdge(), 4.0);
        setTurnCost(edge5.getEdge(), 6, NEXT_EDGE, 6.0);
        // turn costs <-
        setTurnCost(NEXT_EDGE, 6, edge5.getEdge(), 2.0);
        setTurnCost(edge5.getEdge(), 5, edge4.getEdge(), 3.0);
        setTurnCost(edge4.getEdge(), 4, edge3.getEdge(), 2.0);
        setTurnCost(edge3.getEdge(), 3, edge2.getEdge(), 4.0);
        setTurnCost(edge2.getEdge(), 2, edge1.getEdge(), 1.0);
        setTurnCost(edge1.getEdge(), 1, edge0.getEdge(), 0.0);
        setTurnCost(edge0.getEdge(), 0, PREV_EDGE, 1.0);

        shortcut(0, 2, 0, 1, 0, 1);
        shortcut(2, 4, 2, 3, 2, 3);
        shortcut(4, 6, 4, 5, 4, 5);
        shortcut(2, 6, 7, 8, 2, 5);
        shortcut(0, 6, 6, 9, 0, 5);

        {
            // unpack the shortcut 0->6, traverse original edges in 'forward' order (from node 0 to 6)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor();
            visitFwd(10, 6, false, visitor);
            assertEquals("wrong weight", 6 * 0.06 + 17, visitor.weight, 1.e-3);
            assertEquals("wrong time", (6 * 60 + 17000), visitor.time);
        }

        {
            // unpack the shortcut 0->6, traverse original edges in 'backward' order (from node 6 to 0)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor();
            visitFwd(10, 6, true, visitor);
            assertEquals("wrong weight", 6 * 0.06 + 17, visitor.weight, 1.e-3);
            assertEquals("wrong time", (6 * 60 + 17000), visitor.time);
        }

        {
            // unpack the shortcut 6<-0, traverse original edges in 'forward' order (from node 6 to 0)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor();
            visitBwd(10, 0, false, visitor);
            assertEquals("wrong weight", 6 * 0.06 + 21, visitor.weight, 1.e-3);
            assertEquals("wrong time", (6 * 60 + 21000), visitor.time);
        }

        {
            // unpack the shortcut 6<-0, traverse original edges in 'backward' order (from node 0 to 6)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor();
            visitBwd(10, 0, true, visitor);
            assertEquals("wrong weight", 6 * 0.06 + 21, visitor.weight, 1.e-3);
            assertEquals("wrong time", (6 * 60 + 21000), visitor.time);
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

    private void shortcut(int baseNode, int adjNode, int skip1, int skip2, int origFirst, int origLast) {
        // shortcut weight/distance is not important for us here
        double weight = 1;
        if (edgeBased) {
            chGraph.shortcutEdgeBased(baseNode, adjNode, PrepareEncoder.getScFwdDir(), weight, skip1, skip2, origFirst, origLast);
        } else {
            chGraph.shortcut(baseNode, adjNode, PrepareEncoder.getScFwdDir(), weight, skip1, skip2);
        }
    }

    private class TestVisitor implements ShortcutUnpacker.Visitor {
        private final IntArrayList edgeIds = new IntArrayList();
        private final IntArrayList adjNodes = new IntArrayList();
        private final IntArrayList baseNodes = new IntArrayList();
        private final IntArrayList prevOrNextEdgeIds = new IntArrayList();
        private final DoubleArrayList weights = new DoubleArrayList();
        private final DoubleArrayList distances = new DoubleArrayList();
        private final DoubleArrayList times = new DoubleArrayList();

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

    private class TurnWeightingVisitor implements ShortcutUnpacker.Visitor {
        private long time = 0;
        private double weight = 0;

        @Override
        public void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
            time += GHUtility.calcMillisWithTurnMillis(routingCHGraph.getWeighting(), edge, reverse, prevOrNextEdgeId);
            weight += GHUtility.calcWeightWithTurnWeight(routingCHGraph.getWeighting(), edge, reverse, prevOrNextEdgeId);
        }
    }
}