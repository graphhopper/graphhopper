package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor.conditionalWeightToTons;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSMValueExtractorTest {

    private final double DELTA = 0.001;

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
        assertEquals(25.401, OSMValueExtractor.stringToTons("28 st"), DELTA);

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
        assertEquals(1.499, OSMValueExtractor.stringToMeter("4 ft 11 in"), DELTA);
        assertEquals(1.499, OSMValueExtractor.stringToMeter("4'11''"), DELTA);


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
        assertEquals(40, OSMValueExtractor.stringToKmh("40 km/h"), DELTA);
        assertEquals(40, OSMValueExtractor.stringToKmh("40km/h"), DELTA);
        assertEquals(40, OSMValueExtractor.stringToKmh("40kmh"), DELTA);
        assertEquals(64.374, OSMValueExtractor.stringToKmh("40mph"), DELTA);
        assertEquals(48.28, OSMValueExtractor.stringToKmh("30 mph"), DELTA);
        assertEquals(18.52, OSMValueExtractor.stringToKmh("10 knots"), DELTA);
        assertEquals(19, OSMValueExtractor.stringToKmh("19 kph"), DELTA);
        assertEquals(19, OSMValueExtractor.stringToKmh("19kph"), DELTA);

        assertEquals(50, OSMValueExtractor.stringToKmh("RO:urban"), DELTA);

        assertEquals(80, OSMValueExtractor.stringToKmh("RU:rural"), DELTA);

        assertEquals(6, OSMValueExtractor.stringToKmh("walk"), DELTA);
        assertEquals(MaxSpeed.UNLIMITED_SIGN_SPEED, OSMValueExtractor.stringToKmh("none"), DELTA);
    }

    @Test
    public void stringToKmhNaN() {
        assertTrue(Double.isNaN(OSMValueExtractor.stringToKmh(null)));
        assertTrue(Double.isNaN(OSMValueExtractor.stringToKmh("0")));
        assertTrue(Double.isNaN(OSMValueExtractor.stringToKmh("-20")));
    }

    @Test
    public void testConditionalWeightToTons() {
        assertEquals(7.5, conditionalWeightToTons("no @ (weight>7.5)"));
        assertEquals(7.5, conditionalWeightToTons("delivery @ (Mo-Sa 06:00-12:00); no @ (weight>7.5); no @ (length>12)"));
    }
}