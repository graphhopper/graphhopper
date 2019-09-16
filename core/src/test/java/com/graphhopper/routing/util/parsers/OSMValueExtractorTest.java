package com.graphhopper.routing.util.parsers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;

public class OSMValueExtractorTest {
    
    private final double DELTA = 0.1;

    @Test
    public void stringToTons() {
        assertEquals(1.5, OSMValueExtractor.stringToTons("1.5"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToTons("1.5 t"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToTons("1.5   t"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToTons("1.5 tons"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToTons("1.5 ton"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToTons("3306.9 lbs"), DELTA);
        assertEquals(3, OSMValueExtractor.stringToTons("3 T"), DELTA);
        assertEquals(3, OSMValueExtractor.stringToTons("3ton"), DELTA);

        // maximum gross weight
        assertEquals(6, OSMValueExtractor.stringToTons("6t mgw"), DELTA);
    }

    @Test(expected = NumberFormatException.class)
    public void stringToTonsException() {
        // Unexpected values
        OSMValueExtractor.stringToTons("weight limit 1.5t");
    }

    @Test
    public void stringToMeter() {
        assertEquals(1.5, OSMValueExtractor.stringToMeter("1.5"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToMeter("1.5m"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToMeter("1.5 m"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToMeter("1.5   m"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToMeter("1.5 meter"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToMeter("4 ft 11 in"), DELTA);
        assertEquals(1.5, OSMValueExtractor.stringToMeter("4'11''"), DELTA);


        assertEquals(3, OSMValueExtractor.stringToMeter("3 m."), DELTA);
        assertEquals(3, OSMValueExtractor.stringToMeter("3meters"), DELTA);
        assertEquals(0.8 * 3, OSMValueExtractor.stringToMeter("~3"), DELTA);
        assertEquals(3 * 0.8, OSMValueExtractor.stringToMeter("3 m approx"), DELTA);

        // 2.743 + 0.178
        assertEquals(2.921, OSMValueExtractor.stringToMeter("9 ft 7in"), DELTA);
        assertEquals(2.921, OSMValueExtractor.stringToMeter("9'7\""), DELTA);
        assertEquals(2.921, OSMValueExtractor.stringToMeter("9'7''"), DELTA);
        assertEquals(2.921, OSMValueExtractor.stringToMeter("9' 7\""), DELTA);

        assertEquals(2.743, OSMValueExtractor.stringToMeter("9'"), DELTA);
        assertEquals(2.743, OSMValueExtractor.stringToMeter("9 feet"), DELTA);
    }

    @Test(expected = NumberFormatException.class)
    public void stringToMeterException() {
        // Unexpected values
        OSMValueExtractor.stringToMeter("height limit 1.5m");
    }
}