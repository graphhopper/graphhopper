package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.graphhopper.routing.weighting.custom.ConditionalExpressionVisitor.parse;
import static com.graphhopper.routing.weighting.custom.CustomModelParser.isValidVariableName;
import static org.junit.jupiter.api.Assertions.*;

public class ConditionalExpressionVisitorTest {

    @BeforeEach
    public void before() {
        StringEncodedValue sev = new StringEncodedValue("country", 10);
        new EncodingManager.Builder().add(sev).build();
        sev.setString(false, new IntsRef(1), "DEU");
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
            ParseResult res = parse(toParse, allNamesInvalid);
            assertFalse(res.ok, "should not be simple condition: " + toParse);
            assertTrue(res.guessedVariables == null || res.guessedVariables.isEmpty());
        }

        assertFalse(parse("edge; getClass()", allNamesInvalid).ok);
    }

    @Test
    public void testConvertExpression() {
        NameValidator validVariable = s -> isValidVariableName(s)
                || Helper.toUpperCase(s).equals(s) || s.equals("road_class") || s.equals("toll");

        ParseResult result = parse("toll == NO", validVariable);
        assertTrue(result.ok);
        assertEquals("[toll]", result.guessedVariables.toString());

        assertEquals("road_class == RoadClass.PRIMARY",
                parse("road_class == PRIMARY", validVariable).converted.toString());
        assertEquals("toll == Toll.NO", parse("toll == NO", validVariable).converted.toString());
        assertEquals("toll == Toll.NO || road_class == RoadClass.NO", parse("toll == NO || road_class == NO", validVariable).converted.toString());

        // convert in_area variable to function call:
        assertEquals(CustomWeightingHelper.class.getSimpleName() + ".in(this.in_custom_1, edge)",
                parse("in_custom_1", validVariable).converted.toString());

        // no need to inject:
        assertNull(parse("toll == Toll.NO", validVariable).converted);
    }

    @Test
    public void isValidAndSimpleCondition() {
        NameValidator validVariable = s -> isValidVariableName(s)
                || Helper.toUpperCase(s).equals(s) || s.equals("road_class") || s.equals("toll") || s.equals("my_speed");

        ParseResult result = parse("in_something", validVariable);
        assertTrue(result.ok);
        assertEquals("[in_something]", result.guessedVariables.toString());

        result = parse("edge == edge", validVariable);
        assertFalse(result.ok);

        result = parse("Math.sqrt(my_speed)", validVariable);
        assertTrue(result.ok);
        assertEquals("[my_speed]", result.guessedVariables.toString());

        result = parse("Math.sqrt(2)", validVariable);
        assertTrue(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        result = parse("edge.blup()", validVariable);
        assertFalse(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        result = parse("edge.getDistance()", validVariable);
        assertTrue(result.ok);
        assertEquals("[edge]", result.guessedVariables.toString());
        assertFalse(parse("road_class == PRIMARY", s -> false).ok);
        result = parse("road_class == PRIMARY", validVariable);
        assertTrue(result.ok);
        assertEquals("[road_class]", result.guessedVariables.toString());

        result = parse("toll == Toll.NO", validVariable);
        assertFalse(result.ok);
        assertEquals("[toll]", result.guessedVariables.toString());

        assertTrue(parse("road_class.ordinal()*2 == PRIMARY.ordinal()*2", validVariable).ok);
        assertTrue(parse("Math.sqrt(road_class.ordinal()) > 1", validVariable).ok);

        result = parse("(toll == NO || road_class == PRIMARY) && toll == NO", validVariable);
        assertTrue(result.ok);
        assertEquals("[toll, road_class]", result.guessedVariables.toString());
    }

    @Test
    public void testNegativeConstant() {
        ParseResult result = parse("average_slope < -0.5", "average_slope"::equals);
        assertTrue(result.ok);
        assertEquals("[average_slope]", result.guessedVariables.toString());
        result = parse("-average_slope > -0.5", "average_slope"::equals);
        assertTrue(result.ok);
        assertEquals("[average_slope]", result.guessedVariables.toString());

        result = parse("Math.sqrt(-2)", (var) -> false);
        assertTrue(result.ok);
        assertTrue(result.guessedVariables.isEmpty());
    }
}