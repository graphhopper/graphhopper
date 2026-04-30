package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

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
                parse("road_class == PRIMARY", validVariable, k -> "Hello").converted.toString());
        assertEquals("toll == Toll.NO", parse("toll == NO", validVariable, k -> "Toll").converted.toString());
        assertEquals("toll == Toll.NO || road_class == RoadClass.NO", parse("toll == NO || road_class == NO", validVariable, k -> k.equals("toll") ? "Toll" : "RoadClass").converted.toString());

        // convert in_area variable to function call:
        assertEquals(CustomWeightingHelper.class.getSimpleName() + ".in(this.in_custom_1, edge)",
                parse("in_custom_1", validVariable, k -> "").converted.toString());

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
    public void testConvertCondition() {
        NameValidator validVariable = s -> Helper.toUpperCase(s).equals(s) || s.equals("road_class") || s.equals("road_environment") || s.equals("max_speed") || s.equals("bike_road_access") || s.equals("prev_bike_road_access");
        ClassHelper helper = k -> k.equals("road_class") ? "RoadClass" : k.equals("road_environment") ? "RoadEnvironment" : k.equals("max_speed") ? "MaxSpeed" : "BikeRoadAccess";

        // use case should read like "road_class is in set_of(SECONDARY,PRIMARY)
        ParseResult result = parse("road_class == SECONDARY || PRIMARY", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("[road_class]", result.guessedVariables.toString());
        assertEquals("road_class == RoadClass.SECONDARY || road_class == RoadClass.PRIMARY", result.converted.toString());

        // still support old explicit way
        result = parse("road_class == SECONDARY || road_class == PRIMARY", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("road_class == RoadClass.SECONDARY || road_class == RoadClass.PRIMARY", result.converted.toString());

        // try different use case like 'not in set_of(SECONDARY,PRIMARY)'
        result = parse("road_class != SECONDARY && PRIMARY", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("road_class != RoadClass.SECONDARY && road_class != RoadClass.PRIMARY", result.converted.toString());

        // and more than 2 arguments
        result = parse("road_class == SECONDARY || PRIMARY|| TERTIARY", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("road_class == RoadClass.SECONDARY || road_class == RoadClass.PRIMARY|| road_class == RoadClass.TERTIARY", result.converted.toString());

        // || and && should both work
        result = parse("road_class == SECONDARY && PRIMARY && TERTIARY || RESIDENTIAL", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("road_class == RoadClass.SECONDARY && road_class == RoadClass.PRIMARY && road_class == RoadClass.TERTIARY || road_class == RoadClass.RESIDENTIAL", result.converted.toString());

        // but no variable inclusion outside of parenthesis (parsing here works but later compiling obviously not)
        result = parse("(road_class == SECONDARY && PRIMARY) && TERTIARY || RESIDENTIAL", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("(road_class == RoadClass.SECONDARY && road_class == RoadClass.PRIMARY) && TERTIARY || RESIDENTIAL", result.converted.toString());

        // also such mixed constructs won't work
        result = parse("road_class == SECONDARY || PRIMARY && road_environment == TUNNEL || BRIDGE || ROAD", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("road_class == RoadClass.SECONDARY || PRIMARY && road_environment == RoadEnvironment.TUNNEL || BRIDGE || ROAD", result.converted.toString());

        // when combining multiple conditions with different variables you need to put brackets around them ...
        result = parse("(road_class == SECONDARY || PRIMARY) && (road_environment == TUNNEL || BRIDGE || ROAD)", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("(road_class == RoadClass.SECONDARY || road_class == RoadClass.PRIMARY) && (road_environment == RoadEnvironment.TUNNEL || road_environment == RoadEnvironment.BRIDGE || road_environment == RoadEnvironment.ROAD)", result.converted.toString());

        // ... or use the natural preference of &&
        result = parse("road_class == SECONDARY && PRIMARY || road_environment == TUNNEL && BRIDGE && ROAD", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("road_class == RoadClass.SECONDARY && road_class == RoadClass.PRIMARY || road_environment == RoadEnvironment.TUNNEL && road_environment == RoadEnvironment.BRIDGE && road_environment == RoadEnvironment.ROAD", result.converted.toString());

        // no automatic bracket placement => no working replacement mechanism
        result = parse("road_environment == TUNNEL && road_class == SECONDARY || PRIMARY", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("road_environment == RoadEnvironment.TUNNEL && road_class == RoadClass.SECONDARY || PRIMARY", result.converted.toString());
        result = parse("road_environment == TUNNEL && (road_class == SECONDARY || PRIMARY)", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("road_environment == RoadEnvironment.TUNNEL && (road_class == RoadClass.SECONDARY || road_class == RoadClass.PRIMARY)", result.converted.toString());

        // 'variable include' currently won't work for numbers, but has no practical relevance at the moment
        result = parse("max_speed == 90 || 100", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("max_speed == 90 || 100", result.converted.toString());

        result = parse("prev_bike_road_access != bike_road_access && (bike_road_access == DESTINATION || PRIVATE)", validVariable, helper);
        assertTrue(result.ok);
        assertEquals("prev_bike_road_access != bike_road_access && (bike_road_access == BikeRoadAccess.DESTINATION || bike_road_access == BikeRoadAccess.PRIVATE)", result.converted.toString());
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
    }
}
