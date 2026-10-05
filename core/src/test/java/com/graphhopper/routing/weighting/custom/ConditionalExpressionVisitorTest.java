package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.graphhopper.routing.weighting.custom.ConditionalExpressionVisitor.parse;
import static org.junit.jupiter.api.Assertions.*;

public class ConditionalExpressionVisitorTest {

    @BeforeEach
    public void before() {
        StringEncodedValue sev = new StringEncodedValue("country", 10);
        new EncodingManager.Builder().add(sev).build();
        sev.setString(false, 0, new ArrayEdgeIntAccess(1), "DEU");
    }

    @Test
    public void protectUsFromStuff() {
        NameValidator allNamesInvalid = s -> false;
        for (String toParse : Arrays.asList(
                "",
                "new Object()",
                "java.lang.Object",
                "Test.class",
                "new Object(){}.toString().length",
                "{ 5}",
                "{ 5, 7 }",
                "Object.class",
                "System.out.println(\"\")",
                "something.newInstance()",
                "e.getClass ( )",
                "edge.getDistance()*7/*test",
                "edge.getDistance()//*test",
                "edge . getClass()",
                "(edge = edge) == edge",
                ") edge (",
                "in(area_blup(), edge)",
                "s -> truevalue")) {
            ParseResult res = parse(toParse, allNamesInvalid, k -> "");
            assertFalse(res.ok, "should not be simple condition: " + toParse);
            assertTrue(res.guessedVariables == null || res.guessedVariables.isEmpty());
        }

        assertFalse(parse("edge; getClass()", allNamesInvalid, k -> "").ok);
    }

    @Test
    public void testConvertExpression() {
        NameValidator validVariable = s -> Helper.toUpperCase(s).equals(s) || s.equals("road_class") || s.equals("toll");

        ParseResult result = parse("toll == NO", validVariable, k -> "");
        assertTrue(result.ok);
        assertEquals("[toll]", result.guessedVariables.toString());

        assertEquals("road_class == Hello.PRIMARY",
                parse("road_class == PRIMARY", validVariable, k -> "Hello").converted);
        assertEquals("toll == Toll.NO", parse("toll == NO", validVariable, k -> "Toll").converted);
        assertEquals("toll == Toll.NO || road_class == RoadClass.NO", parse("toll == NO || road_class == NO", validVariable, k -> k.equals("toll") ? "Toll" : "RoadClass").converted);

        // convert in_area variable to function call:
        assertEquals(CustomWeightingHelper.class.getSimpleName() + ".in(this.in_custom_1, edge)",
                parse("in_custom_1", validVariable, k -> "").converted);

        // no need to inject:
        assertNull(parse("toll == Toll.NO", validVariable, k -> "").converted);
    }

    @Test
    public void isValidAndSimpleCondition() {
        NameValidator validVariable = s -> Helper.toUpperCase(s).equals(s)
                || s.equals("road_class") || s.equals("toll") || s.equals("my_speed") || s.equals("backward_my_speed");

        ParseResult result = parse("in_something", validVariable, k -> "");
        assertTrue(result.ok);
        assertEquals("[in_something]", result.guessedVariables.toString());

        result = parse("edge == edge", validVariable, k -> "");
        assertFalse(result.ok);

        result = parse("Math.sqrt(my_speed)", validVariable, k -> "");
        assertTrue(result.ok);
        assertEquals("[my_speed]", result.guessedVariables.toString());

        result = parse("Math.sqrt(2)", validVariable, k -> "");
        assertTrue(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        result = parse("edge.blup()", validVariable, k -> "");
        assertFalse(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        result = parse("edge.getDistance()", validVariable, k -> "");
        assertTrue(result.ok);
        assertEquals("[edge]", result.guessedVariables.toString());

        // chained calls are not supported and the message must state a reason
        result = parse("edge.getName().contains(\"A 4\")", validVariable, k -> "");
        assertFalse(result.ok);
        assertTrue(result.guessedVariables.isEmpty());
        assertEquals("contains is an illegal method in a conditional expression", result.invalidMessage);

        assertFalse(parse("road_class == PRIMARY", s -> false, k -> "").ok);
        result = parse("road_class == PRIMARY", validVariable, k -> "");
        assertTrue(result.ok);
        assertEquals("[road_class]", result.guessedVariables.toString());

        result = parse("toll == Toll.NO", validVariable, k -> "");
        assertFalse(result.ok);
        assertEquals("[toll]", result.guessedVariables.toString());

        assertTrue(parse("road_class.ordinal()*2 == PRIMARY.ordinal()*2", validVariable, k -> "").ok);
        assertTrue(parse("Math.sqrt(road_class.ordinal()) > 1", validVariable, k -> "").ok);

        result = parse("(toll == NO || road_class == PRIMARY) && toll == NO", validVariable, k -> "");
        assertTrue(result.ok);
        assertEquals("[toll, road_class]", result.guessedVariables.toString());

        result = parse("backward_my_speed", validVariable, k -> "");
        assertTrue(result.ok);
        assertEquals("[backward_my_speed]", result.guessedVariables.toString());
    }

    @Test
    public void testAbs() {
        ParseResult result = parse("Math.abs(average_slope) < -0.5", "average_slope"::equals, k -> "");
        assertTrue(result.ok);
        assertEquals("[average_slope]", result.guessedVariables.toString());
    }

    @Test
    public void testNegativeConstant() {
        ParseResult result = parse("average_slope < -0.5", "average_slope"::equals, k -> "");
        assertTrue(result.ok);
        assertEquals("[average_slope]", result.guessedVariables.toString());
        result = parse("-average_slope > -0.5", "average_slope"::equals, k -> "");
        assertTrue(result.ok);
        assertEquals("[average_slope]", result.guessedVariables.toString());

        result = parse("Math.sqrt(-2)", (var) -> false, k -> "");
        assertTrue(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        // two unary minus must not become the decrement operator
        assertEquals("- -average_slope > -0.5", parse("- -average_slope > -0.5", "average_slope"::equals, k -> "").converted);
        assertEquals("!!car_access", parse("! !car_access", "car_access"::equals, k -> "").converted);
    }

    private static final NameValidator VALIDATOR = s -> s.equals("PRIMARY")
            || s.equals("road_class") || s.equals("street_name") || s.equals("prev_street_name");

    @Test
    public void testNoShiftedReplacement() {
        // line breaks, tabs and unicode escapes must not shift the enum or area replacement into the string
        String area = CustomWeightingHelper.class.getSimpleName() + ".in(this.in_area_1, edge)";
        for (String sep : Arrays.asList("\n", "\r", "\r\n", "\t", "\\u000a")) {
            assertEquals("street_name.equals(\"; attack(); \") || road_class == RoadClass.PRIMARY",
                    parse("street_name.equals(" + sep + "\"; attack(); \") || road_class == PRIMARY", VALIDATOR, k -> "RoadClass").converted);
            assertEquals("street_name.equals(\"; attack(); \") || " + area,
                    parse("street_name.equals(" + sep + "\"; attack(); \") || in_area_1", VALIDATOR, k -> "").converted);
        }
    }

    @Test
    public void testLiteralsAndOperators() {
        assertEquals("road_class == RoadClass.PRIMARY && prev_street_name.equals('x' + \"A 4\")",
                parse("road_class == PRIMARY && prev_street_name.equals('x' + \"A 4\")", VALIDATOR, k -> "RoadClass").converted);
        assertTrue(parse("prev_street_name.equals(street_name)", VALIDATOR, k -> "").ok);

        // enum value is validated too
        assertEquals("'FOO' not available", parse("road_class == FOO", VALIDATOR, k -> "RoadClass").invalidMessage);

        ParseResult result = parse("road_class.ordinal() & 1", VALIDATOR, k -> "");
        assertFalse(result.ok);
        assertNull(result.converted);
        assertEquals("operator & not allowed", result.invalidMessage);
    }
}
