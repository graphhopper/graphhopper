package com.graphhopper;

import static org.junit.Assert.*;
import org.junit.Test;

public class GHResponseTest
{
    @Test
    public void testToString() throws Exception
    {
        assertEquals("no alternatives", new GHResponse().toString());
    }

    @Test
    public void testHasError() throws Exception
    {
        assertTrue(new GHResponse().hasErrors());
        GHResponse rsp = new GHResponse();
        rsp.addAlternative(new AltResponse());
        assertFalse(rsp.hasErrors());
    }
}
