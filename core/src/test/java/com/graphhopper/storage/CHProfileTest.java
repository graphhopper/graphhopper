package com.graphhopper.storage;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CHProfileTest {

    @Test
    public void filename() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        EncodingManager.create(encoder);
        assertEquals("fastest_car_edge_utc30", CHProfile.edgeBased(new FastestWeighting(encoder, new DefaultTurnCostProvider(encoder, new TurnCostStorage(null, null), 30)), 30).toFileName());
        assertEquals("shortest_car_node", CHProfile.nodeBased(new ShortestWeighting(encoder)).toFileName());
    }

}