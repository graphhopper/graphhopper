package com.graphhopper.routing.weighting.custom;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static com.graphhopper.routing.weighting.custom.ScriptHelper.isValidVariableName;
import static com.graphhopper.routing.weighting.custom.ScriptHelper.parseAndGuessParametersFromCondition;
import static org.junit.jupiter.api.Assertions.*;

public class ScriptHelperTest {

    @Test
    public void protectUsFromStuff() {
        ScriptHelper.NameValidator allNamesInvalid = s -> false;
        for (String toParse : Arrays.asList("",
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
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd" +
                        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd")) {
            ScriptHelper.ParseResult res = parseAndGuessParametersFromCondition(toParse, allNamesInvalid);
            assertFalse(res.ok, "should not be simple condition: " + toParse);
            assertTrue(res.guessVariables == null || res.guessVariables.isEmpty());
        }

        assertFalse(parseAndGuessParametersFromCondition("edge; getClass()", allNamesInvalid).ok);
    }

    @Test
    public void testConvertExpression() {
        ScriptHelper.NameValidator validVariable = s -> isValidVariableName(s)
                || s.toUpperCase(Locale.ROOT).equals(s) || s.equals("road_class") || s.equals("toll");

        ScriptHelper.ParseResult result = parseAndGuessParametersFromCondition("toll == NO", validVariable);
        assertTrue(result.ok);
        assertEquals("[toll]", result.guessVariables.toString());

        assertEquals("road_class == RoadClass.PRIMARY", parseAndGuessParametersFromCondition("road_class == PRIMARY", validVariable).converted.toString());
        assertEquals("toll == Toll.NO", parseAndGuessParametersFromCondition("toll == NO", validVariable).converted.toString());
        assertEquals("toll == Toll.NO || road_class == RoadClass.NO", parseAndGuessParametersFromCondition("toll == NO || road_class == NO", validVariable).converted.toString());
    }

    @Test
    public void isValidAndSimpleCondition() {
        ScriptHelper.NameValidator validVariable = s -> isValidVariableName(s)
                || s.toUpperCase(Locale.ROOT).equals(s) || s.equals("road_class") || s.equals("toll");
        ScriptHelper.ParseResult result = parseAndGuessParametersFromCondition("edge == edge", validVariable);
        assertTrue(result.ok);
        assertEquals("[edge]", result.guessVariables.toString());

        result = parseAndGuessParametersFromCondition("Math.sqrt(2)", validVariable);
        assertTrue(result.ok);
        assertTrue(result.guessVariables.isEmpty());

        result = parseAndGuessParametersFromCondition("edge.blup()", validVariable);
        assertFalse(result.ok);
        assertTrue(result.guessVariables.isEmpty());

        result = parseAndGuessParametersFromCondition("edge.getDistance()", validVariable);
        assertTrue(result.ok);
        assertEquals("[edge]", result.guessVariables.toString());
        assertFalse(parseAndGuessParametersFromCondition("road_class == PRIMARY", s -> false).ok);
        result = parseAndGuessParametersFromCondition("road_class == PRIMARY", validVariable);
        assertTrue(result.ok);
        assertEquals("[road_class]", result.guessVariables.toString());

        result = parseAndGuessParametersFromCondition("toll == Toll.NO", validVariable);
        assertFalse(result.ok);
        assertEquals("[toll]", result.guessVariables.toString());

        assertTrue(parseAndGuessParametersFromCondition("road_class.ordinal()*2 == PRIMARY.ordinal()*2", validVariable).ok);
        assertTrue(parseAndGuessParametersFromCondition("Math.sqrt(road_class.ordinal()) > 1", validVariable).ok);
    }
}