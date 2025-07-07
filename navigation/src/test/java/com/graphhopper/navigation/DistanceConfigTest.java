package com.graphhopper.navigation;

import com.graphhopper.config.Profile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DistanceConfigTest {

    @Test
    public void distanceConfigTest() {
        DistanceConfig driving = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, NavigationTransportMode.CAR);
        assertEquals(4, driving.voiceInstructions.size());
        DistanceConfig walking = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, NavigationTransportMode.FOOT);
        assertEquals(1, walking.voiceInstructions.size());
        DistanceConfig cycling = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, NavigationTransportMode.BIKE);
        assertEquals(1, cycling.voiceInstructions.size());


        Profile drivingProfile = new Profile("my_car").putHint("navigation_transport_mode", "driving");
        DistanceConfig drivingFromProfile = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, drivingProfile);
        assertEquals(4, drivingFromProfile.voiceInstructions.size());

        Profile walkingProfile = new Profile("my_feet").putHint("navigation_transport_mode", "walking");
        DistanceConfig walkingFromProfile = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, walkingProfile);
        assertEquals(1, walkingFromProfile.voiceInstructions.size());

        Profile cyclingProfile = new Profile("my_bike").putHint("navigation_transport_mode", "cycling");
        DistanceConfig cyclingFromProfile = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, cyclingProfile);
        assertEquals(1, cyclingFromProfile.voiceInstructions.size());

        Profile truckProfile = new Profile("my_truck"); // no hint set, so defaults to driving
        DistanceConfig truckCfg = new DistanceConfig(DistanceUtils.Unit.METRIC, null, null, truckProfile);
        assertEquals(4, truckCfg.voiceInstructions.size());
    }

}
