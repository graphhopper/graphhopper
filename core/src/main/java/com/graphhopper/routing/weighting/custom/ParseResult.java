package com.graphhopper.routing.weighting.custom;

import java.util.Set;

public class ParseResult {
    StringBuilder converted;
    boolean ok;
    String invalidMessage;
    Set<String> guessedVariables;
    Set<String> operators;
    // the (power, mass) arguments of the single allowed bike_climb_factor call, or null
    String[] bikeClimbArgs;
    // the optional literal scale of the call as code prefix, e.g. "0.9 * ", see ValueExpressionVisitor.isScaledCall
    String bikeClimbScale = "";
}
