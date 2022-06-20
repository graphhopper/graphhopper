package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValueImpl;
import com.graphhopper.routing.util.EncodingManager;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.graphhopper.routing.weighting.custom.ValueExpressionVisitor.findMinMax;
import static com.graphhopper.routing.weighting.custom.ValueExpressionVisitor.parse;
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
        NameValidator validVariable = s -> s.equals("edge") || s.equals("Math") || s.equals("priority");
        ParseResult result = parse("edge == edge", validVariable);
        assertFalse(result.ok);

        result = parse("Math.sqrt(2)", validVariable);
        assertTrue(result.ok, result.invalidMessage);
        assertTrue(result.guessedVariables.isEmpty());

        result = parse("edge.getDistance()", validVariable);
        assertFalse(result.ok);

        result = parse("road_class == PRIMARY", validVariable);
        assertFalse(result.ok);

        result = parse("toll == Toll.NO", validVariable);
        assertFalse(result.ok);

        result = parse("priority * 2", validVariable);
        assertTrue(result.ok, result.invalidMessage);
        assertEquals("[priority]", result.guessedVariables.toString());

        // LATER but requires accepting also EnumEncodedValue for value expression
        // result = parse("road_class.ordinal()*2", validVariable);
        // assertTrue(result.ok, result.invalidMessage);
        // assertTrue(parse("Math.sqrt(road_class.ordinal())", validVariable).ok);
    }


    @Test
    public void testErrors() {
        Set<String> objs = new HashSet<>();
        DecimalEncodedValue prio1 = new DecimalEncodedValueImpl("my_priority", 5, 1, false);
        IntEncodedValueImpl prio2 = new IntEncodedValueImpl("my_priority2", 5, -5, false, false);
        EncodedValueLookup lookup = new EncodingManager.Builder().add(prio1).add(prio2).build();

        String msg = assertThrows(IllegalArgumentException.class, () -> findMinMax(objs, "unknown*3", lookup)).getMessage();
        assertTrue(msg.contains("'unknown' not available"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findMinMax(objs, "my_priority - my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("a single EncodedValue"), msg);
        // unary minus is also a minus operator
        msg = assertThrows(IllegalArgumentException.class, () -> findMinMax(objs, "-my_priority + my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("a single EncodedValue"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findMinMax(objs, "1/my_priority", lookup)).getMessage();
        assertTrue(msg.contains("invalid operation '/'"), msg);

        msg = assertThrows(IllegalArgumentException.class, () -> findMinMax(objs, "my_priority*my_priority2 * 3", lookup)).getMessage();
        assertTrue(msg.contains("Currently only a single EncodedValue is allowed on the right-hand side"), msg);
    }

    @Test
    public void runMaxMin() {
        long x = 6_000_000_000L;
        System.out.println((int)x);

        DecimalEncodedValue prio1 = new DecimalEncodedValueImpl("my_priority", 5, 1, false);
        IntEncodedValueImpl prio2 = new IntEncodedValueImpl("my_priority2", 5, -5, false, false);
        EncodedValueLookup lookup = new EncodingManager.Builder().add(prio1).add(prio2).build();

        assertInterval(2, 2, "2", lookup);

        assertInterval(0, 62, "2*my_priority", lookup);

        assertInterval(-52, 10, "-2*my_priority2", lookup);
    }

    void assertInterval(double min, double max, String expression, EncodedValueLookup lookup) {
        MinMax minmax = findMinMax(new HashSet<>(), expression, lookup);
        assertEquals(min, minmax.min, 0.1, expression);
        assertEquals(max, minmax.max, 0.1, expression);
    }
}