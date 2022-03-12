package com.graphhopper.routing;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TryAPITest {

    @Test
    void testAPI() {
        EVCollection evCollection = new EVCollection();
        String prefix = "car";
        // road_access is required by FastestWeighting
        EnumEncodedValue<RoadAccess> roadAccessEnc = new EnumEncodedValue<>(RoadAccess.KEY, RoadAccess.class);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl(EncodingManager.getKey(prefix, "average_speed"), 5, 5, true);
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue(EncodingManager.getKey(prefix, "access"), true);
        evCollection.addEncodedValue(roadAccessEnc, false);
        evCollection.addEncodedValue(speedEnc, true);
        evCollection.addEncodedValue(accessEnc, true);

        BaseGraph baseGraph = new BaseGraph(new RAMDirectory(), evCollection.getIntsForFlags(), false, false, -1);
        EdgeIteratorState edge = baseGraph.edge(0, 1).setDistance(1000);
        edge.set(speedEnc, 10);
        edge.set(accessEnc, true);
        RoutingFlagEncoder encoder = new RoutingFlagEncoder(evCollection, prefix, TransportationMode.CAR, 140);
        FastestWeighting weighting = new FastestWeighting(encoder);
        Dijkstra dijkstra = new Dijkstra(baseGraph, weighting, TraversalMode.NODE_BASED);
        Path path = dijkstra.calcPath(0, 1);
        assertTrue(path.isFound());
        assertEquals(1000, path.getDistance());
    }
}
