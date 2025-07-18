package com.graphhopper.navigation;

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
        Profile awesomeProfile = new Profile("my_awesome_profile").putHint("navigation_transport_mode", "car");
        DistanceConfig carFromProfile = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, awesomeProfile);
        assertEquals(4, carFromProfile.voiceInstructions.size());

        Profile fastWalkProfile = new Profile("my_fast_walk_profile").putHint("navigation_transport_mode", "foot");
        DistanceConfig footFromProfile = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, fastWalkProfile);
        assertEquals(1, footFromProfile.voiceInstructions.size());

        Profile crazyMtbProfile = new Profile("my_crazy_mtb").putHint("navigation_transport_mode", "bike");
        DistanceConfig bikeFromProfile = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, crazyMtbProfile);
        assertEquals(1, bikeFromProfile.voiceInstructions.size());

        Profile truckProfile = new Profile("my_truck"); // no hint set, so defaults to car
        DistanceConfig truckCfg = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, truckProfile);
        assertEquals(4, truckCfg.voiceInstructions.size());


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

}
