package com.graphhopper.routing.weighting;

public class BikeClimbSpeedTable {

    private static final double STEP = 0.25; // in percentage
    private static final double INV_STEP = 1.0 / STEP;
    private static final double V_DISMOUNT_KMH = 3.5;

    // maximum factor for the rolling resistance derived from a reduced current speed, see getSlopeOffset
    static final double MAX_CRR_FACTOR = 3;

    private final double[] tab;   // values in km/h
    // the table covers the slopes from -MAX_SLOPE to MAX_SLOPE (in percent), beyond the first or last entry is
    // used. The maximum of average_slope is 31.5 plus the slope offset of at most 100 * crr * (MAX_CRR_FACTOR - 1),
    // see getSlopeOffset
    private static final double MAX_SLOPE = 40;
    private static final int OFFSET = (int) (MAX_SLOPE * INV_STEP); // index of slope 0
    private final double flatSpeed, crr; // the flat speed in km/h follows from the power balance

    /**
     * This constructor creates a speed table to be later used via getSpeed as a fast function inside a custom model.
     * <p>
     * The air resistance is F_air = 0.5 * rho * cda * v^2 and its power P_air = 0.5 * rho * cda * v^3, with the
     * air density rho (about 1.2 kg/m^3). cda is the drag coefficient Cd (dimensionless, how streamlined the shape
     * is, about 0.9 to 1.1 for a cyclist) times the frontal area A (about 0.4 to 0.6 m^2 depending on the posture).
     * As only the product can be measured, wind tunnel and field tests report cda directly.
     *
     * @param power in Watt
     * @param mass in kg
     * @param cda drag area in m^2, e.g. 0.6 for an upright cyclist and 0.4 on a racingbike
     * @param crr coefficient of rolling resistance (F_roll = crr * m * g)
     */
    public BikeClimbSpeedTable(double power, double mass, double cda, double crr) {
        if (power <= 0 || mass <= 0 || cda <= 0 || crr < 0)
            throw new IllegalArgumentException("power > 0, mass > 0, cda > 0, crr >= 0 expected, got: "
                    + power + ", " + mass + ", " + cda + ", " + crr);

        double rollingForce = mass * 9.81 * crr;
        double aero = 0.5 * 1.226 * cda; // 0.5 * air density * cda
        double m_g_100 = mass * 9.81 / 100.0;
        this.flatSpeed = cyclingSpeedKmh(0, power, rollingForce, aero, m_g_100);
        this.crr = crr;

        // calculate dismount slope (at approx. 12% for bike and 22.7% for racingbike)
        double vd = V_DISMOUNT_KMH / 3.6;
        double dismountSlope = 100.0 * ((power - aero * vd * vd * vd) / (vd * mass * 9.81) - crr);

        // Dismounting for small speed (steep incline) reduces speed by a lot. With this 'blending'
        // we try to avoid fluctuation problems e.g. because of tiny elevation data changes.
        int blendStartIdx = Math.max(1, (int) Math.floor(dismountSlope * INV_STEP));
        double dismountBlendPercent = 2; // 2% slope interval for the blending from biking (at high slopes) to pushing the bike (at even higher slopes)
        int blendEndIdx = blendStartIdx + (int) Math.ceil(dismountBlendPercent * INV_STEP);
        double blendStartSlope = blendStartIdx * STEP;
        double blendEndSlope = blendEndIdx * STEP;

        this.tab = new double[2 * OFFSET + 1];
        // descents: the power model ignores braking, so limit the resulting speed in the custom model
        for (int i = 0; i < OFFSET; i++)
            tab[i] = cyclingSpeedKmh((i - OFFSET) * STEP, power, rollingForce, aero, m_g_100);
        for (int i = 0; i <= OFFSET; i++) {
            double slope = i * STEP;
            if (i <= blendStartIdx) {
                tab[OFFSET + i] = cyclingSpeedKmh(slope, power, rollingForce, aero, m_g_100);
            } else if (i < blendEndIdx) {
                double walkInfluence = (slope - blendStartSlope) / (blendEndSlope - blendStartSlope);
                tab[OFFSET + i] = (1 - walkInfluence) * cyclingSpeedKmh(slope, power, rollingForce, aero, m_g_100)
                        + walkInfluence * walkingSpeedKmh(slope);
            } else {
                tab[OFFSET + i] = walkingSpeedKmh(slope);
            }
        }
    }

    /**
     * High precision calculation for when the table values are created (not called per edge).
     *
     * @return the speed of the cyclist pushing the bike, i.e. 0.8 times Tobler's hiking function.
     */
    private static double walkingSpeedKmh(double slope) {
        return 0.8 * 6 * Math.exp(-3.5 * (slope / 100 + 0.05));
    }

    /**
     * Calculates the speed of a cyclist on a given gradient, from the power balance
     * {@code power = c * v + aero * v^3}. Solved via Newton iteration from a start value beyond
     * the root, where this convex function increases, so convergence is monotone and guaranteed,
     * and 8 iterations converge to machine precision.
     *
     * @param slope        gradient in percent, e.g. {@code 5} = 5 %, negative for descents
     * @param power        rider's power in watts
     * @param rollingForce mass * 9.81 * rollingResistance
     * @param aero         aerodynamic drag coefficient in kg/m (0.5 * rho * cda), must be > 0
     * @param m_g_100      mass * 9.81 / 100
     * @return speed in km/h (no dismount handling, pure cycling model)
     */
    private static double cyclingSpeedKmh(double slope, double power, double rollingForce, double aero, double m_g_100) {
        double c = rollingForce + m_g_100 * slope;   // total resisting force per m/s [N], negative for descents
        // start value: the drag-only speed, for descents (c < 0) plus the coasting speed
        double v = Math.sqrt(Math.max(0, -c) / aero) + Math.cbrt(power / aero);
        for (int k = 0; k < 8; k++)
            v -= (aero * v * v * v + c * v - power) / (3 * aero * v * v + c);
        return v * 3.6;
    }

    /**
     * @return the factor for 'multiply_by' to change the current speed to the climb or descent speed for
     * the specified slope. For a climb the resulting speed is the minimum of the current speed (e.g.
     * limited by the surface) and the power-limited climb speed, i.e. the factor is never above 1. A
     * current speed below the flat speed additionally increases the rolling resistance, see getSlopeOffset.
     * For a descent the factor is the speed gain relative to the flat speed, i.e. a surface-limited
     * current speed increases proportionally.
     */
    public double getClimbFactor(double slope, double currentSpeed) {
        if (currentSpeed <= 0) return 1;
        if (slope < 0) return getSpeed(slope) / flatSpeed;
        return Math.min(1, getSpeed(slope + getSlopeOffset(currentSpeed)) / currentSpeed);
    }

    /**
     * @return the speed on flat ground in km/h that follows from the power balance
     */
    public double getFlatSpeed() {
        return flatSpeed;
    }

    /**
     * A current speed below the flat speed (e.g. rough surface) is interpreted as a higher rolling
     * resistance crr * min(MAX_CRR_FACTOR, flatSpeed / currentSpeed) - only partly, as the reduced speed
     * is mostly a comfort limit (see the minimum in getClimbFactor) and not an energy loss. As crr
     * and slope/100 appear only as sum in the power balance power = aero * v^3 + m * g * (crr + slope/100) * v,
     * the increase is returned as slope offset in percent, which allows to reuse the table (calibrated with crr).
     */
    double getSlopeOffset(double currentSpeed) {
        if (currentSpeed >= flatSpeed) return 0;
        return 100 * crr * (Math.min(MAX_CRR_FACTOR, flatSpeed / currentSpeed) - 1);
    }

    public double getSpeed(double slope) {
        if (slope <= -MAX_SLOPE) return tab[0];
        if (slope >= MAX_SLOPE) return tab[tab.length - 1];
        double t = (slope + MAX_SLOPE) * INV_STEP;
        int idx = (int) t;
        return tab[idx] + (t - idx) * (tab[idx + 1] - tab[idx]); // interpolate
    }
}
