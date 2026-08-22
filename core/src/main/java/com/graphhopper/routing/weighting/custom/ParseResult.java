package com.graphhopper.routing.weighting.custom;

import java.util.Map;
import java.util.Set;

public class ParseResult {
    StringBuilder converted;
    boolean ok;
    String invalidMessage;
    Set<String> guessedVariables;
    Set<String> operators;
    // the built-in function calls: method name -> parameters as literals, see ValueExpressionVisitor.toCall
    Map<String, String[]> methods;
}
