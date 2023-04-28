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
    private EnumEncodedValue<UrbanDensity> urbanDensity;
    private EnumEncodedValue<Country> countryEnc;
    private EnumEncodedValue<RoadClass> roadClassEnc;
    private BooleanEncodedValue roadClassLinkEnc;
    private DecimalEncodedValue maxSpeedEnc;
    private BooleanEncodedValue roundaboutEnc;

    @BeforeEach
    public void setup() {
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, false);
        roadClassEnc = RoadClass.create();
        roadClassLinkEnc = RoadClassLink.create();
        urbanDensity = UrbanDensity.create();
        countryEnc = Country.create();
        maxSpeedEnc = MaxSpeed.create();
        roundaboutEnc = Roundabout.create();
        EncodingManager em = EncodingManager.start().add(urbanDensity).add(countryEnc).add(roundaboutEnc).
                add(roadClassEnc).add(roadClassLinkEnc).add(maxSpeedEnc).add(accessEnc).add(speedEnc).build();
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
        edge.set(roundaboutEnc, true);
        calc.fillMaxSpeed();
        assertEquals(30, edge.get(maxSpeedEnc), 1);
    }

    @Test
    public void testRawAccess_RuralIsDefault_IfNoUrbanDensityWasSet() {
        Map<String, String> tags = new HashMap<>();
        tags.put("highway", "primary");
        LegalDefaultSpeeds.Result res = defaultSpeeds.getSpeedLimits(Country.DEU.getAlpha2(), tags, new ArrayList<>(), (name, eval) -> eval.invoke());

        assertEquals("100", res.getTags().get("maxspeed").toString());
    }
}
