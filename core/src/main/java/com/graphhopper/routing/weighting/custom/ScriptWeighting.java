package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.EdgeIteratorState;
import org.codehaus.janino.*;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ScriptWeighting extends AbstractWeighting {
    public static final String NAME = "script";

    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
    private final static double SPEED_CONV = 3.6;
    private final BooleanEncodedValue baseVehicleAccessEnc;
    private final double maxSpeed;
    private final double distanceInfluence;
    private final double headingPenaltySeconds;
    private final ScriptHelper scriptHelper;

    public ScriptWeighting(FlagEncoder baseFlagEncoder, EncodedValueLookup lookup,
                           TurnCostProvider turnCostProvider, CustomModel customModel) {
        super(baseFlagEncoder, turnCostProvider);
        if (customModel == null)
            throw new IllegalStateException("CustomModel cannot be null");

        headingPenaltySeconds = customModel.getHeadingPenalty();
        baseVehicleAccessEnc = baseFlagEncoder.getAccessEnc();

        scriptHelper = ScriptHelper.create(customModel, lookup, baseFlagEncoder.getMaxSpeed(), baseFlagEncoder.getAverageSpeedEnc());
        maxSpeed = baseFlagEncoder.getMaxSpeed() / SPEED_CONV;

        // given unit is s/km -> convert to s/m
        distanceInfluence = customModel.getDistanceInfluence() / 1000;
        if (distanceInfluence < 0)
            throw new IllegalArgumentException("maximum distance_influence cannot be negative " + distanceInfluence);
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed + distance * distanceInfluence;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        final double distance = edgeState.getDistance();
        double seconds = calcSeconds(distance, edgeState, reverse);
        if (Double.isInfinite(seconds))
            return Double.POSITIVE_INFINITY;
        double distanceCosts = distance * distanceInfluence;
        if (Double.isInfinite(distanceCosts))
            return Double.POSITIVE_INFINITY;
        return seconds / scriptHelper.getPriority(edgeState, reverse) + distanceCosts;
    }

    double calcSeconds(double distance, EdgeIteratorState edgeState, boolean reverse) {
        // special case for loop edges: since they do not have a meaningful direction we always need to read them in forward direction
        if (edgeState.getBaseNode() == edgeState.getAdjNode())
            reverse = false;

        // TODO see #1835
        if (reverse ? !edgeState.getReverse(baseVehicleAccessEnc) : !edgeState.get(baseVehicleAccessEnc))
            return Double.POSITIVE_INFINITY;

        double speed = scriptHelper.getSpeed(edgeState, reverse);
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative");

        double seconds = distance / speed * SPEED_CONV;
        // add penalty at start/stop/via points
        return edgeState.get(EdgeIteratorState.UNFAVORED_EDGE) ? seconds + headingPenaltySeconds : seconds;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return Math.round(calcSeconds(edgeState.getDistance(), edgeState, reverse) * 1000);
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * additionally to SecurityManager let's enforce a simple expressions.
     * From FUNDAMENTALS-5: SecurityManager checks should be considered a last resort.
     *
     * @param returnSet collects guess parameters
     * @return true if valid and "simple" expression
     */
    public static boolean parseAndGuessParametersFromCondition(Set<String> returnSet, String key, NameValidator validator) {
        if (key.length() > 100)
            return false;
        try {
            Parser parser = new Parser(new Scanner("ignore", new StringReader(key)));
            Java.Atom atom = parser.parseConditionalExpression();
            // after parsing the expression the input should end (otherwise it is not "simple")
            if (parser.peek().type == TokenType.END_OF_INPUT)
                return atom.accept(new MyConditionVisitor(returnSet, validator));
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private static class MyConditionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {
        private final Set<String> parameters;
        private final NameValidator nameValidator;
        private final Set<String> allowedMethods = new HashSet<>(Arrays.asList("ordinal",
                "getDistance", "getName", "contains",
                "sqrt", "abs"));

        public MyConditionVisitor(Set<String> parameters, NameValidator nameValidator) {
            this.parameters = parameters;
            this.nameValidator = nameValidator;
        }

        @Override
        public Boolean visitPackage(Java.Package p) {
            return false;
        }

        @Override
        public Boolean visitRvalue(Java.Rvalue rv) throws Exception {
            if (rv instanceof Java.AmbiguousName) {
                Java.AmbiguousName n = (Java.AmbiguousName) rv;
                for (String identifier : n.identifiers) {
                    // allow only certain methods and other identifiers (constants and like encoded values)
                    if (nameValidator.isValid(identifier) || allowedMethods.contains(identifier)) {
                        if (!Character.isUpperCase(identifier.charAt(0)))
                            parameters.add(n.identifiers[0]);
                        return true;
                    }
                }
                return false;
            }
            if (rv instanceof Java.Literal)
                return true;
            if (rv instanceof Java.MethodInvocation) {
                Java.MethodInvocation mi = (Java.MethodInvocation) rv;
                if (allowedMethods.contains(mi.methodName)) return mi.target.accept(this);
                return false;
            }
            if (rv instanceof Java.BinaryOperation)
                if (((Java.BinaryOperation) rv).lhs.accept(this))
                    return ((Java.BinaryOperation) rv).rhs.accept(this);
            return false;
        }

        @Override
        public Boolean visitType(Java.Type t) {
            return false;
        }

        @Override
        public Boolean visitConstructorInvocation(Java.ConstructorInvocation ci) {
            return false;
        }
    }

    private static class MyExpressionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {
        private final Set<String> parameters;
        private final NameValidator nameValidator;

        public MyExpressionVisitor(Set<String> parameters, NameValidator nameValidator) {
            this.parameters = parameters;
            this.nameValidator = nameValidator;
        }

        @Override
        public Boolean visitPackage(Java.Package p) {
            return false;
        }

        @Override
        public Boolean visitRvalue(Java.Rvalue rv) {
            if (rv instanceof Java.AmbiguousName) {
                Java.AmbiguousName n = (Java.AmbiguousName) rv;
                for (String identifier : n.identifiers) {
                    // allow only valid identifiers, encoded values, explicitly allowed variables and methods
                    if (nameValidator.isValid(identifier)) {
                        if (!Character.isUpperCase(identifier.charAt(0)))
                            parameters.add(n.identifiers[0]);
                        return true;
                    }
                }
                return false;
            }
            return rv instanceof Java.IntegerLiteral || rv instanceof Java.FloatingPointLiteral;
        }

        @Override
        public Boolean visitType(Java.Type t) {
            return false;
        }

        @Override
        public Boolean visitConstructorInvocation(Java.ConstructorInvocation ci) {
            return false;
        }
    }

    interface NameValidator {
        boolean isValid(String name);
    }
}