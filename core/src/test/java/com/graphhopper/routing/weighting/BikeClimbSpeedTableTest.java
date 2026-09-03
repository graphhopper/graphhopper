package com.graphhopper.routing.weighting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BikeClimbSpeedTableTest {

    @Test
    public void testSpeed() {
        BikeClimbSpeedTable table = new BikeClimbSpeedTable(120, 95, 0.6, 0.006);
        // the flat speed follows from the power balance
        assertEquals(22.14, table.getFlatSpeed(), 0.01);
        assertEquals(22.14, table.getSpeed(0), 0.01);
        // aero resistance dominates for small slopes
        assertEquals(14.36, table.getSpeed(2), 0.01);
        assertEquals(8.0, table.getSpeed(5), 0.01);
        assertEquals(3.67, table.getSpeed(12), 0.01);
        // blending into the walking speed of the pushing cyclist (0.8 times Tobler's hiking function)
        assertEquals(2.38, table.getSpeed(15), 0.01);
        assertEquals(2.0, table.getSpeed(20), 0.01);
        assertEquals(1.34, table.getSpeed(31.5), 0.01);
        // beyond the table the last value is used
        assertEquals(table.getSpeed(40), table.getSpeed(50), 0.001);
        // descents: the same power balance without braking, i.e. limit the speed in the custom model
        assertEquals(30.9, table.getSpeed(-2), 0.1);
        assertEquals(38.8, table.getSpeed(-4), 0.1);
        assertEquals(45.9, table.getSpeed(-6), 0.1);
        assertEquals(55.1, table.getSpeed(-9), 0.1);
        assertEquals(table.getSpeed(-40), table.getSpeed(-50), 0.001);
        assertThrows(IllegalArgumentException.class, () -> new BikeClimbSpeedTable(120, 95, 0, 0.006));
    }

    @Test
    public void testClimbFactor() {
        BikeClimbSpeedTable table = new BikeClimbSpeedTable(120, 95, 0.6, 0.006);
        // a current speed at or above the flat speed (22.14) results in the climb speed of the table
        assertEquals(3.67, 25 * table.getClimbFactor(12, 25), 0.01);
        // a reduced speed is interpreted as a rolling resistance crr * flat/current, i.e. as slope offset
        // 100 * crr * (flat/current - 1) = 0.88 for 9km/h: 12% on a 9km/h track is 3.32km/h instead of 3.67km/h
        assertEquals(3.32, 9 * table.getClimbFactor(12, 9), 0.01);
        assertEquals(table.getSpeed(12.88), 9 * table.getClimbFactor(12, 9), 0.01);
        assertEquals(3.63, 18 * table.getClimbFactor(12, 18), 0.01);
        // the factor never increases the speed above the current speed
        assertEquals(1, table.getClimbFactor(2, 3));
        // but even pushing the bike (3km/h) gets slower on a 12% climb
        assertEquals(2.32, 3 * table.getClimbFactor(12, 3), 0.01);
        // on a descent the gain relative to the flat speed is applied to the current speed
        assertEquals(34.5, 18 * table.getClimbFactor(-5, 18), 0.1);
        assertEquals(17.3, 9 * table.getClimbFactor(-5, 9), 0.1);
    }
}
