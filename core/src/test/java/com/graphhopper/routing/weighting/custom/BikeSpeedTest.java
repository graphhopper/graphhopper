package com.graphhopper.routing.weighting.custom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BikeSpeedTest {
    // default riders: bike 100 W, mtb and racingbike 160 W, 90 kg including the bike
    static final BikeSpeed BIKE = new BikeSpeed(100, 90, 0.74, 0.008);

    static double kmh(BikeSpeed bs, double slope, double base) {
        return bs.speed(slope);
    }

    @Test
    public void speeds() {
        assertEquals(18.68, kmh(BIKE, 0, 18), 0.01); // observed 18.4 in Copenhagen GPS data
        assertEquals(12.07, kmh(BIKE, 2, 18), 0.01);
        assertEquals(6.81, kmh(BIKE, 5, 18), 0.01);
        assertEquals(3.76, kmh(BIKE, 10, 18), 0.01);
        // steep: the power balance yields walking speeds, so no extra pushing model
        assertEquals(3.18, kmh(BIKE, 12, 18), 0.01);
        assertEquals(1.96, kmh(BIKE, 20, 18), 0.01);
        assertEquals(1.33, kmh(BIKE, 30, 18), 0.01);
        // descents: gravity until braking at 1.75 * flat speed
        assertEquals(26.3, kmh(BIKE, -2, 18), 0.1);
        assertEquals(30.0, kmh(BIKE, -3, 18), 0.1);
        assertEquals(32.7, kmh(BIKE, -5, 18), 0.1);
        assertEquals(32.7, kmh(BIKE, -30, 18), 0.1);

        assertEquals(28.25, kmh(new BikeSpeed(160, 90, 0.4, 0.006), 0, 1), 0.01);
        assertEquals(21.51, kmh(new BikeSpeed(160, 90, 0.74, 0.012), 0, 1), 0.01);
        // stronger rider: faster everywhere
        BikeSpeed strong = new BikeSpeed(200, 90, 0.74, 0.008);
        assertEquals(24.95, kmh(strong, 0, 18), 0.01);
        assertEquals(11.18, kmh(strong, 6, 18), 0.01);
        assertEquals(42.4, kmh(strong, -6, 18), 0.1);
    }

    @Test
    public void speedIsInterpolatedAndClamped() {
        assertEquals(BIKE.speed(0.25), (BIKE.speed(0) + BIKE.speed(0.5)) / 2, 1e-9);
        assertEquals(BIKE.speed(40), BIKE.speed(100), 0);
        assertEquals(BIKE.speed(-40), BIKE.speed(-100), 0);
        assertEquals(BIKE.speed(40), BIKE.speed(39.9), 0.01);
        // monotone
        for (double s = -40; s < 40; s += 0.1)
            assertTrue(BIKE.speed(s) >= BIKE.speed(s + 0.1), "slope " + s);
    }

    @Test
    public void factor() {
        // riding: flat speed relative to the base speed, limited by the power on climbs, the descent speed on descents
        assertEquals(18.68, 18 * BIKE.factor(18, 0, 18), 0.01);
        assertEquals(12.46, 12 * BIKE.factor(12, 0, 18), 0.01);
        assertEquals(6.81, 18 * BIKE.factor(18, 5, 18), 0.01);
        assertEquals(6.81, 12 * BIKE.factor(12, 5, 18), 0.01);
        assertEquals(26.3, 18 * BIKE.factor(18, -2, 18), 0.1);
        assertEquals(32.7 * 12 / 18, 12 * BIKE.factor(12, -10, 18), 0.1);
        // pushing: no scaling and no gain, only the power limit
        assertEquals(6, 6 * BIKE.factor(6, 0, 18), 0);
        assertEquals(6, 6 * BIKE.factor(6, -10, 18), 0);
        assertEquals(3.76, 6 * BIKE.factor(6, 10, 18), 0.01);
        assertEquals(2, 2 * BIKE.factor(2, 12, 18), 0);
        // bounds: the maximum over all current speeds
        assertEquals(18.68 / 18, BIKE.factor(Double.NaN, 5, 18), 0.001);
        assertEquals(32.7 / 18, BIKE.factor(Double.NaN, -10, 18), 0.01);
        assertEquals(1, new BikeSpeed(60, 90, 0.74, 0.008).factor(Double.NaN, 5, 18), 0);
        assertEquals(1, BIKE.factor(0, 5, 18), 0);
        assertThrows(IllegalArgumentException.class, () -> BIKE.factor(18, 0, 0));
    }

    @Test
    public void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new BikeSpeed(0, 90, 0.74, 0.008));
        assertThrows(IllegalArgumentException.class, () -> new BikeSpeed(100, 90, 0, 0.008));
        assertThrows(IllegalArgumentException.class, () -> new BikeSpeed(100, 90, 0.74, -0.1));
    }
}
