package com.graphhopper.routing.ch;


import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.EdgeBasedRoutingAlgorithmTest;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_ONE_TO_MANY;

@RunWith(Parameterized.class)
public class EdgeBasedCHTest extends EdgeBasedRoutingAlgorithmTest{

    public EdgeBasedCHTest() {
        super("");
    }

    @Override
    protected GraphHopperStorage createStorage(EncodingManager em) {
        return new GraphBuilder(em).setCHGraph(new FastestWeighting(em.fetchEdgeEncoders().get(0))).create();
    }
}
