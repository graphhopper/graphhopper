package com.graphhopper.routing.weighting.custom;

import java.util.Set;

public class ParseResult {
    String converted;
    boolean ok;
    String invalidMessage;
    Set<String> guessedVariables;
    Set<String> operators;
    // the expression calls bike_speed_factor, which gets the running speed and may combine several parameters
    boolean bikeSpeedFactor;
}
