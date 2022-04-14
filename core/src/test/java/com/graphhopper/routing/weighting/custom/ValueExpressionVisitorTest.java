package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValueImpl;
import com.graphhopper.routing.util.EncodingManager;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.graphhopper.routing.weighting.custom.ValueExpressionVisitor.findMinMax;
import static com.graphhopper.routing.weighting.custom.ValueExpressionVisitor.parseValueExpression;
import static org.junit.jupiter.api.Assertions.*;

class ValueExpressionVisitorTest {

    @Test
    public void protectUsFromStuff() {
        NameValidator allNamesInvalid = s -> false;
        for (String toParse : Arrays.asList("", "new Object()", "java.lang.Object", "Test.class",
                "new Object(){}.toString().length", "{ 5}", "{ 5, 7 }", "Object.class", "System.out.println(\"\")",
                "something.newInstance()", "e.getClass ( )", "edge.getDistance()*7/*test", "edge.getDistance()//*test",
                "edge . getClass()", "(edge = edge) == edge", ") edge (", "in(area_blup(), edge)", "s -> truevalue")) {
            ParseResult res = parseValueExpression(toParse, allNamesInvalid);
            assertFalse(res.ok, "should not be simple condition: " + toParse);
            assertTrue(res.guessedVariables == null || res.guessedVariables.isEmpty());
        }

        assertFalse(parseValueExpression("edge; getClass()", allNamesInvalid).ok);
    }

    @Test
    public void isValidAndSimpleCondition() {
        NameValidator validVariable = s -> s.equals("edge") || s.equals("Math") || s.equals("priority");
        ParseResult result = parseValueExpression("edge == edge", validVariable);
        assertFalse(result.ok);

        result = parseValueExpression("Math.sqrt(2)", validVariable);
        assertTrue(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        result = parseValueExpression("edge.getDistance()", validVariable);
        assertFalse(result.ok);

        result = parseValueExpression("road_class == PRIMARY", validVariable);
        assertFalse(result.ok);

        result = parseValueExpression("toll == Toll.NO", validVariable);
        assertFalse(result.ok);

        result = parseValueExpression("priority * 2", validVariable);
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
        assertTrue(msg.contains("'unknown' not available"), msg);

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
}