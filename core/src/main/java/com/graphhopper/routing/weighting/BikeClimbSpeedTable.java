package com.graphhopper.routing.weighting;

public class BikeClimbSpeedTable {

    private static final double STEP = 0.25; // in percentage
    private static final double INV_STEP = 1.0 / STEP;
    private static final double V_DISMOUNT_KMH = 3.5;

    private final double[] tab;   // values in km/h
    // the table covers the slopes from -MAX_SLOPE to MAX_SLOPE (in percent), beyond the first or last entry is
    // used. The maximum of average_slope is 31.5 plus the slope offset of getClimbFactor
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

        // Below V_DISMOUNT_KMH the cyclist cannot balance and pushes the bike instead (at approx. 12% for the bike
        // and 22.7% for the racingbike). Blend from riding to pushing over a 2% slope interval to avoid
        // fluctuations, e.g. from tiny elevation data changes.
        double vd = V_DISMOUNT_KMH / 3.6;
        double dismountSlope = 100.0 * ((power - aero * vd * vd * vd) / (vd * mass * 9.81) - crr);

        // descents: the power model ignores braking, so limit the resulting speed in the custom model
        this.tab = new double[2 * OFFSET + 1];
        for (int i = 0; i < tab.length; i++) {
            double slope = (i - OFFSET) * STEP;
            double walkInfluence = Math.min(1, Math.max(0, (slope - dismountSlope) / 2));
            tab[i] = (1 - walkInfluence) * cyclingSpeedKmh(slope, power, rollingForce, aero, m_g_100)
                    + walkInfluence * walkingSpeedKmh(slope);
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
     * current speed below the flat speed (e.g. rough surface) is additionally interpreted as a higher
     * rolling resistance crr * flatSpeed / currentSpeed - only partly, as the reduced speed is mostly a
     * comfort limit (see the minimum) and not an energy loss. As crr and slope/100 appear only as sum
     * in the power balance, this is a slope offset in percent, which allows to reuse the table.
     * For a descent the factor is the speed gain relative to the flat speed, i.e. a surface-limited
     * current speed increases proportionally.
     */
    public double getClimbFactor(double slope, double currentSpeed) {
        if (currentSpeed <= 0) return 1;
        if (slope < 0) return getSpeed(slope) / flatSpeed;
        double slopeOffset = 100 * crr * Math.max(0, flatSpeed / currentSpeed - 1);
        return Math.min(1, getSpeed(slope + slopeOffset) / currentSpeed);
    }

    /**
     * @return the speed on flat ground in km/h that follows from the power balance
     */
    public double getFlatSpeed() {
        return flatSpeed;
    }

    public double getSpeed(double slope) {
        if (slope <= -MAX_SLOPE) return tab[0];
        if (slope >= MAX_SLOPE) return tab[tab.length - 1];
        double t = (slope + MAX_SLOPE) * INV_STEP;
        int idx = (int) t;
        return tab[idx] + (t - idx) * (tab[idx + 1] - tab[idx]); // interpolate
    }
}
