/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.weighting.custom;

/**
 * Speed of a cyclist on a slope from the power balance
 * <pre>power = (mass * g * (crr + slope / 100)) * v + 0.5 * rho * cda * v^3</pre>
 * i.e. rolling resistance plus gravity plus air drag. On descents the speed is capped at
 * {@link #MAX_SPEED_FACTOR} times the flat speed (braking) and never below the walking speed of a
 * cyclist pushing the bike (0.8 * Tobler), which only matters for weak riders as the power balance of
 * 100 W already yields walking speeds on steep climbs (3 km/h at 12 %, 2 km/h at 20 %).
 * <p>
 * Used by the custom model via multiply_by {@link #factor}, see bike.json.
 */
public class BikeSpeed {
    static final double G = 9.81, RHO = 1.226;
    // riders brake on descents at roughly 1.75 times their flat speed, 32.7 km/h for the default bike rider
    static final double MAX_SPEED_FACTOR = 1.75;
    // covers the ±31.5 % of average_slope, beyond it the speed hardly changes
    static final double MAX_SLOPE = 40, STEP = 0.5;
    // encoded speeds up to this are pushing sections (steps 2, pushing 4, footway and path 6 km/h) and walked
    static final double PUSHING_SPEED = 6;

    private final double flatSpeed;
    private final double[] speeds = new double[(int) Math.round(2 * MAX_SLOPE / STEP) + 1];

    /**
     * @param power in W
     * @param mass  of rider and bike in kg
     * @param cda   drag coefficient times frontal area in m²
     * @param crr   rolling resistance coefficient
     */
    public BikeSpeed(double power, double mass, double cda, double crr) {
        if (!(power > 0) || !(mass > 0) || !(cda > 0) || !(crr >= 0))
            throw new IllegalArgumentException("bike_speed: power, mass and cda must be positive and crr non-negative, but got "
                    + power + ", " + mass + ", " + cda + ", " + crr);
        double aero = 0.5 * RHO * cda, rolling = mass * G * crr, climb = mass * G / 100;
        flatSpeed = solve(power, rolling, aero) * 3.6;
        double maxSpeed = MAX_SPEED_FACTOR * flatSpeed;
        for (int i = 0; i < speeds.length; i++) {
            double slope = -MAX_SLOPE + i * STEP;
            double walking = 0.8 * 6 * Math.exp(-3.5 * Math.abs(slope / 100 + 0.05)); // pushing the bike, 0.8 * Tobler
            speeds[i] = Math.max(Math.min(solve(power, rolling + climb * slope, aero) * 3.6, maxSpeed), walking);
        }
    }

    /**
     * @return the speed in km/h on the slope in %, linearly interpolated and constant beyond ±MAX_SLOPE
     */
    public double speed(double slope) {
        double pos = (Math.max(-MAX_SLOPE, Math.min(MAX_SLOPE, slope)) + MAX_SLOPE) / STEP;
        int i = (int) pos;
        if (i >= speeds.length - 1) return speeds[speeds.length - 1];
        return speeds[i] + (pos - i) * (speeds[i + 1] - speeds[i]);
    }

    /**
     * The factor for the running speed of the custom model: a riding section is scaled to the rider
     * (flat speed relative to baseSpeed, the encoded speed of a flat asphalt road), on descents it gets
     * the speed of the slope relative to baseSpeed instead, and climbs are limited by the power. So a bad
     * surface limits the climb speed but is not multiplied with it. A pushing section is only limited.
     *
     * @param current the running speed in km/h
     */
    public double factor(double current, double slope, double baseSpeed) {
        checkBaseSpeed(baseSpeed);
        if (current == 0) return 1; // blocked edge, CustomWeighting makes it infinite
        double v = speed(slope);
        double scale = current > PUSHING_SPEED ? Math.max(flatSpeed, v) / baseSpeed : 1;
        return Math.min(scale, v / current);
    }

    /**
     * The bound of {@link #factor} for the speed bounds of the custom model: the resulting speed
     * <code>current * factor</code> is <code>min(scale * current, v)</code> for riding and <code>min(current, v)</code>
     * for pushing sections, both increasing in the running speed, so the maximum over all running speeds up to
     * currentMax is at currentMax and at PUSHING_SPEED.
     *
     * @param currentMax the maximum running speed in km/h before this statement
     * @return the factor that currentMax * factor is not exceeded by any running speed up to currentMax
     */
    public double maxFactor(double currentMax, double slope, double baseSpeed) {
        checkBaseSpeed(baseSpeed);
        if (currentMax == 0) return 1; // blocked by a previous statement
        double v = speed(slope);
        if (currentMax <= PUSHING_SPEED) return Math.min(1, v / currentMax);
        return Math.max(Math.min(PUSHING_SPEED, v), Math.min(Math.max(flatSpeed, v) / baseSpeed * currentMax, v)) / currentMax;
    }

    private static void checkBaseSpeed(double baseSpeed) {
        if (!(baseSpeed > 0))
            throw new IllegalArgumentException("bike_speed_factor: base_speed must be positive, but got " + baseSpeed);
    }

    public double getFlatSpeed() {
        return flatSpeed;
    }

    /**
     * @param c the speed-proportional resistance, negative on descents
     * @return the positive root v of aero * v^3 + c * v = power in m/s
     */
    static double solve(double power, double c, double aero) {
        // start right of the root where the cubic is increasing and convex, so Newton descends monotonically to it
        // and converges in at most 6 iterations for any sensible rider
        double v = Math.sqrt(Math.max(0, -c) / aero) + Math.cbrt(power / aero);
        for (int i = 0; i < 20; i++) {
            double next = v - (aero * v * v * v + c * v - power) / (3 * aero * v * v + c);
            if (v - next < 1e-6) return next;
            v = next;
        }
        return v;
    }
}
