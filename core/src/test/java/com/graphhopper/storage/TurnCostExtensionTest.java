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
        TurnCostAccess bikeTCAccess = new TurnCostAccess(bikeEncoder.toString(), g);
        TurnCostAccess carTCAccess = new TurnCostAccess(carEncoder.toString(), g);

        int edge42 = getEdge(g, 4, 2).getEdge();
        int edge23 = getEdge(g, 2, 3).getEdge();
        int edge31 = getEdge(g, 3, 1).getEdge();
        int edge10 = getEdge(g, 1, 0).getEdge();
        int edge02 = getEdge(g, 0, 2).getEdge();
        int edge24 = getEdge(g, 2, 4).getEdge();

        carTCAccess.addRestriction(edge42, 2, edge23);
        bikeTCAccess.addRestriction(edge42, 2, edge23);
        carTCAccess.addRestriction(edge23, 3, edge31);
        bikeTCAccess.add(edge23, 3, edge31, 2.0);
        carTCAccess.add(edge31, 1, edge10, 2.0);
        bikeTCAccess.addRestriction(edge31, 1, edge10);
        bikeTCAccess.clear(edge02, 2, edge24);
        bikeTCAccess.addRestriction(edge02, 2, edge24);

        assertEquals(Double.POSITIVE_INFINITY, carTCAccess.get(edge42, 2, edge23), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeTCAccess.get(edge42, 2, edge23), 0);

        assertEquals(Double.POSITIVE_INFINITY, carTCAccess.get(edge23, 3, edge31), 0);
        assertEquals(2.0, bikeTCAccess.get(edge23, 3, edge31), 0);

        assertEquals(2.0, carTCAccess.get(edge31, 1, edge10), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeTCAccess.get(edge31, 1, edge10), 0);

        assertEquals(0.0, carTCAccess.get(edge02, 2, edge24), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeTCAccess.get(edge02, 2, edge24), 0);

        // merge per default
        carTCAccess.addRestriction(edge02, 2, edge23);
        bikeTCAccess.addRestriction(edge02, 2, edge23);
        assertEquals(Double.POSITIVE_INFINITY, carTCAccess.get(edge02, 2, edge23), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeTCAccess.get(edge02, 2, edge23), 0);

        // overwrite unrelated turn cost value
        bikeTCAccess.clear(edge02, 2, edge23);
        carTCAccess.clear(edge02, 2, edge23);
        bikeTCAccess.addRestriction(edge02, 2, edge23);
        assertEquals(0, carTCAccess.get(edge02, 2, edge23), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeTCAccess.get(edge02, 2, edge23), 0);

        // clear
        bikeTCAccess.clear(edge02, 2, edge23);
        assertEquals(0, carTCAccess.get(edge02, 2, edge23), 0);
        assertEquals(0, bikeTCAccess.get(edge02, 2, edge23), 0);
    }

    @Test
    public void testMergeFlagsBeforeAdding() {
        FlagEncoder carEncoder = new CarFlagEncoder(5, 5, 3);
        FlagEncoder bikeEncoder = new BikeFlagEncoder(5, 5, 3);
        EncodingManager manager = EncodingManager.create(carEncoder, bikeEncoder);
        GraphHopperStorage g = new GraphBuilder(manager).create();
        initGraph(g);

        int edge23 = getEdge(g, 2, 3).getEdge();
        int edge02 = getEdge(g, 0, 2).getEdge();
        TurnCostAccess carTCAccess = new TurnCostAccess(carEncoder.toString(), g).addRestriction(edge02, 2, edge23);
        TurnCostAccess bikeTCAccess = new TurnCostAccess(bikeEncoder.toString(), g).addRestriction(edge02, 2, edge23);
        assertEquals(Double.POSITIVE_INFINITY, carTCAccess.get(edge02, 2, edge23), 0);
        assertEquals(Double.POSITIVE_INFINITY, bikeTCAccess.get(edge02, 2, edge23), 0);
    }
}
