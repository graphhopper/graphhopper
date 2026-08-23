package com.graphhopper.routing.weighting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BikeClimbSpeedTableTest {

    @Test
    public void testSpeed() {
        BikeClimbSpeedTable table = new BikeClimbSpeedTable(120, 95);
        assertEquals(18, table.getSpeed(0), 0.01);
        // aero resistance dominates for small slopes
        assertEquals(12.85, table.getSpeed(2), 0.01);
        assertEquals(7.77, table.getSpeed(5), 0.01);
        assertEquals(3.66, table.getSpeed(12), 0.01);
        // blending into the walking speed of the pushing cyclist (0.8 times Tobler's hiking function)
        assertEquals(2.38, table.getSpeed(15), 0.01);
        assertEquals(2.0, table.getSpeed(20), 0.01);
        assertEquals(1.34, table.getSpeed(31.5), 0.01);
        // beyond the table the last value is used
        assertEquals(table.getSpeed(40), table.getSpeed(50), 0.001);
        assertThrows(IllegalArgumentException.class, () -> table.getSpeed(-1));
        assertThrows(IllegalArgumentException.class, () -> new BikeClimbSpeedTable(120, 95, 3, 0.006));
    }

    @Test
    public void testSlopeOffset() {
        BikeClimbSpeedTable table = new BikeClimbSpeedTable(120, 95);
        // no offset for the base speed and above
        assertEquals(0, table.getSlopeOffset(18), 0.01);
        assertEquals(0, table.getSlopeOffset(25), 0.01);
        // a reduced speed is interpreted as a rolling resistance crr * base/current, i.e. as slope offset 100 * crr * (base/current - 1) ...
        assertEquals(0.035, table.getSlopeOffset(17), 0.001);
        assertEquals(0.15, table.getSlopeOffset(14.4), 0.01);
        assertEquals(0.6, table.getSlopeOffset(9), 0.01);
        // ... but the rolling resistance is at most 3 * crr
        assertEquals(1.2, table.getSlopeOffset(6), 0.01);
        assertEquals(1.2, table.getSlopeOffset(4), 0.01);

        // the factor is relative to the current speed: 12% on a 9km/h track is 3.45km/h instead of 3.66km/h
        assertEquals(3.45, 9 * table.getBikeClimbFactor(12, 9), 0.01);
        assertEquals(3.66, 18 * table.getBikeClimbFactor(12, 18), 0.01);
        // the factor never increases the speed above the current speed
        assertEquals(1, table.getBikeClimbFactor(12, 3));
        assertEquals(1, table.getBikeClimbFactor(-5, 18));
    }
}
