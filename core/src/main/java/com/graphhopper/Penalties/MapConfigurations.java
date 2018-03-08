package com.graphhopper.Penalties;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MapConfigurations {
    public static final int RIGHT = 0, STRAIGHT = 1, LEFT = 2;

    private static final double penaltyForTurnRight = 1;
    private static final double penaltyForTurnLeft = 3;
    private static final double penaltyForTrafficLights = 1.5;

    private final static Map<String, Collection<Double>> penaltiesByMap = new HashMap<>();

    private static final Collection<String> DRIVING_ON_THE_LEFT_SIDE = ImmutableList.of(
            "au",/*Australia*/
            "vg",/*Caribbean Islands*/
            "gb",/*Channel Islands*/
            "cy",/*Cyprus*/
            "jp",/*Japan*/
            "hk",/*Hong Kong*/
            "in",/*India*/
            "im",/*Isle of Man*/
            "ie",/*Ireland*/
            "jm",/*Jamaica*/
            "ke",/*Kenya*/
            "mt",/*Malta*/
            "my",/*Malaysia*/
            "nz",/*New Zealand*/
            "sg",/*Singapore*/
            "th",/*Thailand*/
            "uk",
            "South Africa");


    public static Collection<Double> getTurnPenalties(String map) {
        if (penaltiesByMap.containsKey(map))
            return penaltiesByMap.get(map);

        return DRIVING_ON_THE_LEFT_SIDE.contains(map)
                        ?
                        ImmutableList.of(penaltyForTurnLeft, penaltyForTrafficLights, penaltyForTurnRight)
                        :
                        ImmutableList.of(penaltyForTurnRight, penaltyForTrafficLights, penaltyForTurnLeft);
    }
}
