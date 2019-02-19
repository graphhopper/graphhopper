package com.graphhopper.storage;

import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import org.junit.Test;

import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.Assert.assertEquals;

public class TurnCostExtensionTest {

    // 0---1
    // |   /
    // 2--3
    // |
    // 4
    public static void initGraph(Graph g) {
        g.edge(0, 1, 3, true);
        g.edge(0, 2, 1, true);
        g.edge(1, 3, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(2, 4, 1, true);
    }

    /**
     * Test if multiple turn costs can be safely written to the storage and read from it.
     */
    @Test
    public void testMultipleTurnCosts() {
        FlagEncoder carEncoder = new CarFlagEncoder(5, 5, 3);
        FlagEncoder bikeEncoder = new BikeFlagEncoder(5, 5, 3);
        EncodingManager manager = EncodingManager.create(carEncoder, bikeEncoder);
        GraphHopperStorage g = new GraphBuilder(manager).create();
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();

        // introduce some turn costs
        long carRestricted = carEncoder.getTurnFlags(true, 0);
        long carCosts = carEncoder.getTurnFlags(false, 2);
        long bikeRestricted = bikeEncoder.getTurnFlags(true, 0);
        long bikeCosts = bikeEncoder.getTurnFlags(false, 2);

        int edge42 = getEdge(g, 4, 2).getEdge();
        int edge23 = getEdge(g, 2, 3).getEdge();
        int edge31 = getEdge(g, 3, 1).getEdge();
        int edge10 = getEdge(g, 1, 0).getEdge();
        int edge02 = getEdge(g, 0, 2).getEdge();
        int edge24 = getEdge(g, 2, 4).getEdge();

        tcs.mergeOrOverwriteTurnInfo(edge42, 2, edge23, carRestricted, true);
        tcs.mergeOrOverwriteTurnInfo(edge42, 2, edge23, bikeRestricted, true);
        tcs.mergeOrOverwriteTurnInfo(edge23, 3, edge31, carRestricted, true);
        tcs.mergeOrOverwriteTurnInfo(edge23, 3, edge31, bikeCosts, true);
        tcs.mergeOrOverwriteTurnInfo(edge31, 1, edge10, carCosts, true);
        tcs.mergeOrOverwriteTurnInfo(edge31, 1, edge10, bikeRestricted, true);
        tcs.mergeOrOverwriteTurnInfo(edge02, 2, edge24, carRestricted, false);
        tcs.mergeOrOverwriteTurnInfo(edge02, 2, edge24, bikeRestricted, false);

        long flags423 = tcs.getTurnCostFlags(edge42, 2, edge23);
        assertEquals(Double.POSITIVE_INFINITY, carEncoder.getTurnCost(flags423), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeEncoder.getTurnCost(flags423), 0);

        long flags231 = tcs.getTurnCostFlags(edge23, 3, edge31);
        assertEquals(Double.POSITIVE_INFINITY, carEncoder.getTurnCost(flags231), 0);
        assertEquals(2.0, bikeEncoder.getTurnCost(flags231), 0);

        long flags310 = tcs.getTurnCostFlags(edge31, 1, edge10);
        assertEquals(2.0, carEncoder.getTurnCost(flags310), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeEncoder.getTurnCost(flags310), 0);

        long flags024 = tcs.getTurnCostFlags(edge02, 2, edge24);
        assertEquals(0.0, carEncoder.getTurnCost(flags024), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeEncoder.getTurnCost(flags024), 0);

        // merge per default
        tcs.addTurnInfo(edge02, 2, edge23, carRestricted);
        tcs.addTurnInfo(edge02, 2, edge23, bikeRestricted);
        long flags023 = tcs.getTurnCostFlags(edge02, 2, edge23);
        assertEquals(Double.POSITIVE_INFINITY, carEncoder.getTurnCost(flags023), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeEncoder.getTurnCost(flags023), 0);

        // overwrite
        tcs.mergeOrOverwriteTurnInfo(edge02, 2, edge23, bikeRestricted, false);
        flags023 = tcs.getTurnCostFlags(edge02, 2, edge23);
        assertEquals(0, carEncoder.getTurnCost(flags023), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeEncoder.getTurnCost(flags023), 0);

        // clear
        tcs.mergeOrOverwriteTurnInfo(edge02, 2, edge23, 0, false);
        flags023 = tcs.getTurnCostFlags(edge02, 2, edge23);
        assertEquals(0, carEncoder.getTurnCost(flags023), 0);
        assertEquals(0, bikeEncoder.getTurnCost(flags023), 0);
    }

    @Test
    public void testMergeFlagsBeforeAdding() {
        FlagEncoder carEncoder = new CarFlagEncoder(5, 5, 3);
        FlagEncoder bikeEncoder = new BikeFlagEncoder(5, 5, 3);
        EncodingManager manager = EncodingManager.create(carEncoder, bikeEncoder);
        GraphHopperStorage g = new GraphBuilder(manager).create();
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();

        long carRestricted = carEncoder.getTurnFlags(true, 0);
        long bikeRestricted = bikeEncoder.getTurnFlags(true, 0);

        int edge23 = getEdge(g, 2, 3).getEdge();
        int edge02 = getEdge(g, 0, 2).getEdge();

        tcs.addTurnInfo(edge02, 2, edge23, carRestricted | bikeRestricted);

        long flags023 = tcs.getTurnCostFlags(edge02, 2, edge23);
        assertEquals(Double.POSITIVE_INFINITY, carEncoder.getTurnCost(flags023), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeEncoder.getTurnCost(flags023), 0);
    }
}
