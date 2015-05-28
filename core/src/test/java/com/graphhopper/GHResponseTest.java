package com.graphhopper;

import junit.framework.TestCase;

public class GHResponseTest extends TestCase
{
    public void testToString() throws Exception
    {
        assertEquals("nodes:0; ", new GHResponse().toString());
    }
}