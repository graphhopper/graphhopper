package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ExpressionEvaluator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.weighting.custom.ValueExpressionVisitor.parse;
import static org.junit.jupiter.api.Assertions.*;

class ValueExpressionVisitorTest {

    @Test
    public void protectUsFromStuff() {
        NameValidator allNamesInvalid = s -> false;
        for (String toParse : Arrays.asList("", "new Object()", "java.lang.Object", "Test.class", "new Object(){}.toString().length", "{ 5}", "{ 5, 7 }", "Object.class", "System.out.println(\"\")", "something.newInstance()", "e.getClass ( )", "edge.getDistance()*7/*test", "edge.getDistance()//*test", "edge . getClass()", "(edge = edge) == edge", ") edge (", "in(area_blup(), edge)", "s -> truevalue")) {
            ParseResult res = parse(toParse, allNamesInvalid);
            assertFalse(res.ok, "should not be simple condition: " + toParse);
            assertTrue(res.guessedVariables == null || res.guessedVariables.isEmpty());
        }

        assertFalse(parse("edge; getClass()", allNamesInvalid).ok);
    }

    @Test
    public void isValidAndSimpleCondition() {
        NameValidator validVariable = s -> s.equals("edge") || s.equals("Math") || s.equals("priority");
        ParseResult result = parse("edge == edge", validVariable);
        assertFalse(result.ok);

        result = parse("Math.sqrt(2)", validVariable);
        assertTrue(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        result = parse("edge.getDistance()", validVariable);
        assertFalse(result.ok);

        result = parse("road_class == PRIMARY", validVariable);
        assertFalse(result.ok);

        result = parse("toll == Toll.NO", validVariable);
        assertFalse(result.ok);

        result = parse("priority * 2", validVariable);
        assertTrue(result.ok);
        assertEquals("[priority]", result.guessedVariables.toString());

        // LATER
//        assertTrue(parse("road_class.ordinal()*2", validVariable).ok);
//        assertTrue(parse("Math.sqrt(road_class.ordinal())", validVariable).ok);
    }

    @Test
    public void runMaxMin() {
        DecimalEncodedValue prio1 = new DecimalEncodedValueImpl("my_priority", 5, 1, false);
        IntEncodedValueImpl prio2 = new IntEncodedValueImpl("my_priority2", 5, -5, false, false);
        EncodedValueLookup lookup = new EncodingManager.Builder().add(prio1).add(prio2).build();

        String msg = assertThrows(IllegalArgumentException.class, () -> findMinMax("unknown*3", lookup)).getMessage();
        assertTrue(msg.contains("identifier unknown invalid"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findMinMax("my_priority - my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("only a single EncodedValue"), msg);
        // unary minus is also a minus operator
        msg = assertThrows(IllegalArgumentException.class, () -> findMinMax("-my_priority + my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("only a single EncodedValue"), msg);

        assertInterval(0, 2418, "my_priority*my_priority2 * 3", lookup);

        assertInterval(2, 2, "2", lookup);

        assertInterval(0, 62, "2*my_priority", lookup);

        assertInterval(-52, 10, "-2*my_priority2", lookup);

        // for a single expression we allow this "unlimited maximum" for a list we throw an error if this is the overall outcome
        assertInterval(0, Double.POSITIVE_INFINITY, "1/my_priority", lookup);
    }

    void assertInterval(double min, double max, String expression, EncodedValueLookup lookup) {
        double[] minmax = findMinMax(expression, lookup);
        assertEquals(min, minmax[0], 0.1, expression);
        assertEquals(max, minmax[1], 0.1, expression);
    }

    double[] findMinMax(String valueExpression, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, lookup::hasEncodedValue);
        if (!result.ok)
            throw new IllegalArgumentException(result.invalidMessage);
        if ((result.operators.contains("-") || result.operators.contains("/")) && result.guessedVariables.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue in the value expression is allowed when expression contains \"/\" or \"-\". " + valueExpression);

        try {
            ExpressionEvaluator ee = new ExpressionEvaluator();
            List<String> names = new ArrayList<>(result.guessedVariables.size());
            List<Class> values = new ArrayList<>(result.guessedVariables.size());
            for (String var : result.guessedVariables) {
                names.add(var);
                values.add(double.class);
            }
            ee.setParameters(names.toArray(new String[0]), values.toArray(new Class[0]));
            ee.cook(valueExpression);
            if (result.guessedVariables.isEmpty()) { // constant - no EncodedValues
                double val = ((Number) ee.evaluate()).doubleValue();
                return new double[]{val, val};
            }

            List<Object> args = new ArrayList<>();
            for (String var : result.guessedVariables) {
                EncodedValue enc = lookup.getEncodedValue(var, EncodedValue.class);
                if (enc instanceof DecimalEncodedValue)
                    args.add(((DecimalEncodedValue) enc).getMaxDecimal());
                else if (enc instanceof IntEncodedValue)
                    args.add(((IntEncodedValue) enc).getMaxInt());
                else
                    throw new IllegalArgumentException("Cannot use non-number data in value expression");
            }
            Number val1 = (Number) ee.evaluate(args.toArray());

            args.clear();
            for (String var : result.guessedVariables) {
                EncodedValue enc = lookup.getEncodedValue(var, EncodedValue.class);
                if (enc instanceof DecimalEncodedValue)
                    args.add(((DecimalEncodedValue) enc).getMinDecimal());
                else if (enc instanceof IntEncodedValue)
                    args.add(((IntEncodedValue) enc).getMinInt());
                else
                    throw new IllegalArgumentException("Cannot use non-number data in value expression");
            }
            Number val2 = (Number) ee.evaluate(args.toArray());
            return new double[]{Math.min(val1.doubleValue(), val2.doubleValue()), Math.max(val1.doubleValue(), val2.doubleValue())};
        } catch (CompileException | InvocationTargetException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}