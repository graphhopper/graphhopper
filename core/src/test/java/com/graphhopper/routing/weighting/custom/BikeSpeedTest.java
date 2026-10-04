package com.graphhopper.routing.weighting.custom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BikeSpeedTest {
    // default riders: bike 100 W, mtb and racingbike 160 W, 90 kg including the bike
    static final BikeSpeed BIKE = new BikeSpeed(100, 90, 0.74, 0.008);

    @Test
    public void speeds() {
        assertEquals(18.68, BIKE.speed(0), 0.01); // observed 18.4 in Copenhagen GPS data
        assertEquals(12.07, BIKE.speed(2), 0.01);
        assertEquals(6.81, BIKE.speed(5), 0.01);
        assertEquals(3.76, BIKE.speed(10), 0.01);
        // steep: the power balance yields walking speeds, the walking floor takes over from 20 % on
        assertEquals(3.18, BIKE.speed(12), 0.01);
        assertEquals(2.0, BIKE.speed(20), 0.01);
        assertEquals(1.41, BIKE.speed(30), 0.01);
        // a weak rider pushes at walking speed where riding would be slower
        assertEquals(2.84, new BikeSpeed(60, 90, 0.74, 0.008).speed(10), 0.01);
        // descents: gravity until braking at 1.75 * flat speed
        assertEquals(26.3, BIKE.speed(-2), 0.1);
        assertEquals(30.0, BIKE.speed(-3), 0.1);
        assertEquals(32.7, BIKE.speed(-5), 0.1);
        assertEquals(32.7, BIKE.speed(-30), 0.1);

        assertEquals(28.25, new BikeSpeed(160, 90, 0.4, 0.006).speed(0), 0.01);
        assertEquals(21.51, new BikeSpeed(160, 90, 0.74, 0.012).speed(0), 0.01);
        // stronger rider: faster everywhere
        BikeSpeed strong = new BikeSpeed(200, 90, 0.74, 0.008);
        assertEquals(24.95, strong.speed(0), 0.01);
        assertEquals(11.18, strong.speed(6), 0.01);
        assertEquals(42.4, strong.speed(-6), 0.1);
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
        // riding on a 12 km/h surface: scaled to the rider on the flat, capped by the power on climbs, the descent speed scaled
        assertEquals(12 * 18.68 / 18, 12 * BIKE.factor(12, 0, 18), 0.01);
        assertEquals(6.81, 12 * BIKE.factor(12, 5, 18), 0.01);
        assertEquals(12 * 32.7 / 18, 12 * BIKE.factor(12, -10, 18), 0.1);
        // pushing: no scaling and no gain, only the power limit
        assertEquals(6, 6 * BIKE.factor(6, -10, 18), 0);
        assertEquals(3.76, 6 * BIKE.factor(6, 10, 18), 0.01);
        assertEquals(1, BIKE.factor(0, 5, 18), 0);
        assertThrows(IllegalArgumentException.class, () -> BIKE.factor(-1, 5, 18));
        assertThrows(IllegalArgumentException.class, () -> BIKE.factor(Double.NaN, 5, 18));
        // bounds: the speed of the slope caps every running speed, so a fast road does not loosen them
        assertEquals(6.81, 30 * BIKE.maxFactor(30, 5, 18), 0.01);
        assertEquals(18.68, 30 * BIKE.maxFactor(30, 0, 18), 0.01);
        assertEquals(32.7, 60 * BIKE.maxFactor(60, -10, 18), 0.1);
        assertEquals(6, 6 * BIKE.maxFactor(6, -10, 18), 0);
        // a weak rider: pushing sections (6 km/h) are faster than the scaled running speed 7 * 14.7 / 18
        assertEquals(6, 7 * new BikeSpeed(60, 90, 0.74, 0.008).maxFactor(7, 0, 18), 1e-9);
        assertEquals(1, BIKE.maxFactor(0, 5, 18), 0);
        assertThrows(IllegalArgumentException.class, () -> BIKE.factor(18, 0, 0));
    }

    @Test
    public void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new BikeSpeed(0, 90, 0.74, 0.008));
        assertThrows(IllegalArgumentException.class, () -> new BikeSpeed(100, 90, 0, 0.008));
        assertThrows(IllegalArgumentException.class, () -> new BikeSpeed(100, 90, 0.74, -0.1));
    }
}
