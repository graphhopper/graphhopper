package com.graphhopper.navigation;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.TransportationMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DistanceConfigTest {

    @Test
    public void distanceConfigTest() {
        // from TransportationMode
        DistanceConfig car = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, TransportationMode.CAR);
        assertEquals(4, car.voiceInstructions.size());
        DistanceConfig foot = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, TransportationMode.FOOT);
        assertEquals(1, foot.voiceInstructions.size());
        DistanceConfig bike = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, TransportationMode.BIKE);
        assertEquals(1, bike.voiceInstructions.size());
        DistanceConfig bus = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, TransportationMode.BUS);
        assertEquals(4, bus.voiceInstructions.size());

        // from Profile
        GraphHopper hopper = new GraphHopper().setProfiles(
                new Profile("my_truck"),
                new Profile("foot"),
                new Profile("ebike").putHint("navigation_mode", "bike"));
        assertEquals(TransportationMode.CAR, hopper.getNavigationMode("unknown"));
        assertEquals(TransportationMode.CAR, hopper.getNavigationMode("my_truck"));
        assertEquals(TransportationMode.FOOT, hopper.getNavigationMode("foot"));
        assertEquals(TransportationMode.BIKE, hopper.getNavigationMode("ebike"));

        // from String
        DistanceConfig driving = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "driving");
        assertEquals(4, driving.voiceInstructions.size());
        DistanceConfig anything = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "anything");
        assertEquals(4, anything.voiceInstructions.size());
        DistanceConfig none = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "");
        assertEquals(4, none.voiceInstructions.size());
        DistanceConfig biking = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "biking");
        assertEquals(1, biking.voiceInstructions.size());
    }

    @Test
    public void testModeMapping() {
        // Test TransportationMode enum values
        DistanceConfig car = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, TransportationMode.CAR);
        assertEquals("driving", car.getMode());

        DistanceConfig foot = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, TransportationMode.FOOT);
        assertEquals("walking", foot.getMode());

        DistanceConfig bike = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, TransportationMode.BIKE);
        assertEquals("cycling", bike.getMode());

        DistanceConfig bus = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, TransportationMode.BUS);
        assertEquals("driving", bus.getMode());

        DistanceConfig hgv = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, TransportationMode.HGV);
        assertEquals("driving", hgv.getMode());

        // cycling
        assertEquals("cycling", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "biking").getMode());
        assertEquals("cycling", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "cycling").getMode());
        assertEquals("cycling", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "cyclist").getMode());
        assertEquals("cycling", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "mtb").getMode());
        assertEquals("cycling", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "racingbike").getMode());
        assertEquals("cycling", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "bike").getMode());

        // walking
        assertEquals("walking", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "walking").getMode());
        assertEquals("walking", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "walk").getMode());
        assertEquals("walking", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "hiking").getMode());
        assertEquals("walking", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "hike").getMode());
        assertEquals("walking", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "foot").getMode());
        assertEquals("walking", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "pedestrian").getMode());

        // unknown modes
        assertEquals("driving", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "driving").getMode());
        assertEquals("driving", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "unknown").getMode());
        assertEquals("driving", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "").getMode());
        assertEquals("driving", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "car").getMode());
        assertEquals("driving", new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, "truck").getMode());
    }

}
