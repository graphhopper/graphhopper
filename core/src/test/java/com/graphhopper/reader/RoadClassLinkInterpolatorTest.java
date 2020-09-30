package com.graphhopper.reader;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.BikeCommonFlagEncoder;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.spatialrules.TransportationMode;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Translation;
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
        EdgeIteratorState edge01 = graph.edge(0, 1).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).setName("01");
        EdgeIteratorState edge12 = graph.edge(1, 2).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).setName("12");
        EdgeIteratorState link = graph.edge(1, 2).set(avgSpeed, 110).set(rcEnc, RoadClass.MOTORWAY).set(rcLinkEnc, true).setName("12_link");
        EdgeIteratorState edge23 = graph.edge(2, 3).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).setName("23");

        RoadClassLinkInterpolator interpolator = new RoadClassLinkInterpolator(graph, em, RoadClassLinkInterpolator.collect(em, 0.85));
        interpolator.execute();

        assertEquals(100, GHUtility.getEdge(graph, 0, 1).get(avgSpeed), .1, edge01.getName());
        assertEquals(100, graph.getEdgeIteratorState(edge12.getEdge(), edge12.getAdjNode()).get(avgSpeed), .1, edge12.getName());
        assertEquals(85, graph.getEdgeIteratorState(link.getEdge(), link.getAdjNode()).get(avgSpeed), .1, link.getName());
        assertEquals(100, GHUtility.getEdge(graph, 2, 3).get(avgSpeed), .1, edge23.getName());
    }

    @Test
    public void linkInterpolation() {
        EncodingManager em = EncodingManager.create(new CarFlagEncoder().setSpeedTwoDirections(true));
        EnumEncodedValue<RoadClass> rcEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        BooleanEncodedValue rcLinkEnc = em.getBooleanEncodedValue(RoadClassLink.KEY);
        DecimalEncodedValue avgSpeed = em.getDecimalEncodedValue(EncodingManager.getKey("car", "average_speed"));

        //  0-->1-------------2---->3 -> motorway
        //       \->\     /->/        -> motorway_link
        //           \4--5            -> primary
        Graph graph = new GraphHopperStorage(new RAMDirectory(), em, false).create(100);
        graph.edge(0, 1).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).setName("01");
        graph.edge(1, 2).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).setName("12");
        graph.edge(2, 3).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).setName("23");
        graph.edge(1, 4).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).set(rcLinkEnc, true).setName("14_link");
        graph.edge(5, 2).set(avgSpeed, 100).set(rcEnc, RoadClass.MOTORWAY).set(rcLinkEnc, true).setName("52_link");
        graph.edge(4, 5).set(avgSpeed, 75).setReverse(avgSpeed, 70).set(rcEnc, RoadClass.PRIMARY).setName("45");

        RoadClassLinkInterpolator interpolator = new RoadClassLinkInterpolator(graph, em, RoadClassLinkInterpolator.collect(em, 0.85));
        interpolator.execute();

        assertEquals(100, GHUtility.getEdge(graph, 0, 1).get(avgSpeed), .1);
        assertEquals(0, GHUtility.getEdge(graph, 0, 1).getReverse(avgSpeed), .1);
        assertEquals(100, GHUtility.getEdge(graph, 1, 2).get(avgSpeed), .1);
        assertEquals(100, GHUtility.getEdge(graph, 2, 3).get(avgSpeed), .1);
        assertEquals(85, GHUtility.getEdge(graph, 1, 4).get(avgSpeed), .1);
        assertEquals(85, GHUtility.getEdge(graph, 5, 2).get(avgSpeed), .1);
        assertEquals(75, GHUtility.getEdge(graph, 4, 5).get(avgSpeed), .1);
        assertEquals(70, GHUtility.getEdge(graph, 4, 5).getReverse(avgSpeed), .1);
    }

    @Test
    public void keepBikeSpeed() {
        EncodingManager em = EncodingManager.create(new CarFlagEncoder(new PMap().putObject("speed_factor", 2)).setSpeedTwoDirections(true),
                new BikeFlagEncoder());
        EnumEncodedValue<RoadClass> rcEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        BooleanEncodedValue rcLinkEnc = em.getBooleanEncodedValue(RoadClassLink.KEY);
        DecimalEncodedValue carSpeed = em.getDecimalEncodedValue(EncodingManager.getKey("car", "average_speed"));
        DecimalEncodedValue bikeSpeed = em.getDecimalEncodedValue(EncodingManager.getKey("bike", "average_speed"));

        //  0-->1----------2 -> primary (car: 30km/h bike: 35km/h -> unrealistic but check that it is not touched from the car value)
        //       \->\        -> primary_link (car: 30km/h bike: 35km/h -> unrealistic but check that it is not touched)
        //           \3--4   -> secondary
        Graph graph = new GraphHopperStorage(new RAMDirectory(), em, false).create(100);
        graph.edge(0, 1).set(carSpeed, 25).set(bikeSpeed, 28).set(rcEnc, RoadClass.PRIMARY).setName("01");
        graph.edge(1, 2).set(carSpeed, 25).set(bikeSpeed, 28).set(rcEnc, RoadClass.PRIMARY).setName("12");
        graph.edge(1, 3).set(carSpeed, 25).set(bikeSpeed, 30).set(rcEnc, RoadClass.PRIMARY).set(rcLinkEnc, true).setName("13_link");
        graph.edge(3, 4).set(carSpeed, 20).set(bikeSpeed, 28).set(rcEnc, RoadClass.SECONDARY).setName("34");

        RoadClassLinkInterpolator interpolator = new RoadClassLinkInterpolator(graph, em, RoadClassLinkInterpolator.collect(em, 0.85));
        interpolator.execute();

        assertEquals(26, GHUtility.getEdge(graph, 0, 1).get(carSpeed), .1);
        assertEquals(22, GHUtility.getEdge(graph, 1, 3).get(carSpeed), .1);
        assertEquals(20, GHUtility.getEdge(graph, 3, 4).get(carSpeed), .1);

        // for bike we reduce speed for link too, but do not apply the factor
        assertEquals(28, GHUtility.getEdge(graph, 0, 1).get(bikeSpeed), .1);
        assertEquals(28, GHUtility.getEdge(graph, 1, 3).get(bikeSpeed), .1);
        assertEquals(28, GHUtility.getEdge(graph, 3, 4).get(bikeSpeed), .1);
    }
}