package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Only some rudimentary tests here, because the code here is more or less an implementation detail of
 * {@link EdgeBasedNodeContractor} and is extensively tested via the corresponding tests.
 */
public class LegacyWitnessPathFinderTest {
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
        WitnessSearchEntry entry = new WitnessSearchEntry(0, 0, 1, 8, true);
        entry.onOrigPath = true;
        IntObjectMap<WitnessSearchEntry> initialEntries = new IntObjectHashMap<>();
        int edgeKey = getEdgeKey(entry.incEdge, entry.adjNode);
        initialEntries.put(edgeKey, entry);
        int maxLevel = chGraph.getNodes();
        for (int i = 0; i < maxLevel; ++i) {
            chGraph.setLevel(i, maxLevel);
        }
        LegacyWitnessPathFinder witnessPathFinder = new MapBasedLegacyWitnessPathFinder(chGraph, weighting, TraversalMode.EDGE_BASED_2DIR, maxLevel);
        witnessPathFinder.setInitialEntries(initialEntries);
        witnessPathFinder.findTarget(1, 2);
        witnessPathFinder.findTarget(2, 3);
        CHEntry entry1 = witnessPathFinder.getFoundEntry(1, 2);
        CHEntry entry2 = witnessPathFinder.getFoundEntry(2, 3);
        assertEquals(9, entry1.weight, 1.e-6);
        assertEquals(10, entry2.weight, 1.e-6);
    }

    private int getEdgeKey(int edge, int adjNode) {
        // todo: this is similar to some code in DijkstraBidirectionEdgeCHNoSOD and should be cleaned up, see comments there
        EdgeIteratorState eis = graph.getEdgeIteratorState(edge, adjNode);
        return GHUtility.createEdgeKey(eis.getBaseNode(), eis.getAdjNode(), eis.getEdge(), false);
    }
}