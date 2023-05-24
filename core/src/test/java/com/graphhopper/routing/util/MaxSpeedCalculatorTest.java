package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.*;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.routing.ev.MaxSpeed.UNSET_SPEED;
import static com.graphhopper.routing.ev.UrbanDensity.CITY;
import static com.graphhopper.routing.ev.UrbanDensity.RURAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MaxSpeedCalculatorTest {

    private final LegalDefaultSpeeds defaultSpeeds = MaxSpeedCalculator.createLegalDefaultSpeeds();
    private MaxSpeedCalculator calc;
    private BaseGraph graph;
    private EncodingManager em;
    private EnumEncodedValue<UrbanDensity> urbanDensity;
    private DecimalEncodedValue maxSpeedEnc;
    private OSMParsers parsers;

    @BeforeEach
    public void setup() {
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, false);
        EnumEncodedValue<RoadClass> roadClassEnc = RoadClass.create();
        urbanDensity = UrbanDensity.create();
        EnumEncodedValue<Country> countryEnc = Country.create();
        maxSpeedEnc = MaxSpeed.create();
        EnumEncodedValue<Surface> surfaceEnc = Surface.create();
        IntEncodedValue lanesEnc = Lanes.create();
        BooleanEncodedValue roundaboutEnc = Roundabout.create();
        em = EncodingManager.start().add(urbanDensity).add(countryEnc).add(roundaboutEnc).
                add(surfaceEnc).add(lanesEnc).add(roadClassEnc).add(maxSpeedEnc).add(accessEnc).
                add(speedEnc).build();
        graph = new BaseGraph.Builder(em).create();
        calc = new MaxSpeedCalculator(defaultSpeeds);
        parsers = new OSMParsers();
        parsers.addWayTagParser(new CountryParser(countryEnc));
        parsers.addWayTagParser(new OSMRoundaboutParser(roundaboutEnc));
        parsers.addWayTagParser(new OSMRoadClassParser(roadClassEnc));
        parsers.addWayTagParser(new OSMMaxSpeedParser(maxSpeedEnc));
        parsers.addWayTagParser(new OSMSurfaceParser(surfaceEnc));
        parsers.addWayTagParser(new OSMLanesParser(lanesEnc));
    }

    EdgeIteratorState createEdge(ReaderWay way) {
        EdgeIteratorState edge = graph.edge(0, 1);
        EdgeIntAccess edgeIntAccess = graph.createEdgeIntAccess();
        parsers.handleWayTags(edge.getEdge(), edgeIntAccess, way, em.createRelationFlags());
        return edge;
    }

    @Test
    public void testCityGermany() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("country", Country.DEU);
        way.setTag("highway", "primary");
        EdgeIteratorState edge = createEdge(way).set(urbanDensity, CITY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(50, edge.get(maxSpeedEnc), 1);

        way = new ReaderWay(0L);
        way.setTag("country", Country.DEU);
        way.setTag("highway", "motorway");
        edge = createEdge(way).set(urbanDensity, CITY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(UNSET_SPEED, edge.get(maxSpeedEnc), 1);

        way = new ReaderWay(0L);
        way.setTag("country", Country.DEU);
        way.setTag("highway", "residential");
        edge = createEdge(way).set(urbanDensity, CITY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(50, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testRuralGermany() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("country", Country.DEU);
        way.setTag("highway", "primary");
        EdgeIteratorState edge = createEdge(way).set(urbanDensity, RURAL);
        calc.fillMaxSpeed(graph, em);
        assertEquals(100, edge.get(maxSpeedEnc), 1);
        assertEquals(100, edge.getReverse(maxSpeedEnc), 1);

        way = new ReaderWay(0L);
        way.setTag("country", Country.DEU);
        way.setTag("highway", "motorway");
        edge = createEdge(way).set(urbanDensity, RURAL);
        calc.fillMaxSpeed(graph, em);
        assertEquals(UNSET_SPEED, edge.get(maxSpeedEnc), 1);

        way = new ReaderWay(0L);
        way.setTag("country", Country.DEU);
        way.setTag("highway", "residential");
        edge = createEdge(way).set(urbanDensity, RURAL);
        calc.fillMaxSpeed(graph, em);
        assertEquals(100, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testRoundabout() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("country", Country.CRI);
        way.setTag("highway", "primary");
        EdgeIteratorState edge = createEdge(way).set(urbanDensity, CITY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(50, edge.get(maxSpeedEnc), 1);

        way = new ReaderWay(0L);
        way.setTag("country", Country.CRI);
        way.setTag("highway", "primary");
        way.setTag("junction", "roundabout");
        edge = createEdge(way).set(urbanDensity, CITY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(30, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testLanes() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("country", Country.CHL);
        way.setTag("highway", "primary");
        EdgeIteratorState edge = createEdge(way).set(urbanDensity, RURAL);
        calc.fillMaxSpeed(graph, em);
        assertEquals(100, edge.get(maxSpeedEnc), 1);

        way = new ReaderWay(0L);
        way.setTag("country", Country.CHL);
        way.setTag("highway", "primary");
        way.setTag("lanes", "4"); // 2 in each direction!
        edge = createEdge(way).set(urbanDensity, RURAL);
        calc.fillMaxSpeed(graph, em);
        assertEquals(120, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testSurface() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("country", Country.LTU);
        way.setTag("highway", "primary");
        EdgeIteratorState edge = createEdge(way).set(urbanDensity, RURAL);
        calc.fillMaxSpeed(graph, em);
        assertEquals(90, edge.get(maxSpeedEnc), 1);

        way = new ReaderWay(0L);
        way.setTag("country", Country.LTU);
        way.setTag("highway", "primary");
        way.setTag("surface", "compacted");
        edge = createEdge(way).set(urbanDensity, RURAL);
        calc.fillMaxSpeed(graph, em);
        assertEquals(70, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testLivingStreetWithMaxSpeed() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("country", Country.DEU);
        way.setTag("highway", "living_street");
        way.setTag("maxspeed", "30");
        EdgeIteratorState edge = createEdge(way).set(urbanDensity, CITY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(30, edge.get(maxSpeedEnc), 1);
        assertEquals(30, edge.getReverse(maxSpeedEnc), 1);
    }

    @Test
    public void testFwdBwd() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("country", Country.DEU);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:forward", "50");
        way.setTag("maxspeed:backward", "70");
        EdgeIteratorState edge = createEdge(way);
        calc.fillMaxSpeed(graph, em);
        // internal max speed must be ignored as library currently ignores forward/backward
        assertEquals(50, edge.get(maxSpeedEnc), 1);
        assertEquals(70, edge.getReverse(maxSpeedEnc), 1);
    }

    @Test
    public void testRawAccess_RuralIsDefault_IfNoUrbanDensityWasSet() {
        Map<String, String> tags = new HashMap<>();
        tags.put("highway", "primary");
        LegalDefaultSpeeds.Result res = defaultSpeeds.getSpeedLimits(Country.DEU.getAlpha2(), tags, new ArrayList<>(), (name, eval) -> eval.invoke());

        assertEquals("100", res.getTags().get("maxspeed").toString());
    }

    @Test
    public void testRawAccess_FwdBwdSpeedsAreIgnored() {
        Map<String, String> tags = new HashMap<>();
        tags.put("highway", "primary");
        tags.put("maxspeed:forward", "70");
        tags.put("maxspeed:backward", "30");
        LegalDefaultSpeeds.Result res = defaultSpeeds.getSpeedLimits(Country.DEU.getAlpha2(), tags, new ArrayList<>(), (name, eval) -> eval.invoke());

        assertNull(res.getTags().get("maxspeed:forward"));
        assertEquals("100", res.getTags().get("maxspeed").toString());
    }
}
