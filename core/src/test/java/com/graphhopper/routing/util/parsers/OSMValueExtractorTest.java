package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OSMValueExtractorTest {

    private final double DELTA = 0.01;

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
        assertEquals(10, OSMValueExtractor.stringToTons("10000 kg"), DELTA);
        assertEquals(25.4, OSMValueExtractor.stringToTons("28 st"), DELTA);

        // maximum gross weight
        assertEquals(6, OSMValueExtractor.stringToTons("6t mgw"), DELTA);
    }

    @Test
    public void stringToTonsNaN() {
        assertTrue(Double.isNaN(OSMValueExtractor.stringToTons("weight limit 1.5t")));
    }

    @Test
    public void stringToTonsNaN2() {
        assertTrue(Double.isNaN(OSMValueExtractor.stringToTons("")));
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

        assertEquals(1.5, OSMValueExtractor.stringToMeter("150 cm"), DELTA);
    }

    @Test
    public void stringToMeterNaN() {
        assertTrue(Double.isNaN(OSMValueExtractor.stringToMeter("height limit 1.5m")));
    }

    @Test
    public void stringToMeterNaN2() {
        assertTrue(Double.isNaN(OSMValueExtractor.stringToMeter("")));
    }

    @Test
    public void stringToMeterNaN3() {
        assertTrue(Double.isNaN(OSMValueExtractor.stringToMeter("default")));
    }

    @Test
    public void stringToKmh() {
        assertEquals(40, OSMValueExtractor.stringToKmh("40 km/h"), 1e-3);
        assertEquals(40, OSMValueExtractor.stringToKmh("40km/h"), 1e-3);
        assertEquals(40, OSMValueExtractor.stringToKmh("40kmh"), 1e-3);
        assertEquals(64.374, OSMValueExtractor.stringToKmh("40mph"), 1e-3);
        assertEquals(48.28, OSMValueExtractor.stringToKmh("30 mph"), 1e-3);
        assertEquals(-1, OSMValueExtractor.stringToKmh(null), 1e-3);
        assertEquals(18.52, OSMValueExtractor.stringToKmh("10 knots"), 1e-3);
        assertEquals(19, OSMValueExtractor.stringToKmh("19 kph"), 1e-3);
        assertEquals(19, OSMValueExtractor.stringToKmh("19kph"), 1e-3);

        assertEquals(50, OSMValueExtractor.stringToKmh("RO:urban"), 1e-3);

        assertEquals(80, OSMValueExtractor.stringToKmh("RU:rural"), 1e-3);

        assertEquals(6, OSMValueExtractor.stringToKmh("walk"), 1e-3);
    }
}