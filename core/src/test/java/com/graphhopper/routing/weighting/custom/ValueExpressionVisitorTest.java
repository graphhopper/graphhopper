package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.routing.ev.AverageSlope;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValueImpl;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.CustomModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static com.graphhopper.routing.weighting.custom.ValueExpressionVisitor.*;
import static org.junit.jupiter.api.Assertions.*;

class ValueExpressionVisitorTest {

    static ParseResult parseValue(String valueExpression, EncodedValueLookup lookup) {
        return ValueExpressionVisitor.parseValue(valueExpression, Map.of(), lookup);
    }

    static MinMax findMinMax(String valueExpression, EncodedValueLookup lookup) {
        return ValueExpressionVisitor.findMinMax(valueExpression, Map.of(), lookup);
    }

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

        result = parse("Math.min(my_speed, 10)", (arg) -> arg.equals("my_speed"));
        assertTrue(result.ok, result.invalidMessage);
        assertEquals("[my_speed]", result.guessedVariables.toString());
        result = parse("1 - 0.1 * Math.max(0.5, my_speed)", (arg) -> arg.equals("my_speed"));
        assertTrue(result.ok, result.invalidMessage);
        assertFalse(parse("Math.pow(my_speed, 2)", (arg) -> arg.equals("my_speed")).ok);

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


    static final Map<String, CustomModel.Parameter> BIKE_PARAMS = Map.of("power", new CustomModel.Parameter(120),
            "mass", new CustomModel.Parameter(95), "cda", new CustomModel.Parameter(0.6), "crr", new CustomModel.Parameter(0.006));
    static final String BIKE_CALL = "bike_climb_factor(average_slope, p_power, p_mass, p_cda, p_crr)";

    @Test
    public void bikeClimbFunctions() {
        NameValidator validator = s -> s.equals("average_slope") || s.startsWith("p_");
        ParseResult result = parse(BIKE_CALL, validator);
        assertTrue(result.ok, result.invalidMessage);
        // the slope encoded value and the parameters are variables
        assertEquals("[average_slope, p_power, p_mass, p_cda, p_crr]", result.guessedVariables.toString());

        // combined with other terms it can be non-monotone and the endpoint-based findMinMax would
        // calculate wrong bounds, e.g. here the real maximum is at average_slope=2 and not at ±31.5
        result = parse(BIKE_CALL + " + 0.02 * average_slope", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("must be the entire expression"), result.invalidMessage);
        result = parse("average_slope * " + BIKE_CALL, validator);
        assertFalse(result.ok);
        result = parse("Math.sqrt(" + BIKE_CALL + ")", validator);
        assertFalse(result.ok);
        // use a separate statement instead of a scale
        result = parse("0.9 * " + BIKE_CALL, validator);
        assertFalse(result.ok);

        // the slope is an explicit argument
        result = parse("bike_climb_factor(p_power, p_mass, p_cda, p_crr)", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("bike_climb_factor expects 5 arguments"), result.invalidMessage);
        result = parse("bike_climb_factor(average_slope, p_power, p_mass)", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("bike_climb_factor expects 5 arguments"), result.invalidMessage);

        // bike_climb_speed is no longer a built-in function
        result = parse("bike_climb_speed(average_slope, 120, 95)", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("illegal method"), result.invalidMessage);

        // average_slope must be available
        result = parse(BIKE_CALL, s -> s.startsWith("p_"));
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("like average_slope as argument 1, but 'average_slope' is not available"), result.invalidMessage);

        // the slope must be an encoded value, the other arguments numbers or parameters as the table is created from them
        result = parse("bike_climb_factor(12, p_power, p_mass, p_cda, p_crr)", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("expects an argument like average_slope as argument 1"), result.invalidMessage);
        result = parse("bike_climb_factor(average_slope, 120, p_mass, 0.6, p_crr)", validator);
        assertTrue(result.ok, result.invalidMessage);
        assertArrayEquals(new String[]{"average_slope", "120", "p_mass", "0.6", "p_crr"}, result.functionArgs);
        result = parse("bike_climb_factor(average_slope, p_power, 2 * p_mass, p_cda, p_crr)", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("expects an argument like p_mass as argument 3"), result.invalidMessage);
        result = parse("bike_climb_factor(average_slope, p_power, p_mass, p_cda, unknown)", validator);
        assertFalse(result.ok);
        assertTrue(result.invalidMessage.contains("expects an argument like p_crr as argument 5, but 'unknown' is not available"), result.invalidMessage);
    }

    @Test
    public void parsedBikeClimbArgs() {
        // the parsed arguments are kept, from which the generated getSpeed code and the expression
        // for the evaluator are derived
        NameValidator validator = s -> s.equals("average_slope") || s.startsWith("p_");
        ParseResult result = parse(BIKE_CALL, validator);
        assertTrue(result.ok, result.invalidMessage);
        assertArrayEquals(new String[]{"average_slope", "p_power", "p_mass", "p_cda", "p_crr"}, result.functionArgs);

        // independent of whitespace
        result = parse("bike_climb_factor( average_slope,   p_power  ,p_mass, p_cda ,p_crr )", validator);
        assertTrue(result.ok, result.invalidMessage);
        assertArrayEquals(new String[]{"average_slope", "p_power", "p_mass", "p_cda", "p_crr"}, result.functionArgs);

        // without the built-in function nothing is recorded
        result = parse("average_slope * 2.5", validator);
        assertTrue(result.ok, result.invalidMessage);
        assertNull(result.functionArgs);
    }

    @Test
    public void bikeClimbMinMax() {
        EncodedValueLookup lookup = new EncodingManager.Builder().add(AverageSlope.create()).build();
        // the function is monotone decreasing in slope so the bounds are taken from the interval
        // limits of average_slope (-31.5 .. 31.5)
        double minFactor = CustomWeightingHelper.bike_climb_factor(31.5, 120, 95, 0.6, 0.006);
        double maxFactor = CustomWeightingHelper.bike_climb_factor(-31.5, 120, 95, 0.6, 0.006);
        assertEquals(4.58, maxFactor, 0.01);
        assertInterval(minFactor, maxFactor, ValueExpressionVisitor.findMinMax(BIKE_CALL, BIKE_PARAMS, lookup));

        // average_slope and the parameters are variables so that the generated class provides them
        assertEquals(Set.of("average_slope", "p_power", "p_mass", "p_cda", "p_crr"),
                ValueExpressionVisitor.parseValue(BIKE_CALL, BIKE_PARAMS, lookup).guessedVariables);

        // the first argument must be an encoded value, the others numbers or defined parameters
        String msg = assertThrows(IllegalArgumentException.class, () -> ValueExpressionVisitor.parseValue(
                "bike_climb_factor(average_slope, p_power, p_mass, p_cda, average_slope)", BIKE_PARAMS, lookup)).getMessage();
        assertTrue(msg.contains("'average_slope' is not defined in 'parameters'"), msg);
        msg = assertThrows(IllegalArgumentException.class, () -> ValueExpressionVisitor.parseValue(
                "bike_climb_factor(p_power, p_power, p_mass, p_cda, p_crr)", BIKE_PARAMS, lookup)).getMessage();
        assertTrue(msg.contains("'p_power' is not an encoded value"), msg);
        msg = assertThrows(IllegalArgumentException.class, () -> ValueExpressionVisitor.findMinMax(
                BIKE_CALL, Map.of("power", new CustomModel.Parameter(120)), lookup)).getMessage();
        assertTrue(msg.contains("p_mass as argument 3, but 'p_mass' is not available"), msg);
        // an invalid parameter value is rejected when the bounds are calculated, e.g. at the range endpoints on startup
        Map<String, CustomModel.Parameter> zeroPower = new java.util.HashMap<>(BIKE_PARAMS);
        zeroPower.put("power", new CustomModel.Parameter(0));
        assertThrows(IllegalArgumentException.class, () -> ValueExpressionVisitor.findMinMax(BIKE_CALL, zeroPower, lookup));
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

        msg = assertThrows(IllegalArgumentException.class, () -> parseValue("unknown*3", lookup)).getMessage();
        assertTrue(msg.contains("'unknown' not available"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> parseValue("my_priority - my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("a single EncodedValue"), msg);
        // unary minus is also a minus operator
        msg = assertThrows(IllegalArgumentException.class, () -> parseValue("-my_priority + my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("a single EncodedValue"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> parseValue("1/my_priority", lookup)).getMessage();
        assertTrue(msg.contains("invalid operation '/'"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> parseValue("my_priority*my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("Currently only a single EncodedValue is allowed on the right-hand side"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> parseValue("my_prio*my_priority2 * 3", lookup)).getMessage();
        assertEquals("'my_prio' not available", msg);

        msg = assertThrows(IllegalArgumentException.class, () -> parseValue("-0.5", lookup)).getMessage();
        assertEquals("illegal expression as it can result in a negative weight: -0.5", msg);

        msg = assertThrows(IllegalArgumentException.class, () -> parseValue("-my_priority", lookup)).getMessage();
        assertEquals("illegal expression as it can result in a negative weight: -my_priority", msg);
    }

    @Test
    public void runMaxMin() {
        DecimalEncodedValue prio1 = new DecimalEncodedValueImpl("my_priority", 5, 1, false);
        IntEncodedValueImpl prio2 = new IntEncodedValueImpl("my_priority2", 5, -5, false, false);
        EncodedValueLookup lookup = new EncodingManager.Builder().add(prio1).add(prio2).build();

        assertInterval(2, 2, "2", lookup);

        assertInterval(0, 62, "2*my_priority", lookup);
        // Math.min and Math.max are monotone, so the bounds from the range endpoints are exact
        assertInterval(0, 10, "Math.min(my_priority, 10)", lookup);
        assertInterval(10, 31, "Math.max(10, my_priority)", lookup);
        assertInterval(0.7, 1, "1 - 0.03 * Math.min(my_priority, 10)", lookup);

        assertInterval(-52, 10, "-2*my_priority2", lookup);
    }

    @Test
    public void runVariables() {
        DecimalEncodedValue prio1 = new DecimalEncodedValueImpl("my_priority", 5, 1, false);
        IntEncodedValueImpl prio2 = new IntEncodedValueImpl("my_priority2", 5, -5, false, false);
        EncodedValueLookup lookup = new EncodingManager.Builder().add(prio1).add(prio2).build();

        assertEquals(Set.of(), parseValue("2", lookup).guessedVariables);
        assertEquals(Set.of("my_priority"), parseValue("2*my_priority", lookup).guessedVariables);

        Exception ex = assertThrows(IllegalArgumentException.class, () ->  parseValue("-2*my_priority", lookup));
        assertTrue(ex.getMessage().contains("illegal expression as it can result in a negative weight"));
    }

    void assertInterval(double min, double max, String expression, EncodedValueLookup lookup) {
        assertInterval(min, max, findMinMax(expression, lookup));
    }

    void assertInterval(double min, double max, MinMax minmax) {
        assertEquals(min, minmax.min, 0.1);
        assertEquals(max, minmax.max, 0.1);
    }
}
