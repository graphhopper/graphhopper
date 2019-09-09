package com.graphhopper.routing.util.parsers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.graphhopper.routing.util.parsers.helpers.OSMWeightExtractor;

public class OSMWeightExtractorTest {
    
    private final double DELTA = 0.1;

    @Test
    public void stringToTons() {
        assertEquals(1.5, OSMWeightExtractor.stringToTons("1.5"), DELTA);
        assertEquals(1.5, OSMWeightExtractor.stringToTons("1.5 t"), DELTA);
        assertEquals(1.5, OSMWeightExtractor.stringToTons("1.5   t"), DELTA);
        assertEquals(1.5, OSMWeightExtractor.stringToTons("1.5 tons"), DELTA);
        assertEquals(1.5, OSMWeightExtractor.stringToTons("1.5 ton"), DELTA);
        assertEquals(1.5, OSMWeightExtractor.stringToTons("3306.9 lbs"), DELTA);
        assertEquals(3, OSMWeightExtractor.stringToTons("3 T"), DELTA);
        assertEquals(3, OSMWeightExtractor.stringToTons("3ton"), DELTA);

        // maximum gross weight
        assertEquals(6, OSMWeightExtractor.stringToTons("6t mgw"), DELTA);
    }

    @Test(expected = NumberFormatException.class)
    public void stringToTonsException() {
        // Unexpected values
        OSMWeightExtractor.stringToTons("weight limit 1.5t");
    }
}