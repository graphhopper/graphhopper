package com.graphhopper.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OMapTest {

    @Test
    public void testDifferentNumberTypes() {
        OMap omap = new OMap();
        omap.put("int", 1);
        assertEquals(1.0, omap.getFloat("int", 2), .1);
        assertEquals(2.0, omap.getFloat("other", 2), .1);

        omap.put("double", 1.33d);
        assertEquals(1.33, omap.getFloat("double", 2), .01);
        assertEquals(1.33, omap.getDouble("double", 2), .01);
        assertEquals(1.0, omap.getInt("double", 2), .01);

        omap.put("double", Double.MAX_VALUE);
        assertTrue(Float.isInfinite(omap.getFloat("double", 2)));
        assertEquals(Double.MAX_VALUE, omap.getDouble("double", 2), .01);
        assertEquals(Integer.MAX_VALUE, omap.getInt("double", 2), .01);
    }
}