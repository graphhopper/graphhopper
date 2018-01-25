package com.graphhopper.routing.ch;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Only some rudimentary tests here, because the code here is more or less an implementation detail of
 * {@link EdgeBasedNodeContractor} and is extensively tested via the corresponding tests.
 */
public class WitnessPathFinderTest {
    private Weighting weighting;
    private GraphHopperStorage graph;
    private CHGraph chGraph;

    @Before
    public void init() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager(encoder);
        weighting = new ShortestWeighting(encoder);
        graph = new GraphBuilder(encodingManager).setCHGraph(weighting).setEdgeBasedCH(true).create();
        chGraph = graph.getGraph(CHGraph.class);
    }


    @Test
    public void findTarget_works() {
        // 0 -> 1 --> 2
        //       \
        //        \-> 3
        graph.edge(0, 1, 2, false);
        graph.edge(1, 2, 1, false);
        graph.edge(1, 3, 2, false);
        graph.freeze();
        WitnessSearchEntry entry = new WitnessSearchEntry(0, 0, 1, 8);
        entry.possibleShortcut = true;
        List<WitnessSearchEntry> initialEntries = Collections.singletonList(entry);
        WitnessPathFinder witnessPathFinder = new WitnessPathFinder(chGraph, weighting, TraversalMode.EDGE_BASED_2DIR);
        witnessPathFinder.setInitialEntries(initialEntries);
        witnessPathFinder.findTarget(1, 2);
        witnessPathFinder.findTarget(2, 3);
        CHEntry entry1 = witnessPathFinder.getFoundEntry(1, 2);
        CHEntry entry2 = witnessPathFinder.getFoundEntry(2, 3);
        assertEquals(9, entry1.weight, 1.e-6);
        assertEquals(10, entry2.weight, 1.e-6);
    }

}