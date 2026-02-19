package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.graphhopper.routing.weighting.custom.ConditionalExpressionVisitor.convertSingleToDoubleQuotes;
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

        // is_forward is a valid variable
        NameValidator withIsForward = s -> validVariable.isValid(s) || s.equals("is_forward");
        result = parse("is_forward", withIsForward, k -> "");
        assertTrue(result.ok);
        assertEquals("[is_forward]", result.guessedVariables.toString());

        result = parse("is_forward && road_class == PRIMARY", withIsForward, k -> "");
        assertTrue(result.ok);
        assertEquals("[is_forward, road_class]", result.guessedVariables.toString());
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
    }

    @Test
    public void testConvertSingleToDoubleQuotes() {
        assertEquals("road_class == PRIMARY", convertSingleToDoubleQuotes("road_class == PRIMARY"));
        assertEquals("tag(\"cycleway\") == \"lane\"", convertSingleToDoubleQuotes("tag('cycleway') == 'lane'"));
        assertEquals("tag(\"name\") == \"O'Brien\"", convertSingleToDoubleQuotes("tag('name') == 'O\\'Brien'"));
        assertThrows(IllegalArgumentException.class, () -> convertSingleToDoubleQuotes("tag('cycleway)"));
        assertThrows(IllegalArgumentException.class, () -> convertSingleToDoubleQuotes("tag(\"cycleway\")"));
    }

    @Test
    public void testTagGetExpression() {
        NameValidator validVariable = s -> Helper.toUpperCase(s).equals(s) || s.equals("road_class");

        // basic tag.get == test
        ParseResult result = parse("tag('cycleway') == 'lane'", validVariable, k -> "");
        assertTrue(result.ok);
        assertTrue(result.converted.toString().contains("\"lane\".equals(edge.get(this.kv_cycleway_enc))"));

        // tag.get != test
        result = parse("tag('lit') != 'yes'", validVariable, k -> "");
        assertTrue(result.ok);
        assertTrue(result.converted.toString().contains("!\"yes\".equals(edge.get(this.kv_lit_enc))"));

        // compound expression with tag.get and regular encoded value
        result = parse("tag('cycleway') == 'lane' || road_class == PRIMARY", validVariable, k -> "RoadClass");
        assertTrue(result.ok);
        assertTrue(result.guessedVariables.contains("road_class"));

        // compound: two tag.get expressions
        result = parse("tag('cycleway') == 'lane' || tag('cycleway') == 'track'", validVariable, k -> "");
        assertTrue(result.ok);
        String converted = result.converted.toString();
        assertTrue(converted.contains("\"lane\".equals(edge.get(this.kv_cycleway_enc))"), converted);
        assertTrue(converted.contains("\"track\".equals(edge.get(this.kv_cycleway_enc))"), converted);

        // tag.get == null
        result = parse("tag('lit') == null", validVariable, k -> "");
        assertTrue(result.ok);
        assertTrue(result.converted.toString().contains("edge.get(this.kv_lit_enc) == null"));

        // tag.get != null
        result = parse("tag('lit') != null", validVariable, k -> "");
        assertTrue(result.ok);
        assertTrue(result.converted.toString().contains("edge.get(this.kv_lit_enc) != null"));

        // reversed: literal on left, tag.get on right
        result = parse("'lane' == tag('cycleway')", validVariable, k -> "");
        assertTrue(result.ok);
        assertTrue(result.converted.toString().contains("\"lane\".equals(edge.get(this.kv_cycleway_enc))"));
    }
}
