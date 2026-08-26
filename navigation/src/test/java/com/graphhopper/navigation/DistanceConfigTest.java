package com.graphhopper.navigation;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.TransportationMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                new Profile("walking"),
                new Profile("ebike").putHint("instructions_base_mode", "bike"));
        assertEquals(TransportationMode.CAR, hopper.getInstructionsBaseMode("unknown"));
        assertEquals(TransportationMode.CAR, hopper.getInstructionsBaseMode("my_truck"));
        assertEquals(TransportationMode.FOOT, hopper.getInstructionsBaseMode("foot"));
        assertEquals(TransportationMode.FOOT, hopper.getInstructionsBaseMode("walking"));
        assertEquals(TransportationMode.BIKE, hopper.getInstructionsBaseMode("ebike"));
        assertThrows(IllegalArgumentException.class, () -> new Profile("x").putHint("navigation_mode", "bike"));
        assertThrows(IllegalArgumentException.class, () -> new Profile("x").putHint("instructions_base_mode", "hgv"));

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
    }

}
