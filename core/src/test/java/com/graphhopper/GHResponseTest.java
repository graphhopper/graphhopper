package com.graphhopper;

import static org.junit.Assert.*;
import org.junit.Test;

public class GHResponseTest
{
    @Test
    public void testToString() throws Exception
    {
        assertEquals("no paths", new GHResponse().toString());
    }

    @Test
    public void testHasNoErrorIfEmpty() throws Exception
    {
        assertFalse(new GHResponse().hasErrors());
        GHResponse rsp = new GHResponse();
        rsp.add(new PathWrapper());
        assertFalse(rsp.hasErrors());
    }
}
