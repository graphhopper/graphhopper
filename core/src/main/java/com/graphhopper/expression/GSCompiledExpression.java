package com.graphhopper.expression;

import com.graphhopper.util.EdgeIteratorState;

public interface GSCompiledExpression {

    boolean eval(EdgeIteratorState edge, boolean reverse);

    Object getValue();

    double getDouble();
}
