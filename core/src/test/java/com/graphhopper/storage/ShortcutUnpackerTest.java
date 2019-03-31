package com.graphhopper.storage;

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.MotorcycleFlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ShortcutUnpackerTest {

    private FlagEncoder encoder;
    private EncodingManager encodingManager;
    private Weighting weighting;
    private GraphHopperStorage graph;
    private CHGraph chGraph;

    @Before
    public void init() {
        // use motorcycle to be able to set different fwd/bwd speeds
        encoder = new MotorcycleFlagEncoder(5, 5, 10);
        encodingManager = EncodingManager.create(encoder);
        weighting = new FastestWeighting(encoder);
        graph = new GraphBuilder(encodingManager).setCHGraph(weighting).create();
        chGraph = graph.getGraph(CHGraph.class, weighting);
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
        chGraph.shortcut(0, 2, PrepareEncoder.getScFwdDir(), 2, 2, 0, 1);
        chGraph.shortcut(2, 4, PrepareEncoder.getScFwdDir(), 2, 2, 2, 3);
        chGraph.shortcut(4, 6, PrepareEncoder.getScFwdDir(), 2, 2, 4, 5);
        chGraph.shortcut(2, 6, PrepareEncoder.getScFwdDir(), 2, 2, 7, 8);
        chGraph.shortcut(0, 6, PrepareEncoder.getScFwdDir(), 2, 2, 6, 9);

        {
            // unpack the shortcut 0-6, traverse original edges in 'forward' order (from node 0 to 6)
            TestVisitor visitor = new TestVisitor();
            new ShortcutUnpacker(chGraph, visitor).visitOriginalEdges(10, 6, false);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.edgeIds);
            assertEquals(IntArrayList.from(1, 2, 3, 4, 5, 6), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
        }

        {
            // unpack the shortcut 0-6, traverse original edges in 'backward' order (from node 6 to 0)
            // note that traversing in backward order does not mean the original edges are read in reverse (e.g. fwd speed still applies)
            // -> only the order of the original edges is reversed
            TestVisitor visitor = new TestVisitor();
            new ShortcutUnpacker(chGraph, visitor).visitOriginalEdges(10, 6, true);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.edgeIds);
            assertEquals(IntArrayList.from(6, 5, 4, 3, 2, 1), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.06, 0.06, 0.06, 0.06, 0.06, 0.06), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(60, 60, 60, 60, 60, 60), visitor.times);
        }

        {
            // unpack the shortcut 6-0, traverse original edges in 'forward' order (from node 6 to 0)
            TestVisitor visitor = new TestVisitor();
            new ShortcutUnpacker(chGraph, visitor).visitOriginalEdges(10, 0, false);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.edgeIds);
            assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.12, 0.12, 0.12, 0.12, 0.12, 0.12), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(120, 120, 120, 120, 120, 120), visitor.times);
        }

        {
            // unpack the shortcut 6-0, traverse original edges in 'backward' order (from node 0 to 6)
            TestVisitor visitor = new TestVisitor();
            new ShortcutUnpacker(chGraph, visitor).visitOriginalEdges(10, 0, true);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.edgeIds);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), visitor.adjNodes);
            assertEquals(DoubleArrayList.from(0.12, 0.12, 0.12, 0.12, 0.12, 0.12), visitor.weights);
            assertEquals(DoubleArrayList.from(1, 1, 1, 1, 1, 1), visitor.distances);
            assertEquals(DoubleArrayList.from(120, 120, 120, 120, 120, 120), visitor.times);
        }
    }

    private class TestVisitor implements ShortcutUnpacker.Visitor {
        private final IntArrayList edgeIds = new IntArrayList();
        private final IntArrayList adjNodes = new IntArrayList();
        private final DoubleArrayList weights = new DoubleArrayList();
        private final DoubleArrayList distances = new DoubleArrayList();
        private final DoubleArrayList times = new DoubleArrayList();

        @Override
        public void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
            edgeIds.add(edge.getEdge());
            adjNodes.add(reverse ? edge.getBaseNode() : edge.getAdjNode());
            weights.add(weighting.calcWeight(edge, reverse, prevOrNextEdgeId));
            distances.add(edge.getDistance());
            times.add(weighting.calcMillis(edge, reverse, prevOrNextEdgeId));
        }
    }
}