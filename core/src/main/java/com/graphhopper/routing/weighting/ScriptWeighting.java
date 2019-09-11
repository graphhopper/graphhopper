package com.graphhopper.routing.weighting;

import com.graphhopper.expression.GSAssignment;
import com.graphhopper.expression.GSCompiledExpression;
import com.graphhopper.expression.GSExpression;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScriptWeighting extends FastestWeighting {

    private List<GSCompiledExpression> speedExpressions = Collections.emptyList();
    private List<GSCompiledExpression> priorityExpressions = Collections.emptyList();

    public ScriptWeighting(FlagEncoder encoder, List<GSAssignment> script) {
        super(encoder);
        for (GSAssignment assignment : script) {
            if ("speed".equals(assignment.getName()))
                this.speedExpressions = compile(assignment.getExpressions());
            else if ("priority".equals(assignment.getName()))
                this.priorityExpressions = compile(assignment.getExpressions());
        }
    }

    List<GSCompiledExpression> compile(List<GSExpression> expressions) {
        EncodedValueLookup encodedValueLookup = getFlagEncoder();
        List<GSCompiledExpression> compiledExpressions = new ArrayList<>(expressions.size());
        for (GSExpression expression : expressions) {
            compiledExpressions.add(expression.compile(encodedValueLookup));
        }
        return compiledExpressions;
    }

    @Override
    public double getMinWeight(double distance) {
        // TODO NOW
        return super.getMinWeight(distance);
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        // TODO NOW
        return super.calcMillis(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        // TODO NOW how can we use cached values?
        // double speed = reverse ? edge.getReverse(avSpeedEnc) : edge.get(avSpeedEnc);

        double speed = getSpeedValue(edge, reverse);
        double time = edge.getDistance() / speed * SPEED_CONV /* + time_delay_in_sec */;
        double priority = getPriorityValue(edge, reverse);
        return time * priority;
    }

    private double getSpeedValue(EdgeIteratorState edge, boolean reverse) {
        if (speedExpressions.isEmpty())
            return 1;

        for (GSCompiledExpression expression : speedExpressions) {
            if (expression.eval(edge, reverse))
                return expression.getDouble();
        }
        // TODO NOW move this into the parser
        throw new IllegalArgumentException("Script does not contain default value for 'speed'");
    }

    private double getPriorityValue(EdgeIteratorState edge, boolean reverse) {
        if (priorityExpressions.isEmpty())
            return 1;

        for (GSCompiledExpression expression : priorityExpressions) {
            if (expression.eval(edge, reverse))
                return expression.getDouble();
        }
        // TODO NOW move this into the parser
        throw new IllegalArgumentException("Script does not contain default value for 'priority'");
    }
}
