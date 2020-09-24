package com.graphhopper.reader;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoadClassLinkInterpolatorTest {

    @Test
    public void simpleLinkInitialization() {
        EncodingManager em = EncodingManager.create(new CarFlagEncoder());
        EnumEncodedValue<RoadClass> rcEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        BooleanEncodedValue rcLinkEnc = em.getBooleanEncodedValue(RoadClassLink.KEY);
        DecimalEncodedValue avgSpeed = em.getDecimalEncodedValue(EncodingManager.getKey("car", "average_speed"));

        //  0---1------2-----3 -> motorway
        //       \----/        -> motorway_link
        Graph graph = new GraphHopperStorage(new RAMDirectory(), em, false).create(100);
        graph.edge(0, 1).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).setName("01");
        graph.edge(1, 2).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).setName("12");
        graph.edge(1, 2).set(avgSpeed, 110).set(rcEnc, RoadClass.MOTORWAY).set(rcLinkEnc, true).setName("12_link");
        graph.edge(2, 3).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).setName("23");

        RoadClassLinkInterpolator interpolator = new RoadClassLinkInterpolator(graph, em, RoadClassLinkInterpolator.collect(em));
        interpolator.execute();

        AllEdgesIterator allIter = graph.getAllEdges();
        while (allIter.next()) {
            assertEquals(100, allIter.get(avgSpeed), .1, allIter.getName());
        }
    }

    @Test
    public void ignoreBike() {

    }

}