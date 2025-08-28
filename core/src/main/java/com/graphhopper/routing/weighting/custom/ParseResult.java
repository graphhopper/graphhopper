package com.graphhopper.routing.weighting.custom;

import java.util.Set;

public class ParseResult {
    StringBuilder converted;
    boolean ok;
    String invalidMessage;
    Set<String> guessedVariables;
    Set<String> operators;
}
