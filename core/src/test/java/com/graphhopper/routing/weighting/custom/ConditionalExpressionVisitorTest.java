package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.routing.weighting.custom.CustomModelParser.isValidVariableName;
import static com.graphhopper.routing.weighting.custom.ConditionalExpressionVisitor.parseConditionalExpression;
import static org.junit.jupiter.api.Assertions.*;

public class ConditionalExpressionVisitorTest {

    private EncodedValueLookup lookup;

    @BeforeEach
    public void before() {
        StringEncodedValue sev = new StringEncodedValue("country", 10);
        lookup = new EncodingManager.Builder().add(sev).build();
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
            ParseResult res = parseConditionalExpression(toParse, allNamesInvalid, lookup);
            assertFalse(res.ok, "should not be simple condition: " + toParse);
            assertTrue(res.guessedVariables == null || res.guessedVariables.isEmpty());
        }

        assertFalse(parseConditionalExpression("edge; getClass()", allNamesInvalid, lookup).ok);
    }

    @Test
    public void testConvertExpression() {
        NameValidator validVariable = s -> isValidVariableName(s)
                || Helper.toUpperCase(s).equals(s) || s.equals("road_class") || s.equals("toll");

        ParseResult result = parseConditionalExpression("toll == NO", validVariable, lookup);
        assertTrue(result.ok);
        assertEquals("[toll]", result.guessedVariables.toString());

        assertEquals("road_class == RoadClass.PRIMARY",
                parseConditionalExpression("road_class == PRIMARY", validVariable, lookup).converted.toString());
        assertEquals("toll == Toll.NO", parseConditionalExpression("toll == NO", validVariable, lookup).converted.toString());
        assertEquals("toll == Toll.NO || road_class == RoadClass.NO", parseConditionalExpression("toll == NO || road_class == NO", validVariable, lookup).converted.toString());

        // convert in_area variable to function call:
        assertEquals(CustomWeightingHelper.class.getSimpleName() + ".in(this.in_custom_1, edge)",
                parseConditionalExpression("in_custom_1", validVariable, lookup).converted.toString());

        // no need to inject:
        assertNull(parseConditionalExpression("toll == Toll.NO", validVariable, lookup).converted);
    }

    @Test
    public void testStringExpression() {
        NameValidator validVariable = s -> isValidVariableName(s) || s.equals("country");

        ParseResult result = parseConditionalExpression("country == \"DEU\"", validVariable, lookup);
        assertTrue(result.ok);
        assertEquals("[country]", result.guessedVariables.toString());
        assertEquals("country == 1", result.converted.toString());

        // unknown String should result in a negative integer. If we would throw an Exception here the same script that
        // works on a global map will not work on a smaller map where the "blup" String is missing
        result = parseConditionalExpression("country == \"blup\"", validVariable, lookup);
        assertTrue(result.ok);
        assertEquals("[country]", result.guessedVariables.toString());
        assertEquals("country == -1", result.converted.toString());
    }

    @Test
    public void isValidAndSimpleCondition() {
        NameValidator validVariable = s -> isValidVariableName(s)
                || Helper.toUpperCase(s).equals(s) || s.equals("road_class") || s.equals("toll");
        ParseResult result = parseConditionalExpression("edge == edge", validVariable, lookup);
        assertTrue(result.ok);
        assertEquals("[edge]", result.guessedVariables.toString());

        result = parseConditionalExpression("Math.sqrt(2)", validVariable, lookup);
        assertTrue(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        result = parseConditionalExpression("edge.blup()", validVariable, lookup);
        assertFalse(result.ok);
        assertTrue(result.guessedVariables.isEmpty());

        result = parseConditionalExpression("edge.getDistance()", validVariable, lookup);
        assertTrue(result.ok);
        assertEquals("[edge]", result.guessedVariables.toString());
        assertFalse(parseConditionalExpression("road_class == PRIMARY", s -> false, lookup).ok);
        result = parseConditionalExpression("road_class == PRIMARY", validVariable, lookup);
        assertTrue(result.ok);
        assertEquals("[road_class]", result.guessedVariables.toString());

        result = parseConditionalExpression("toll == Toll.NO", validVariable, lookup);
        assertFalse(result.ok);
        assertEquals("[toll]", result.guessedVariables.toString());

        assertTrue(parseConditionalExpression("road_class.ordinal()*2 == PRIMARY.ordinal()*2", validVariable, lookup).ok);
        assertTrue(parseConditionalExpression("Math.sqrt(road_class.ordinal()) > 1", validVariable, lookup).ok);

        result = parseConditionalExpression("(toll == NO || road_class == PRIMARY) && toll == NO", validVariable, lookup);
        assertTrue(result.ok);
        assertEquals("[toll, road_class]", result.guessedVariables.toString());
    }

    @Test
    public void errorMessage() {
        NameValidator validVariable = s -> lookup.hasEncodedValue(s);

        // existing encoded value but not added
        IllegalArgumentException ret = assertThrows(IllegalArgumentException.class,
                () -> ConditionalExpressionVisitor.parseExpressions(new StringBuilder(),
                        validVariable, "[HERE]", new HashSet<>(),
                        Arrays.asList(If("max_weight > 10", Statement.Op.MULTIPLY, "0")),
                        lookup, ""));
        assertTrue(ret.getMessage().startsWith("[HERE] invalid expression \"max_weight > 10\": 'max_weight' not available"), ret.getMessage());

        // invalid variable or constant (NameValidator returns false)
        ret = assertThrows(IllegalArgumentException.class,
                () -> ConditionalExpressionVisitor.parseExpressions(new StringBuilder(),
                        validVariable, "[HERE]", new HashSet<>(),
                        Arrays.asList(If("country == GERMANY", Statement.Op.MULTIPLY, "0")),
                        lookup, ""));
        assertTrue(ret.getMessage().startsWith("[HERE] invalid expression \"country == GERMANY\": 'GERMANY' not available"), ret.getMessage());

        // not whitelisted method
        ret = assertThrows(IllegalArgumentException.class,
                () -> ConditionalExpressionVisitor.parseExpressions(new StringBuilder(),
                        validVariable, "[HERE]", new HashSet<>(),
                        Arrays.asList(If("edge.fetchWayGeometry().size() > 2", Statement.Op.MULTIPLY, "0")),
                        lookup, ""));
        assertTrue(ret.getMessage().startsWith("[HERE] invalid expression \"edge.fetchWayGeometry().size() > 2\": size is illegal method"), ret.getMessage());
    }
}