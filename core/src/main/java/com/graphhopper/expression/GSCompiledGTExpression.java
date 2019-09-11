package com.graphhopper.expression;

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

public class GSCompiledGTExpression implements GSCompiledExpression {
    private final String name;
    private final double value;
    private final double parameter;
    private final DecimalEncodedValue encodedValue;

    public GSCompiledGTExpression(String name, DecimalEncodedValue encodedValue, double parameter, double value) {
        this.name = name;
        this.encodedValue = encodedValue;
        this.parameter = parameter;
        this.value = value;
    }

    @Override
    public boolean eval(EdgeIteratorState edge, boolean reverse) {
        return reverse ? edge.getReverse(encodedValue) > parameter : edge.get(encodedValue) > parameter;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public double getDouble() {
        return value;
    }

    @Override
    public String toString() {
        return name + ": " + value;
    }
}
