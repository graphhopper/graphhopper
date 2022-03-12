package com.graphhopper.routing;

import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EVCollection;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class TryAPITest {

    @Test
    void bla() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        EVCollection evCollection = new EVCollection();
        encoder.setEncodedValueLookup(evCollection);
        List<EncodedValue> encodedValues = new ArrayList<>();
        encoder.createEncodedValues(encodedValues);
        for (EncodedValue ev : encodedValues)
            evCollection.addEncodedValue(ev, true);

        BaseGraph baseGraph = new BaseGraph(new RAMDirectory(), evCollection.getIntsForFlags(), false, false, -1);
        EdgeIteratorState edge = baseGraph.edge(0, 1);
        edge.set(encoder.getAverageSpeedEnc(), 10);
        FastestWeighting weighting = new FastestWeighting(encoder);
        Dijkstra dijkstra = new Dijkstra(baseGraph, weighting, TraversalMode.NODE_BASED);
        Path path = dijkstra.calcPath(0, 1);
        System.out.println(path.getDistance());
    }
}
