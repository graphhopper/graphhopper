package com.graphhopper;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GHResponseTest
{
    @Test
    public void testToString() throws Exception
    {
        assertEquals("no alternatives", new GHResponse().toString());
    }
}
