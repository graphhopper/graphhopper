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
        // blending into the walking speed of the pushing cyclist
        assertEquals(2.29, table.getSpeed(15), 0.01);
        assertEquals(2.0, table.getSpeed(20), 0.01);
        assertThrows(IllegalArgumentException.class, () -> table.getSpeed(-1));
        assertThrows(IllegalArgumentException.class, () -> new BikeClimbSpeedTable(120, 95, 3, 0.006));
    }

    @Test
    public void testSlopeOffset() {
        BikeClimbSpeedTable table = new BikeClimbSpeedTable(120, 95);
        // no offset for the base speed and above
        assertEquals(0, table.getSlopeOffset(18), 0.01);
        assertEquals(0, table.getSlopeOffset(25), 0.01);
        // a slightly reduced speed is interpreted as an increased rolling resistance (linear below the base speed) ...
        assertEquals(0.37, table.getSlopeOffset(17), 0.01);
        assertEquals(0.75, table.getSlopeOffset(16), 0.01);
        // ... but the increase is capped at 0.012 which is already reached for 14.8km/h (continuous transition)
        assertEquals(1.2, table.getSlopeOffset(14.8), 0.01);
        assertEquals(1.2, table.getSlopeOffset(14.6), 0.01);
        assertEquals(1.2, table.getSlopeOffset(14.4), 0.01);
        assertEquals(1.2, table.getSlopeOffset(9), 0.01);

        // the factor is relative to the current speed: 12% on a 9km/h track is 3.02km/h instead of 3.66km/h
        assertEquals(3.02, 9 * table.getBikeClimbFactor(12, 9), 0.01);
        assertEquals(3.66, 18 * table.getBikeClimbFactor(12, 18), 0.01);
        // the factor never increases the speed above the current speed
        assertEquals(1, table.getBikeClimbFactor(12, 3));
        assertEquals(1, table.getBikeClimbFactor(-5, 18));
    }
}
