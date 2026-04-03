package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BusCustomModelTest {

    private EncodingManager em;
    private OSMParsers parsers;
    private CustomWeighting.Parameters params;

    @BeforeEach
    public void setup() {
        BooleanEncodedValue busAccess = BusAccess.create();
        EnumEncodedValue<RoadClass> roadClass = RoadClass.create();
        DecimalEncodedValue maxHeight = MaxHeight.create();
        DecimalEncodedValue maxWidth = MaxWidth.create();
        DecimalEncodedValue maxWeight = MaxWeight.create();
        em = new EncodingManager.Builder().
                add(busAccess).
                add(VehicleSpeed.create("car", 5, 5, false)).
                add(RoadEnvironment.create()).
                add(Roundabout.create()).add(RoadAccess.create()).add(roadClass).add(FerrySpeed.create()).
                add(maxWeight).add(maxWidth).add(maxHeight).add(MaxSpeed.create()).
                build();

        parsers = new OSMParsers().
                addWayTagParser(new OSMRoadClassParser(roadClass)).
                addWayTagParser(new OSMMaxWeightParser(maxHeight)).
                addWayTagParser(new OSMMaxWidthParser(maxWidth)).
                addWayTagParser(new OSMMaxWeightParser(maxWeight)).
                addWayTagParser(new ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.BUS),
                        busAccess, true, em.getBooleanEncodedValue(Roundabout.KEY),
                        Set.of()));

        CustomModel cm = GHUtility.loadCustomModelFromJar("bus.json");
        params = CustomModelParser.createWeightingParameters(cm, em);
    }

    double priority(ReaderWay way) {
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        parsers.handleWayTags(edge.getEdge(), graph.getEdgeAccess(), way, em.createRelationFlags());
        return params.getEdgeToPriorityMapping().get(edge, false);
    }

    @Test
    public void testNormalRoads() {
        for (String highway : List.of("primary", "secondary", "tertiary", "residential", "unclassified", "living_street", "trunk")) {
            ReaderWay way = new ReaderWay(0L);
            way.setTag("highway", highway);
            assertEquals(1, priority(way), 0.01, highway + " should be accessible");
        }
    }

    @Test
    public void testNonMotorVehicleHighways() {
        for (String highway : List.of("steps", "footway", "cycleway", "pedestrian", "path", "bridleway")) {
            ReaderWay way = new ReaderWay(0L);
            way.setTag("highway", highway);
            assertEquals(0, priority(way), 0.01, highway + " should be blocked");
        }
    }

    @Test
    public void testTrack() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "track");
        assertEquals(0, priority(way), 0.01);
    }

    @Test
    public void testBusway() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "busway");
        assertEquals(1, priority(way), 0.01);
    }

    @Test
    public void testFootwayWithExplicitBusYes() {
        // e.g. a shared bus/pedestrian zone
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "footway");
        way.setTag("bus", "yes");
        assertEquals(1, priority(way), 0.01);
    }

    @Test
    public void testPedestrianWithPsvDesignated() {
        // common in city centers: pedestrian zone where buses are allowed
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "pedestrian");
        way.setTag("psv", "designated");
        assertEquals(1, priority(way), 0.01);
    }

    @Test
    public void testMotorVehicleNoButBusYes() {
        // bus lane on a road otherwise closed to motor vehicles
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "tertiary");
        way.setTag("motor_vehicle", "no");
        way.setTag("bus", "yes");
        assertEquals(1, priority(way), 0.01);
    }

    @Test
    public void testAccessNoButPsvYes() {
        // restricted road with psv exception
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "service");
        way.setTag("access", "no");
        way.setTag("psv", "yes");
        assertEquals(1, priority(way), 0.01);
    }

    @Test
    public void testExplicitBusNo() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "primary");
        way.setTag("bus", "no");
        assertEquals(0, priority(way), 0.01);
    }

    @Test
    public void testMotorVehicleNo() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "residential");
        way.setTag("motor_vehicle", "no");
        assertEquals(0, priority(way), 0.01);
    }

    @Test
    public void testBusTrap() {
        // bus_trap implies motor_vehicle=no, bus=yes -- buses pass, cars don't
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "residential");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "bus_trap"), Map.of()));
        assertEquals(1, priority(way), 0.01);
    }

    @Test
    public void testSumpBuster() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "residential");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "sump_buster"), Map.of()));
        assertEquals(1, priority(way), 0.01);
    }

    @Test
    public void testBollardBlocksBus() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "residential");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "bollard"), Map.of()));
        assertEquals(0, priority(way), 0.01);
    }

    @Test
    public void testBollardWithBusYes() {
        // retractable bollard that lets buses through
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "residential");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "bollard", "bus", "yes"), Map.of()));
        assertEquals(1, priority(way), 0.01);
    }
}
