package com.graphhopper.storage;

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ShortcutUnpackerTest {

    private FlagEncoder encoder;
    private EncodingManager encodingManager;
    private Weighting weighting;
    private GraphHopperStorage graph;
    private CHGraph chGraph;

    @Before
    public void init() {
        encoder = new CarFlagEncoder(5, 5, 10);
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
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        System.out.println(edge.get(speedEnc));
        System.out.println(edge.getReverse(speedEnc));
        System.out.println(weighting.calcWeight(edge, false, -1));
        graph.freeze();
        chGraph.shortcut(0, 2, PrepareEncoder.getScFwdDir(), 2, 2, 0, 1);
        chGraph.shortcut(2, 4, PrepareEncoder.getScFwdDir(), 2, 2, 2, 3);
        chGraph.shortcut(4, 6, PrepareEncoder.getScFwdDir(), 2, 2, 4, 5);
        chGraph.shortcut(2, 6, PrepareEncoder.getScFwdDir(), 2, 2, 7, 8);
        chGraph.shortcut(0, 6, PrepareEncoder.getScFwdDir(), 2, 2, 6, 9);
        final IntArrayList edgeIds = new IntArrayList();
        final IntArrayList adjNodes = new IntArrayList();
        final DoubleArrayList weights = new DoubleArrayList();
        final DoubleArrayList distances = new DoubleArrayList();
        final DoubleArrayList times = new DoubleArrayList();
        ShortcutUnpacker unpacker = new ShortcutUnpacker(chGraph, new ShortcutUnpacker.Visitor() {
            @Override
            public void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
                edgeIds.add(edge.getEdge());
                adjNodes.add(reverse ? edge.getBaseNode() : edge.getAdjNode());
                weights.add(weighting.calcWeight(edge, reverse, prevOrNextEdgeId));
                distances.add(edge.getDistance());
                times.add(weighting.calcMillis(edge, reverse, prevOrNextEdgeId));
            }
        });
        unpacker.visitOriginalEdges(10, 6, false);
        assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5), edgeIds);
        assertEquals(IntArrayList.from(1, 2, 3, 4, 5, 6), adjNodes);
        System.out.println(weights);
        System.out.println(distances);
        System.out.println(times);

        edgeIds.clear();
        adjNodes.clear();
        weights.clear();
        distances.clear();
        times.clear();
        unpacker.visitOriginalEdges(10, 6, true);
        assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), edgeIds);
        assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), edgeIds);
        System.out.println(weights);
        System.out.println(distances);
        System.out.println(times);
    }
}