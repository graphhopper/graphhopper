package com.graphhopper.routing.weighting.custom;

import java.util.Set;

public class ParseResult {
    StringBuilder converted;
    boolean ok;
    String invalidMessage;
    Set<String> guessedVariables;
    Set<String> operators;
    // bike_climb_table(...) with the literal arguments of bike_climb_factor, see ValueExpressionVisitor
    String bikeClimbTable;
    // the expression for findMinMax and findVariables, identical to the expression except for bike_climb_factor
    String evaluable;
}
