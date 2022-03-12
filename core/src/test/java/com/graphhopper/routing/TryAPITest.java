package com.graphhopper.routing;

import com.graphhopper.routing.util.EVCollection;
import com.graphhopper.routing.util.RoutingFlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TryAPITest {

    @Test
    void testAPI() {
        RoutingFlagEncoder encoder = RoutingFlagEncoder.forTest("car");
        EVCollection evCollection = encoder.getEvCollection();
        BaseGraph graph = BaseGraph.inMemoryGraph(evCollection.getIntsForFlags());
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1000));
        FastestWeighting weighting = new FastestWeighting(encoder);
        Dijkstra dijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
        Path path = dijkstra.calcPath(0, 1);
        assertTrue(path.isFound());
        assertEquals(1000, path.getDistance());
    }
}
