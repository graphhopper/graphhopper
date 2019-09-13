package com.graphhopper.routing.weighting;

import com.graphhopper.expression.GSAssignment;
import com.graphhopper.expression.GSCompiledExpression;
import com.graphhopper.expression.GSExpression;
import com.graphhopper.routing.profiles.EncodedValueFactory;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScriptWeighting extends FastestWeighting {

    private List<GSCompiledExpression> speedExpressions = Collections.emptyList();
    private List<GSCompiledExpression> priorityExpressions = Collections.emptyList();
    // TODO NOW accessExpressions => use default encoder.getAccessEnc() or if empty, see avSpeedEnc
    private final double maxSpeed;

    public ScriptWeighting(FlagEncoder encoder, EncodedValueFactory factory, List<GSAssignment> script) {
        super(encoder);
        for (GSAssignment assignment : script) {
            if ("speed".equals(assignment.getName()))
                this.speedExpressions = compile(assignment.getExpressions(), factory);
            else if ("priority".equals(assignment.getName()))
                this.priorityExpressions = compile(assignment.getExpressions(), factory);
        }

        // how to grab maximum value from speedExpressions?
        maxSpeed = encoder.getMaxSpeed();
    }

    List<GSCompiledExpression> compile(List<GSExpression> expressions, EncodedValueFactory factory) {
        EncodedValueLookup encodedValueLookup = getFlagEncoder();
        List<GSCompiledExpression> compiledExpressions = new ArrayList<>(expressions.size());
        for (GSExpression expression : expressions) {
            compiledExpressions.add(expression.compile(encodedValueLookup, factory));
        }
        return compiledExpressions;
    }

    public double getMinWeight(double distance) {
        return distance / maxSpeed;
    }

    @Override
    public long calcMillis(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        // duplicate code in calcWeight
        if (!isAccessible(edge, reverse))
            return Long.MAX_VALUE;
        double speed = getSpeedInKmh(edge, reverse);
        return Math.round(edge.getDistance() / speed * 3600 + getDelayInSec(edge, reverse)) * 1000;
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        if (!isAccessible(edge, reverse))
            return Double.POSITIVE_INFINITY;
        double speed = getSpeedInKmh(edge, reverse);
        double timeInMillis = edge.getDistance() / speed * 3600 + getDelayInSec(edge, reverse) * 1000;
        double priority = getPriorityValue(edge, reverse);
        return timeInMillis * priority;
    }

    private boolean isAccessible(EdgeIteratorState edge, boolean reverse) {
        // if (accessExpressions.isEmpty())
        return reverse ? edge.getReverse(accessEnc) : edge.get(accessEnc);
    }

    private double getDelayInSec(EdgeIteratorState edge, boolean reverse) {
        return 0;
    }

    private double getSpeedInKmh(EdgeIteratorState edge, boolean reverse) {
        if (speedExpressions.isEmpty())
            return reverse ? edge.getReverse(avSpeedEnc) : edge.get(avSpeedEnc);

        // TODO NOW instead of List - we should better use a chained list of expressions combined with an "OR"?
        for (GSCompiledExpression expression : speedExpressions) {
            if (expression.eval(edge, reverse))
                return expression.getDouble();
        }
        // TODO NOW how to move this into the parser
        throw new IllegalArgumentException("Script does not contain default value for 'speed'");
    }

    private double getPriorityValue(EdgeIteratorState edge, boolean reverse) {
        if (priorityExpressions.isEmpty())
            // TODO NOW return the EncodedValue for BikeFlagEncoder
            return 1;

        for (GSCompiledExpression expression : priorityExpressions) {
            if (expression.eval(edge, reverse))
                return expression.getDouble();
        }
        // TODO NOW how to move this into the parser
        throw new IllegalArgumentException("Script does not contain default value for 'priority'");
    }

    @Override
    public String getName() {
        return "script";
    }
}
