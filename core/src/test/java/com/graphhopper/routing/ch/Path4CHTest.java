package com.graphhopper.routing.ch;

import com.graphhopper.routing.AbstractBidirectionEdgeCHNoSOD;
import com.graphhopper.routing.DijkstraBidirectionEdgeCHNoSOD;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.LevelEdgeFilter;
import com.graphhopper.routing.util.MotorcycleFlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.Before;
import org.junit.Test;

import static com.graphhopper.routing.weighting.TurnWeighting.INFINITE_U_TURN_COSTS;
import static org.junit.Assert.assertEquals;

public class Path4CHTest {
    private final int maxTurnCosts = 10;
    private GraphHopperStorage graph;
    private CHGraph chGraph;
    private FlagEncoder encoder;
    private Weighting weighting;
    private TurnCostExtension turnCostExtension;

    @Before
    public void init() {
        encoder = new MotorcycleFlagEncoder(5, 5, maxTurnCosts);
        EncodingManager em = EncodingManager.create(encoder);
        weighting = new FastestWeighting(encoder);
        graph = new GraphBuilder(em).setCHProfiles(CHProfile.edgeBased(weighting, INFINITE_U_TURN_COSTS)).create();
        chGraph = graph.getCHGraph();
        turnCostExtension = (TurnCostExtension) graph.getExtension();
    }

    @Test
    public void shortcut_chain() {
        // 0   2   4   6   8
        //  \ / \ / \ / \ /
        //   1   3   5   7
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.edge(4, 5, 1, false);
        graph.edge(5, 6, 1, false);
        graph.edge(6, 7, 1, false);
        graph.edge(7, 8, 1, false);
        graph.freeze();
        addTurnCost(1, 2, 3, 4);
        addTurnCost(3, 4, 5, 2);
        addTurnCost(5, 6, 7, 3);
        // we 'contract' the graph such that only a few shortcuts are created and that the fwd/bwd searches for the
        // 0-8 query meet at node 4 (make sure we include all three cases where turn cost times might come to play:
        // fwd/bwd search and meeting point)
        addShortcut(0, 2, 0, 1, 0, 1, 0.12, 0);
        addShortcut(2, 4, 2, 3, 2, 3, 0.12, 0);
        addShortcut(4, 6, 4, 5, 4, 5, 0.12, 0);
        addShortcut(6, 8, 6, 7, 6, 7, 0.12, 0);
        setCHOrder(1, 3, 5, 7, 0, 8, 2, 6, 4);

        // going from 0 to 8 will create shortest path tree entries that follow the shortcuts.
        // it is important that the original edge ids are used to calculate the turn costs.
        checkPath(0, 8, 0.48, 8, 9);
    }

    private void setCHOrder(int... nodeIds) {
        for (int i = 0; i < nodeIds.length; i++) {
            chGraph.setLevel(nodeIds[i], i);
        }
    }

    @Test
    public void paths_different_fwd_bwd_speeds() {
        //   5 3 2 1 4    turn costs ->
        // 0-1-2-3-4-5-6
        //   0 1 4 2 3    turn costs <-
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
        addTurnCost(edge0, edge1, 1, 5);
        addTurnCost(edge1, edge2, 2, 3);
        addTurnCost(edge2, edge3, 3, 2);
        addTurnCost(edge3, edge4, 4, 1);
        addTurnCost(edge4, edge5, 5, 4);
        // turn costs <-
        addTurnCost(edge5, edge4, 5, 3);
        addTurnCost(edge4, edge3, 4, 2);
        addTurnCost(edge3, edge2, 3, 4);
        addTurnCost(edge2, edge1, 2, 1);
        addTurnCost(edge1, edge0, 1, 0);

        // shortcuts ->
        addShortcut(0, 2, 0, 1, 0, 1, 0.12, 5);
        addShortcut(2, 4, 2, 3, 2, 3, 0.12, 2);
        addShortcut(4, 6, 4, 5, 4, 5, 0.12, 4);
        addShortcut(2, 6, 2, 5, 7, 8, 0.24, 7);
        addShortcut(0, 6, 0, 5, 6, 9, 0.36, 12);

        // shortcuts <-
        addShortcut(6, 4, 5, 4, 5, 4, 0.24, 3);
        addShortcut(4, 2, 3, 2, 3, 2, 0.24, 4);
        addShortcut(2, 0, 1, 0, 1, 0, 0.24, 0);
        addShortcut(6, 2, 5, 2, 11, 12, 0.48, 9);
        addShortcut(6, 0, 5, 0, 14, 13, 0.60, 10);

        // strictly it would be cleaner to manually build the SPT and extract the path, but for convenience we
        // use the routing algo to build it
        checkPath(0, 6, 0.36, 6, 15);
        checkPath(6, 0, 0.72, 6, 10);
        checkPath(1, 3, 0.12, 2, 3);
        checkPath(3, 1, 0.24, 2, 1);
        checkPath(1, 5, 0.24, 4, 6);
        checkPath(5, 1, 0.48, 4, 7);
    }

    private void addTurnCost(int from, int via, int to, int cost) {
        addTurnCost(getEdge(from, via), getEdge(via, to), via, cost);
    }

    private void addTurnCost(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode, int cost) {
        turnCostExtension.addTurnInfo(inEdge.getEdge(), viaNode, outEdge.getEdge(), encoder.getTurnFlags(false, cost));
    }

    private EdgeIteratorState getEdge(int from, int to) {
        return GHUtility.getEdge(graph, from, to);
    }

    private void addShortcut(int from, int to, int origFirst, int origLast, int skip1, int skip2, double edgeWeight, int turnCost) {
        double weight = edgeWeight + turnCost * 1000;
        chGraph.shortcutEdgeBased(from, to, PrepareEncoder.getScFwdDir(), weight, skip1, skip2, origFirst, origLast);
    }

    private void checkPath(int from, int to, double edgeWeight, int distance, int turnCostTime) {
        double expectedWeight = (edgeWeight + turnCostTime);
        Path path = createAlgo().calcPath(from, to);
        assertEquals("wrong weight", expectedWeight, path.getWeight(), 1.e-3);
        assertEquals("wrong distance", distance, path.getDistance(), 1.e-3);
        assertEquals("wrong time", expectedWeight * 1000, path.getTime(), 1.e-3);
    }

    private AbstractBidirectionEdgeCHNoSOD createAlgo() {
        TurnWeighting chTurnWeighting = new TurnWeighting(new PreparationWeighting(weighting), turnCostExtension);
        CHGraph lg = graph.getCHGraph();
        AbstractBidirectionEdgeCHNoSOD algo = new DijkstraBidirectionEdgeCHNoSOD(lg, chTurnWeighting);
        algo.setEdgeFilter(new LevelEdgeFilter(lg));
        return algo;
    }

}