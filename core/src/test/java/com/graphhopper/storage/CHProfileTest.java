package com.graphhopper.storage;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortFastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import org.junit.Test;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static org.junit.Assert.assertEquals;

public class CHProfileTest {

    @Test
    public void filename() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        EncodingManager.create(encoder);
        TurnCostStorage tcs = new TurnCostStorage(null, null);
        assertEquals("fastest_car_edge_utc30", CHProfile.edgeBased(new FastestWeighting(encoder, new DefaultTurnCostProvider(encoder, tcs, 30))).toFileName());
        assertEquals("my_profile_name", CHProfile.edgeBased("my_profile_name", new FastestWeighting(encoder, new DefaultTurnCostProvider(encoder, tcs, 30))).toFileName());
        assertEquals("shortest_car_edge_utc-1", CHProfile.edgeBased(new ShortestWeighting(encoder, new DefaultTurnCostProvider(encoder, tcs, INFINITE_U_TURN_COSTS))).toFileName());
        assertEquals("shortest_car_edge", CHProfile.edgeBased(new ShortestWeighting(encoder, NO_TURN_COST_PROVIDER)).toFileName());
        assertEquals("fastest_car_node", CHProfile.nodeBased(new FastestWeighting(encoder)).toFileName());
        assertEquals("short_fastest_car_node", CHProfile.nodeBased(new ShortFastestWeighting(encoder, 0.1)).toFileName());
        assertEquals("your_profile_name", CHProfile.nodeBased("your_profile_name", new ShortFastestWeighting(encoder, 0.1)).toFileName());
    }

}