package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.routing.ev.MaxSpeed.UNSET_SPEED;
import static com.graphhopper.routing.ev.RoadClass.*;
import static com.graphhopper.routing.ev.UrbanDensity.CITY;
import static com.graphhopper.routing.ev.UrbanDensity.RURAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MaxSpeedCalculatorTest {

    private final LegalDefaultSpeeds defaultSpeeds = MaxSpeedCalculator.createLegalDefaultSpeeds();
    private MaxSpeedCalculator calc;
    private Graph graph;
    private EncodingManager em;
    private EnumEncodedValue<UrbanDensity> urbanDensity;
    private EnumEncodedValue<Country> countryEnc;
    private EnumEncodedValue<RoadClass> roadClassEnc;
    private DecimalEncodedValue maxSpeedEnc;

    @BeforeEach
    public void setup() {
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, false);
        roadClassEnc = RoadClass.create();
        urbanDensity = UrbanDensity.create();
        countryEnc = Country.create();
        maxSpeedEnc = MaxSpeed.create();
        em = EncodingManager.start().add(urbanDensity).add(countryEnc).add(Roundabout.create()).add(Surface.create()).
                add(Lanes.create()).add(roadClassEnc).add(maxSpeedEnc).add(accessEnc).add(speedEnc).build();
        graph = new BaseGraph.Builder(em).create();
        calc = new MaxSpeedCalculator(defaultSpeeds, new RAMDirectory());
    }

    @Test
    public void internalMaxSpeed() {
        DecimalEncodedValue enc = calc.getInternalMaxSpeedEnc();
        enc.setDecimal(false, 0, calc.getInternalMaxSpeedStorage(), UNSET_SPEED);
        assertEquals(UNSET_SPEED, enc.getDecimal(false, 0, calc.getInternalMaxSpeedStorage()));

        enc.setDecimal(false, 1, calc.getInternalMaxSpeedStorage(), 33);
        assertEquals(35, enc.getDecimal(false, 1, calc.getInternalMaxSpeedStorage()));
    }

    EdgeIteratorState createEdge() {
        EdgeIteratorState edge = graph.edge(0, 1);
        edge.set(maxSpeedEnc, UNSET_SPEED, UNSET_SPEED);
        calc.getInternalMaxSpeedEnc().setDecimal(false, edge.getEdge(), calc.getInternalMaxSpeedStorage(), UNSET_SPEED);
        return edge;
    }

    @Test
    public void testCityGermany() {
        EdgeIteratorState edge = createEdge();
        edge.set(countryEnc, Country.DEU);
        edge.set(urbanDensity, CITY);
        edge.set(roadClassEnc, PRIMARY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(50, edge.get(maxSpeedEnc), 1);

        edge = createEdge();
        edge.set(countryEnc, Country.DEU);
        edge.set(urbanDensity, RURAL);
        edge.set(roadClassEnc, MOTORWAY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(UNSET_SPEED, edge.get(maxSpeedEnc), 1);

        edge = createEdge();
        edge.set(countryEnc, Country.DEU);
        edge.set(urbanDensity, CITY);
        edge.set(roadClassEnc, RESIDENTIAL);
        calc.fillMaxSpeed(graph, em);
        assertEquals(50, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testRuralGermany() {
        EdgeIteratorState edge = createEdge();
        edge.set(countryEnc, Country.DEU);
        edge.set(urbanDensity, RURAL);
        edge.set(roadClassEnc, PRIMARY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(100, edge.get(maxSpeedEnc), 1);
        assertEquals(100, edge.getReverse(maxSpeedEnc), 1);

        edge = createEdge();
        edge.set(countryEnc, Country.DEU);
        edge.set(urbanDensity, RURAL);
        edge.set(roadClassEnc, MOTORWAY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(UNSET_SPEED, edge.get(maxSpeedEnc), 1);

        edge = createEdge();
        edge.set(countryEnc, Country.DEU);
        edge.set(urbanDensity, RURAL);
        edge.set(roadClassEnc, RESIDENTIAL);
        calc.fillMaxSpeed(graph, em);
        assertEquals(100, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testRoundabout() {
        EdgeIteratorState edge = createEdge();
        edge.set(countryEnc, Country.CRI);
        edge.set(urbanDensity, CITY);
        edge.set(roadClassEnc, PRIMARY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(50, edge.get(maxSpeedEnc), 1);

        edge = createEdge();
        edge.set(countryEnc, Country.CRI);
        edge.set(urbanDensity, CITY);
        edge.set(em.getBooleanEncodedValue(Roundabout.KEY), true);
        calc.fillMaxSpeed(graph, em);
        assertEquals(30, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testLanes() {
        EdgeIteratorState edge = createEdge();
        edge.set(countryEnc, Country.CHL);
        edge.set(urbanDensity, RURAL);
        edge.set(roadClassEnc, PRIMARY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(100, edge.get(maxSpeedEnc), 1);

        edge = createEdge();
        edge.set(countryEnc, Country.CHL);
        edge.set(roadClassEnc, PRIMARY);
        edge.set(em.getIntEncodedValue(Lanes.KEY), 4); // 2 in each direction!
        calc.fillMaxSpeed(graph, em);
        assertEquals(120, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testSurface() {
        EdgeIteratorState edge = createEdge();
        edge.set(countryEnc, Country.LTU);
        edge.set(urbanDensity, RURAL);
        edge.set(roadClassEnc, PRIMARY);
        calc.fillMaxSpeed(graph, em);
        assertEquals(90, edge.get(maxSpeedEnc), 1);

        edge = createEdge();
        edge.set(countryEnc, Country.LTU);
        edge.set(urbanDensity, RURAL);
        edge.set(roadClassEnc, PRIMARY);
        edge.set(em.getEnumEncodedValue(Surface.KEY, Surface.class), Surface.COMPACTED);
        calc.fillMaxSpeed(graph, em);
        assertEquals(70, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testLivingStreetWithMaxSpeed() {
        EdgeIteratorState edge = createEdge();
        edge.set(countryEnc, Country.DEU);
        edge.set(roadClassEnc, LIVING_STREET);
        edge.set(urbanDensity, CITY);
        edge.set(maxSpeedEnc, 30, 30);
        calc.getInternalMaxSpeedEnc().setDecimal(false, edge.getEdge(), calc.getInternalMaxSpeedStorage(), 5);
        calc.fillMaxSpeed(graph, em);
        assertEquals(30, edge.get(maxSpeedEnc), 1);
        assertEquals(30, edge.getReverse(maxSpeedEnc), 1);
    }

    @Test
    public void testFwdBwd() {
        EdgeIteratorState edge = createEdge();
        edge.set(countryEnc, Country.DEU);
        edge.set(roadClassEnc, PRIMARY);
        edge.set(maxSpeedEnc, 50, 70);
        calc.getInternalMaxSpeedEnc().setDecimal(false, edge.getEdge(), calc.getInternalMaxSpeedStorage(), 100);
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
