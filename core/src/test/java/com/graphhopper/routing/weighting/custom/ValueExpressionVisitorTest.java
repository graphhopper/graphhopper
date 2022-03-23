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
        DecimalEncodedValue prio2 = new DecimalEncodedValueImpl("my_priority2", 5, 1, false);
        EncodedValueLookup lookup = new EncodingManager.Builder().add(prio1).add(prio2).build();

        String msg = assertThrows(IllegalArgumentException.class, () -> findMax("unknown*3", lookup)).getMessage();
        assertTrue(msg.contains("identifier unknown invalid"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findMax("my_priority*my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("only a single EncodedValue"), msg);

        assertEquals(2, findMax("2", lookup), 0.1);
        assertEquals(62, findMax("2*my_priority", lookup), 0.1);

        // TODO NOW should we allow this "unlimited maximum"?
        assertEquals(Double.POSITIVE_INFINITY, findMax("1/my_priority", lookup), 0.1);
    }

    double findMax(String valueExpression, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, lookup::hasEncodedValue);
        if (!result.ok)
            throw new IllegalArgumentException(result.invalidMessage);
        if (result.guessedVariables.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue in the value expression is allowed but was " + valueExpression);

        try {
            ExpressionEvaluator ee = new ExpressionEvaluator();
            for (String var : result.guessedVariables) {
                ee.setParameters(new String[]{var}, new Class[]{double.class});
            }
            ee.cook(valueExpression);
            if (result.guessedVariables.isEmpty())
                return ((Number) ee.evaluate()).doubleValue();

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
            return Math.max(val1.doubleValue(), val2.doubleValue());
        } catch (CompileException | InvocationTargetException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}