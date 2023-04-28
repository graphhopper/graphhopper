package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIteratorState;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        calc = new MaxSpeedCalculator(defaultSpeeds, graph, em);
    }

    @Test
    public void testCityGermany() {
        EdgeIteratorState edge = graph.edge(0, 1);
        edge.set(countryEnc, Country.DEU);
        edge.set(urbanDensity, UrbanDensity.CITY);

        edge.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED);
        edge.set(roadClassEnc, RoadClass.PRIMARY);
        calc.fillMaxSpeed();
        assertEquals(50, edge.get(maxSpeedEnc), 1);

        edge.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED);
        edge.set(roadClassEnc, RoadClass.MOTORWAY);
        calc.fillMaxSpeed();
        assertEquals(MaxSpeed.UNSET_SPEED, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testRuralGermany() {
        EdgeIteratorState edge = graph.edge(0, 1);
        edge.set(countryEnc, Country.DEU);
        edge.set(urbanDensity, UrbanDensity.RURAL);

        edge.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED);
        edge.set(roadClassEnc, RoadClass.PRIMARY);
        calc.fillMaxSpeed();
        assertEquals(100, edge.get(maxSpeedEnc), 1);

        edge.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED);
        edge.set(roadClassEnc, RoadClass.MOTORWAY);
        calc.fillMaxSpeed();
        assertEquals(MaxSpeed.UNSET_SPEED, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testRoundabout() {
        EdgeIteratorState edge = graph.edge(0, 1);
        edge.set(countryEnc, Country.CRI);
        edge.set(urbanDensity, UrbanDensity.CITY);
        edge.set(roadClassEnc, RoadClass.PRIMARY);

        edge.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED);
        calc.fillMaxSpeed();
        assertEquals(50, edge.get(maxSpeedEnc), 1);

        edge.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED);
        edge.set(em.getBooleanEncodedValue(Roundabout.KEY), true);
        calc.fillMaxSpeed();
        assertEquals(30, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testLanes() {
        EdgeIteratorState edge = graph.edge(0, 1);
        edge.set(countryEnc, Country.CHL);
        edge.set(urbanDensity, UrbanDensity.RURAL);
        edge.set(roadClassEnc, RoadClass.PRIMARY);

        edge.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED);
        calc.fillMaxSpeed();
        assertEquals(100, edge.get(maxSpeedEnc), 1);

        edge.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED);
        edge.set(em.getIntEncodedValue(Lanes.KEY), 4); // 2 in each direction!
        calc.fillMaxSpeed();
        assertEquals(120, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testSurface() {
        EdgeIteratorState edge = graph.edge(0, 1);
        edge.set(countryEnc, Country.LTU);
        edge.set(urbanDensity, UrbanDensity.RURAL);
        edge.set(roadClassEnc, RoadClass.PRIMARY);

        edge.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED);
        calc.fillMaxSpeed();
        assertEquals(90, edge.get(maxSpeedEnc), 1);

        edge.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED);
        edge.set(em.getEnumEncodedValue(Surface.KEY, Surface.class), Surface.COMPACTED);
        calc.fillMaxSpeed();
        assertEquals(70, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testRawAccess_RuralIsDefault_IfNoUrbanDensityWasSet() {
        Map<String, String> tags = new HashMap<>();
        tags.put("highway", "primary");
        LegalDefaultSpeeds.Result res = defaultSpeeds.getSpeedLimits(Country.DEU.getAlpha2(), tags, new ArrayList<>(), (name, eval) -> eval.invoke());

        assertEquals("100", res.getTags().get("maxspeed").toString());
    }
}
