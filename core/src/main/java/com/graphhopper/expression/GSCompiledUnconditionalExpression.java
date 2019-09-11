package com.graphhopper.expression;

import com.graphhopper.util.EdgeIteratorState;

public class GSCompiledUnconditionalExpression implements GSCompiledExpression {
    private final String name;
    private final Object value;
    private double doubleValue;

    public GSCompiledUnconditionalExpression(String name, Object value) {
        this.name = name;
        this.value = value;
        try {
            doubleValue = ((Number) value).doubleValue();
        } catch (Exception ex) {
            doubleValue = Double.NaN;
        }
    }

    @Override
    public boolean eval(EdgeIteratorState edge, boolean reverse) {
        return true;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public double getDouble() {
        if (Double.isNaN(doubleValue))
            throw new IllegalStateException("Cannot use value '" + name + "' as double");
        return doubleValue;
    }
}
