package com.graphhopper.routing.weighting.custom;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static com.graphhopper.routing.weighting.custom.ExpressionBuilder.isValidVariableName;
import static com.graphhopper.routing.weighting.custom.ExpressionVisitor.parseExpression;
import static org.junit.jupiter.api.Assertions.*;

public class ExpressionVisitorTest {

    @Test
    public void protectUsFromStuff() {
        ExpressionVisitor.NameValidator allNamesInvalid = s -> false;
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
            ExpressionVisitor.ParseResult res = parseExpression(toParse, allNamesInvalid);
            assertFalse(res.ok, "should not be simple condition: " + toParse);
            assertTrue(res.guessedVariables == null || res.guessedVariables.isEmpty());
        }

        assertFalse(parseExpression("edge; getClass()", allNamesInvalid).ok);
    }

    @Test
    public void testConvertExpression() {
        ExpressionVisitor.NameValidator validVariable = s -> isValidVariableName(s)
                || s.toUpperCase(Locale.ROOT).equals(s) || s.equals("road_class") || s.equals("toll");

        ExpressionVisitor.ParseResult result = parseExpression("toll == NO", validVariable);
        assertTrue(result.ok);
        assertEquals("[toll]", result.guessedVariables.toString());

        assertEquals("road_class == RoadClass.PRIMARY", parseExpression("road_class == PRIMARY", validVariable).converted.toString());
        assertEquals("toll == Toll.NO", parseExpression("toll == NO", validVariable).converted.toString());
        assertEquals("toll == Toll.NO || road_class == RoadClass.NO", parseExpression("toll == NO || road_class == NO", validVariable).converted.toString());
    }

    @Test
    public void isValidAndSimpleCondition() {
        ExpressionVisitor.NameValidator validVariable = s -> isValidVariableName(s)
                || s.toUpperCase(Locale.ROOT).equals(s) || s.equals("road_class") || s.equals("toll");
        ExpressionVisitor.ParseResult result = parseExpression("edge == edge", validVariable);
        assertTrue(result.ok);
        assertEquals("[edge]", result.guessedVariables.toString());

        result = parseExpression("Math.sqrt(2)", validVariable);
        assertTrue(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        result = parseExpression("edge.blup()", validVariable);
        assertFalse(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        result = parseExpression("edge.getDistance()", validVariable);
        assertTrue(result.ok);
        assertEquals("[edge]", result.guessedVariables.toString());
        assertFalse(parseExpression("road_class == PRIMARY", s -> false).ok);
        result = parseExpression("road_class == PRIMARY", validVariable);
        assertTrue(result.ok);
        assertEquals("[road_class]", result.guessedVariables.toString());

        result = parseExpression("toll == Toll.NO", validVariable);
        assertFalse(result.ok);
        assertEquals("[toll]", result.guessedVariables.toString());

        assertTrue(parseExpression("road_class.ordinal()*2 == PRIMARY.ordinal()*2", validVariable).ok);
        assertTrue(parseExpression("Math.sqrt(road_class.ordinal()) > 1", validVariable).ok);
    }
}