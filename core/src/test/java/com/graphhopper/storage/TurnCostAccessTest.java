package com.graphhopper.storage;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TurnCostAccessTest {

    @Test
    public void testCarTurnFlagEncoding_withCosts() {
        FlagEncoder tmpEncoder = new CarFlagEncoder(8, 5, 127);
        EncodingManager em = new EncodingManager.Builder().add(tmpEncoder).build();
        GraphHopperStorage graph = new GraphBuilder(em).create();
        TurnCostAccess tca = new TurnCostAccess("car", graph);

        tca.add(0, 1, 2, 10);
        tca.addRestriction(0, 1, 3);

        assertTrue(Double.isInfinite(tca.get(0, 1, 3)));
        assertEquals(0, tca.get(1, 1, 2), 1e-1);
        assertEquals(10, tca.get(0, 1, 2), 1e-1);

        try {
            tca.add(1, 1, 2, 220);
            assertTrue(false);
        } catch (Exception ex) {
        }
        assertEquals(0, tca.get(1, 1, 2), 1e-1);
    }
}