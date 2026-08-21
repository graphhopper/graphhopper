package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.routing.ev.AverageSlope;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValueImpl;
import com.graphhopper.routing.util.EncodingManager;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static com.graphhopper.routing.weighting.custom.ValueExpressionVisitor.*;
import static org.junit.jupiter.api.Assertions.*;

class ValueExpressionVisitorTest {

    @Test
    public void protectUsFromStuff() {
        NameValidator allNamesInvalid = s -> false;
        for (String toParse : Arrays.asList("", "new Object()", "java.lang.Object", "Test.class",
                "new Object(){}.toString().length", "{ 5}", "{ 5, 7 }", "Object.class", "System.out.println(\"\")",
                "something.newInstance()", "e.getClass ( )", "edge.getDistance()*7/*test", "edge.getDistance()//*test",
                "edge . getClass()", "(edge = edge) == edge", ") edge (", "in(area_blup(), edge)", "s -> truevalue")) {
            ParseResult res = parse(toParse, allNamesInvalid);
            assertFalse(res.ok, "should not be simple condition: " + toParse);
            assertTrue(res.guessedVariables == null || res.guessedVariables.isEmpty());
        }

        assertFalse(parse("edge; getClass()", allNamesInvalid).ok);
    }

    @Test
    public void isValidAndSimpleCondition() {
        ParseResult result = parse("edge == edge", (arg) -> false);
        assertFalse(result.ok);

        result = parse("Math.sqrt(2)", (arg) -> false);
        assertTrue(result.ok, result.invalidMessage);
        assertTrue(result.guessedVariables.isEmpty());

        result = parse("Math.sqrt(my_speed)", (arg) -> arg.equals("my_speed"));
        assertTrue(result.ok, result.invalidMessage);
        assertEquals("[my_speed]", result.guessedVariables.toString());

        result = parse("edge.getDistance()", (arg) -> false);
        assertFalse(result.ok);

        result = parse("road_class == PRIMARY", (arg) -> false);
        assertFalse(result.ok);

        result = parse("toll == Toll.NO", (arg) -> false);
        assertFalse(result.ok);

        result = parse("priority * 2", (s) -> s.equals("priority"));
        assertTrue(result.ok, result.invalidMessage);
        assertEquals("[priority]", result.guessedVariables.toString());

        // LATER but requires accepting also EnumEncodedValue for value expression
        // result = parse("road_class.ordinal()*2", validVariable);
        // assertTrue(result.ok, result.invalidMessage);
        // assertTrue(parse("Math.sqrt(road_class.ordinal())", validVariable).ok);
    }


    @Test
    public void bikeClimbFunctions() {
        NameValidator validator = s -> s.equals("average_slope");
        ParseResult result = parse("bike_climb_factor(average_slope, 120, 95, 18)", validator);
        assertTrue(result.ok, result.invalidMessage);
        assertEquals("[average_slope]", result.guessedVariables.toString());

        // scaled by a literal the expression stays monotone in the encoded value
        result = parse("0.9 * bike_climb_factor(average_slope, 120, 95, 18)", validator);
        assertTrue(result.ok, result.invalidMessage);
        result = parse("bike_climb_factor(average_slope, 120, 95, 18) * 0.8", validator);
        assertTrue(result.ok, result.invalidMessage);

        // combined with other terms it can be non-monotone and the endpoint-based findMinMax would
        // calculate wrong bounds, e.g. here the real maximum is at average_slope=2 and not at ±31.5
        result = parse("bike_climb_factor(average_slope, 120, 95, 18) + 0.02 * average_slope", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("must be the entire expression"), result.invalidMessage);
        result = parse("average_slope * bike_climb_factor(average_slope, 120, 95, 18)", validator);
        assertFalse(result.ok);
        result = parse("Math.sqrt(bike_climb_factor(average_slope, 120, 95, 18))", validator);
        assertFalse(result.ok);

        result = parse("bike_climb_factor(average_slope, 120, 95)", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("bike_climb_factor expects 4 arguments"), result.invalidMessage);

        // bike_climb_speed is no longer a built-in function
        result = parse("bike_climb_speed(average_slope, 120, 95)", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("illegal method"), result.invalidMessage);

        result = parse("bike_climb_factor(unknown, 120, 95, 18)", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("'unknown' not available"), result.invalidMessage);
    }

    @Test
    public void convertedValueExpression() {
        // the converted expression is used in the generated getSpeed code and injects the current
        // speed as last argument; findMinMax uses the expression as written instead
        NameValidator validator = s -> s.equals("average_slope");
        ParseResult result = parse("bike_climb_factor(average_slope, 120, 95, 18)", validator);
        assertTrue(result.ok, result.invalidMessage);
        assertEquals("bike_climb_factor(average_slope, 120, 95, 18, value)", result.converted.toString());

        result = parse("0.9 * bike_climb_factor(average_slope, 120, 95, 18)", validator);
        assertTrue(result.ok, result.invalidMessage);
        assertEquals("0.9 * bike_climb_factor(average_slope, 120, 95, 18, value)", result.converted.toString());

        // without a built-in function the expression stays unchanged
        result = parse("average_slope * 2.5", validator);
        assertTrue(result.ok, result.invalidMessage);
        assertEquals("average_slope * 2.5", result.converted.toString());
    }

    @Test
    public void bikeClimbMinMax() {
        EncodedValueLookup lookup = new EncodingManager.Builder().add(AverageSlope.create()).build();
        // the function is monotone decreasing in slope so the bounds are taken from the interval
        // limits of average_slope (-31.5 .. 31.5)
        assertInterval(CustomWeightingHelper.bike_climb_factor(31.5, 120, 95, 18), 1.0,
                "bike_climb_factor(average_slope, 120, 95, 18)", lookup);
        assertInterval(0.9 * CustomWeightingHelper.bike_climb_factor(31.5, 120, 95, 18), 0.9,
                "0.9 * bike_climb_factor(average_slope, 120, 95, 18)", lookup);

        assertEquals(Set.of("average_slope"), findVariables("bike_climb_factor(average_slope, 120, 95, 18)", lookup));
    }

    @Test
    public void testErrors() {
        DecimalEncodedValue prio1 = new DecimalEncodedValueImpl("my_priority", 5, 1, false);
        IntEncodedValueImpl prio2 = new IntEncodedValueImpl("my_priority2", 5, -5, false, false);
        EncodedValueLookup lookup = new EncodingManager.Builder().add(prio1).add(prio2).build();

        String msg = assertThrows(IllegalArgumentException.class, () -> findMinMax("unknown*3", lookup)).getMessage();
        assertTrue(msg.contains("'unknown' not available"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findMinMax("my_priority - my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("a single EncodedValue"), msg);
        // unary minus is also a minus operator
        msg = assertThrows(IllegalArgumentException.class, () -> findMinMax("-my_priority + my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("a single EncodedValue"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findMinMax("1/my_priority", lookup)).getMessage();
        assertTrue(msg.contains("invalid operation '/'"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findMinMax("my_priority*my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("Currently only a single EncodedValue is allowed on the right-hand side"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findVariables("unknown*3", lookup)).getMessage();
        assertTrue(msg.contains("'unknown' not available"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findVariables("my_priority - my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("a single EncodedValue"), msg);
        // unary minus is also a minus operator
        msg = assertThrows(IllegalArgumentException.class, () -> findVariables("-my_priority + my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("a single EncodedValue"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findVariables("1/my_priority", lookup)).getMessage();
        assertTrue(msg.contains("invalid operation '/'"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findVariables("my_priority*my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("Currently only a single EncodedValue is allowed on the right-hand side"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findVariables("my_prio*my_priority2 * 3", lookup)).getMessage();
        assertEquals("'my_prio' not available", msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findVariables("-0.5", lookup)).getMessage();
        assertEquals("illegal expression as it can result in a negative weight: -0.5", msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findVariables("-my_priority", lookup)).getMessage();
        assertEquals("illegal expression as it can result in a negative weight: -my_priority", msg);
    }

    @Test
    public void runMaxMin() {
        DecimalEncodedValue prio1 = new DecimalEncodedValueImpl("my_priority", 5, 1, false);
        IntEncodedValueImpl prio2 = new IntEncodedValueImpl("my_priority2", 5, -5, false, false);
        EncodedValueLookup lookup = new EncodingManager.Builder().add(prio1).add(prio2).build();

        assertInterval(2, 2, "2", lookup);

        assertInterval(0, 62, "2*my_priority", lookup);

        assertInterval(-52, 10, "-2*my_priority2", lookup);
    }

    @Test
    public void runVariables() {
        DecimalEncodedValue prio1 = new DecimalEncodedValueImpl("my_priority", 5, 1, false);
        IntEncodedValueImpl prio2 = new IntEncodedValueImpl("my_priority2", 5, -5, false, false);
        EncodedValueLookup lookup = new EncodingManager.Builder().add(prio1).add(prio2).build();

        assertEquals(Set.of(), findVariables("2", lookup));
        assertEquals(Set.of("my_priority"), findVariables("2*my_priority", lookup));

        Exception ex = assertThrows(IllegalArgumentException.class, () ->  findVariables("-2*my_priority", lookup));
        assertTrue(ex.getMessage().contains("illegal expression as it can result in a negative weight"));
    }

    void assertInterval(double min, double max, String expression, EncodedValueLookup lookup) {
        MinMax minmax = findMinMax(expression, lookup);
        assertEquals(min, minmax.min, 0.1, expression);
        assertEquals(max, minmax.max, 0.1, expression);
    }
}
