package com.graphhopper.storage;

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.MotorcycleFlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static com.graphhopper.routing.weighting.TurnWeighting.INFINITE_U_TURN_COSTS;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ShortcutUnpackerTest {
    private final static int PREV_EDGE = 12;
    private final static int NEXT_EDGE = 13;
    private final boolean edgeBased;
    private FlagEncoder encoder;
    private Weighting weighting;
    private GraphHopperStorage graph;
    private CHGraph chGraph;
    private TurnCostExtension turnCostExtension;

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
        // use motorcycle to be able to set different fwd/bwd speeds
        encoder = new MotorcycleFlagEncoder(5, 5, 10);
        EncodingManager encodingManager = EncodingManager.create(encoder);
        weighting = new FastestWeighting(encoder);
        graph = new GraphBuilder(encodingManager).setCHProfiles(new CHProfile(weighting, edgeBased, INFINITE_U_TURN_COSTS)).create();
        chGraph = graph.getCHGraph();
        if (edgeBased) {
            turnCostExtension = (TurnCostExtension) graph.getExtension();
        }
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
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesFwd(10, 6, false, PREV_EDGE);
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
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesFwd(10, 6, true, PREV_EDGE);
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
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesBwd(10, 0, false, NEXT_EDGE);
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
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesBwd(10, 0, true, NEXT_EDGE);
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
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesFwd(10, 5, false, PREV_EDGE);
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
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesFwd(10, 5, true, PREV_EDGE);
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
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesBwd(10, 0, false, NEXT_EDGE);
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
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesBwd(10, 0, true, NEXT_EDGE);
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
        turnCostExtension.addTurnInfo(PREV_EDGE, 0, edge0.getEdge(), encoder.getTurnFlags(false, 2));
        addTurnCost(edge0, edge1, 1, 5);
        addTurnCost(edge1, edge2, 2, 3);
        addTurnCost(edge2, edge3, 3, 2);
        addTurnCost(edge3, edge4, 4, 1);
        addTurnCost(edge4, edge5, 5, 4);
        turnCostExtension.addTurnInfo(edge5.getEdge(), 6, NEXT_EDGE, encoder.getTurnFlags(false, 6));
        // turn costs <-
        turnCostExtension.addTurnInfo(NEXT_EDGE, 6, edge5.getEdge(), encoder.getTurnFlags(false, 2));
        addTurnCost(edge5, edge4, 5, 3);
        addTurnCost(edge4, edge3, 4, 2);
        addTurnCost(edge3, edge2, 3, 4);
        addTurnCost(edge2, edge1, 2, 1);
        addTurnCost(edge1, edge0, 1, 0);
        turnCostExtension.addTurnInfo(edge0.getEdge(), 0, PREV_EDGE, encoder.getTurnFlags(false, 1));

        shortcut(0, 2, 0, 1, 0, 1);
        shortcut(2, 4, 2, 3, 2, 3);
        shortcut(4, 6, 4, 5, 4, 5);
        shortcut(2, 6, 7, 8, 2, 5);
        shortcut(0, 6, 6, 9, 0, 5);

        {
            // unpack the shortcut 0->6, traverse original edges in 'forward' order (from node 0 to 6)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor();
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesFwd(10, 6, false, PREV_EDGE);
            assertEquals("wrong weight", 6 * 0.06 + 17, visitor.weight, 1.e-3);
            assertEquals("wrong time", (6 * 60 + 17000), visitor.time);
        }

        {
            // unpack the shortcut 0->6, traverse original edges in 'backward' order (from node 6 to 0)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor();
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesFwd(10, 6, true, PREV_EDGE);
            assertEquals("wrong weight", 6 * 0.06 + 17, visitor.weight, 1.e-3);
            assertEquals("wrong time", (6 * 60 + 17000), visitor.time);
        }

        {
            // unpack the shortcut 6<-0, traverse original edges in 'forward' order (from node 6 to 0)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor();
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesBwd(10, 0, false, NEXT_EDGE);
            assertEquals("wrong weight", 6 * 0.06 + 21, visitor.weight, 1.e-3);
            assertEquals("wrong time", (6 * 60 + 21000), visitor.time);
        }

        {
            // unpack the shortcut 6<-0, traverse original edges in 'backward' order (from node 0 to 6)
            TurnWeightingVisitor visitor = new TurnWeightingVisitor();
            new ShortcutUnpacker(chGraph, visitor, edgeBased).visitOriginalEdgesBwd(10, 0, true, NEXT_EDGE);
            assertEquals("wrong weight", 6 * 0.06 + 21, visitor.weight, 1.e-3);
            assertEquals("wrong time", (6 * 60 + 21000), visitor.time);
        }
    }

    private void addTurnCost(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode, double costs) {
        turnCostExtension.addTurnInfo(inEdge.getEdge(), viaNode, outEdge.getEdge(), encoder.getTurnFlags(false, costs));
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
            weights.add(weighting.calcWeight(edge, reverse, prevOrNextEdgeId));
            distances.add(edge.getDistance());
            times.add(weighting.calcMillis(edge, reverse, prevOrNextEdgeId));
            prevOrNextEdgeIds.add(prevOrNextEdgeId);
        }
    }

    private class TurnWeightingVisitor implements ShortcutUnpacker.Visitor {
        private final TurnWeighting turnWeighting = new TurnWeighting(weighting, turnCostExtension);
        private long time = 0;
        private double weight = 0;

        @Override
        public void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
            time += turnWeighting.calcMillis(edge, reverse, prevOrNextEdgeId);
            weight += turnWeighting.calcWeight(edge, reverse, prevOrNextEdgeId);
        }
    }
}