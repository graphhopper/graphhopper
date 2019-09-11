package com.graphhopper.expression;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GSParserTest {

    @Test
    public void testParse() throws IOException {
        GSParser parser = new GSParser();
        List<GSAssignment> assignments = parser.parse(new StringReader("base: 'car'\n" +
                "priority:\n" +
                "  surface == 'mud' ? 0.1"));

        assertEquals(2, assignments.size());

        assertEquals(1, assignments.get(0).getExpressions().size());
        GSExpression expression = assignments.get(0).getExpressions().get(0);
        assertEquals("unconditional", expression.getName());
        assertEquals("car", expression.getValue());

        assertEquals(1, assignments.get(1).getExpressions().size());
        expression = assignments.get(1).getExpressions().get(0);
        assertEquals("==", expression.getName());
        assertEquals(0.1, expression.getValue());
        assertEquals("surface", expression.getParameters().get(0));
        assertEquals("mud", expression.getParameters().get(1));
    }

    @Test
    public void testWrongUnconditionalType() throws IOException {
        GSParser parser = new GSParser();
        try {
            parser.parse(new StringReader("\n\n #\nbase: car # blup"));
            fail();
        } catch (GSParseException ex) {
            assertEquals(4, ex.getLineNumber());
            assertEquals("base: car # blup", ex.getLine());
            assertTrue(ex.getMessage().contains("expected to be a number or a string"));
        }

        // this is fine
        List<GSAssignment> assignments = parser.parse(new StringReader("\n\n #\nbase: 'car' # blup"));
        assertEquals("car", assignments.get(0).getExpressions().get(0).getValue());
    }
}