package com.graphhopper.routing.weighting.custom;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static com.graphhopper.routing.weighting.custom.ScriptHelper.parseAndGuessParametersFromCondition;
import static org.junit.jupiter.api.Assertions.*;

public class ScriptHelperTest {

    @Test
    public void protectUsFromStuff() {
        HashSet<String> set = new HashSet<>();
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
            assertFalse(parseAndGuessParametersFromCondition(set, toParse, allNamesInvalid), "should not be simple condition: " + toParse);
            assertEquals("[]", set.toString());
        }

        assertFalse(parseAndGuessParametersFromCondition(set, "edge; getClass()", allNamesInvalid));
    }

    @Test
    public void isValidAndSimpleCondition() {
        HashSet<String> set = new HashSet<>();
        ScriptHelper.NameValidator nameValidator1 = s -> s.equals("edge") || s.startsWith(ScriptHelper.AREA_PREFIX)
                || s.equals("PRIMARY") || s.equals("road_class");
        assertTrue(parseAndGuessParametersFromCondition(set, "edge == edge", nameValidator1));
        assertEquals("[edge]", set.toString());
        assertTrue(parseAndGuessParametersFromCondition(set, "edge.getDistance()", nameValidator1));
        assertEquals("[edge]", set.toString());
        assertTrue(parseAndGuessParametersFromCondition(set, "road_class == PRIMARY", nameValidator1));
        assertEquals("[edge, road_class]", set.toString());
        assertFalse(parseAndGuessParametersFromCondition(set, "road_class == PRIMARY", s -> false));
        assertTrue(parseAndGuessParametersFromCondition(set, "road_class.ordinal()*2 == PRIMARY.ordinal()*2", nameValidator1));
        // TODO call inside a method call is currently not supported
        //  assertTrue(parseAndGuessParametersFromCondition(set, "Math.sqrt(road_class.ordinal()) > 1", nameValidator1));
        set.clear();
    }
}