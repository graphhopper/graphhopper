package com.graphhopper.routing.weighting.custom;

import java.util.Set;

public class ParseResult {
    StringBuilder converted;
    boolean ok;
    String invalidMessage;
    Set<String> guessedVariables;
    Set<String> operators;
    // the name and arguments of the built-in function call (see ValueExpressionVisitor.BUILT_INS), or null
    String function;
    String[] functionArgs;
}
