package com.graphhopper.routing.weighting;

public class BikeClimbSpeedTable {

    private static final double G = 9.81;
    private static final double STEP = 0.25; // in percentage
    private static final double INV_STEP = 1.0 / STEP;
    private static final double V_DISMOUNT_KMH = 3.5;

    public static final double DEFAULT_BASE_SPEED = 18; // km/h, the flat speed of the bike profile
    public static final double DEFAULT_CRR = 0.006; // rolling resistance coefficient
    // maximum increase of the rolling resistance derived from a reduced current speed, see getSlopeOffset
    static final double MAX_CRR_INCREASE = 0.012;

    private final double[] tab;   // values in km/h
    private final double walkSlope;
    private final double power, mass, baseSpeed, crr, aero;

    public BikeClimbSpeedTable(double power, double mass) {
        this(power, mass, DEFAULT_BASE_SPEED, DEFAULT_CRR);
    }

    /**
     * This constructor creates a speed table to be used as a fast function inside a custom model.
     *
     * @param power in Watt
     * @param mass in kg
     * @param baseSpeed in km/h
     * @param crr coefficient of rolling resistance (F_roll = crr · m · g)
     */
    public BikeClimbSpeedTable(double power, double mass, double baseSpeed, double crr) {
        if (power <= 0 || mass <= 0 || crr < 0)
            throw new IllegalArgumentException("power > 0, mass > 0, crr >= 0, blend >= 0 expected, got: "
                    + power + ", " + mass + ", " + crr);
        if (baseSpeed <= V_DISMOUNT_KMH)
            throw new IllegalArgumentException("baseSpeed must be > " + V_DISMOUNT_KMH + " km/h, got: " + baseSpeed);

        double v0 = baseSpeed / 3.6;
        double rollingForce = mass * G * crr;
        double aero = (power - rollingForce * v0) / (v0 * v0 * v0);
        if (aero <= 0)
            throw new IllegalArgumentException("Inconsistency: power not sufficient at crr=" + crr
                    + " for baseSpeed=" + baseSpeed + " km/h in flat (aero <= 0)");
        this.power = power;
        this.mass = mass;
        this.baseSpeed = baseSpeed;
        this.crr = crr;
        this.aero = aero;
        double mg100 = mass * G / 100.0;

        // calculate dismount slope (at approx. 12% for bike and 22.7% for racingbike)
        double vd = V_DISMOUNT_KMH / 3.6;
        double dismountSlope = 100.0 * ((power - aero * vd * vd * vd) / (vd * mass * G) - crr);

        // Dismounting for small speed (steep incline) reduces speed by a lot. With this 'blending'
        // we try to avoid fluctuation problems e.g. because of tiny elevation data changes.
        int blendStartIdx = Math.max(1, (int) Math.floor(dismountSlope * INV_STEP));
        double dismountBlendPercent = 2; // 2% slope interval for the blending
        int blendEndIdx = blendStartIdx + (int) Math.ceil(dismountBlendPercent * INV_STEP);
        double blendStartSlope = blendStartIdx * STEP;
        double blendEndSlope = blendEndIdx * STEP;

        this.tab = new double[blendEndIdx + 1];
        this.walkSlope = blendEndSlope;

        for (int i = 0; i <= blendEndIdx; i++) {
            double slope = i * STEP;
            if (i <= blendStartIdx) {
                tab[i] = cyclingSpeedKmh(slope, power, v0, rollingForce, aero, mg100);
            } else if (i < blendEndIdx) {
                double walkInfluence = (slope - blendStartSlope) / (blendEndSlope - blendStartSlope);
                tab[i] = (1 - walkInfluence) * cyclingSpeedKmh(slope, power, v0, rollingForce, aero, mg100)
                        + walkInfluence * walkingSpeedKmh(slope);
            } else {
                tab[i] = walkingSpeedKmh(slope);
            }
        }
    }

    public static double walkingSpeedKmh(double slope) {
        return 80.0 / (20.0 + slope);
    }

    /**
     * Speed of a cyclist on a given gradient, from the power balance
     * {@code power = c * v + aero * v^3}. Solved exactly via Newton iteration; the
     * start value lies on the safe side of this convex function, so convergence
     * is monotone and guaranteed, and 8 iterations converge to machine precision
     *
     * @param slope        gradient in percent, e.g. {@code 5} = 5 %; must be >= 0
     * @param power        rider's power in watts; assumed to be the same power at
     *                     which {@code v0} is ridden on flat ground
     * @param v0           speed on flat ground in m/s (= baseSpeedKmh / 3.6);
     *                     upper bound for the result and part of the start value
     * @param rollingForce mass * 9.81 * rollingResistance
     * @param aero         aerodynamic drag coefficient in kg/m (~ 0.5 * rho * CdA),
     *                     calibrated as (power - rollingForce * v0) / v0^3;
     *                     must be > 0
     * @param m_g_100      mass * 9.81 / 100
     * @return speed in km/h (no dismount handling, pure cycling model)
     */
    private static double cyclingSpeedKmh(double slope, double power, double v0,
                                          double rollingForce, double aero, double m_g_100) {
        double c = rollingForce + m_g_100 * slope;   // total resisting force per m/s [N]
        double v = Math.min(v0, power / c);          // seed: flat speed or drag-free climb speed
        for (int k = 0; k < 8; k++)
            v -= (aero * v * v * v + c * v - power) / (3 * aero * v * v + c);
        return v * 3.6;
    }

    /**
     * @return the factor for 'multiply_by' to reduce the current speed to the climb speed for the
     * specified slope. The resulting speed is the minimum of the current speed (e.g. limited by the
     * surface) and the power-limited climb speed, i.e. the factor is never above 1. A current speed
     * below the base speed additionally increases the rolling resistance, see getSlopeOffset.
     */
    public double getBikeClimbFactor(double slope, double currentSpeed) {
        if (slope < 0 || currentSpeed <= 0) return 1;
        return Math.min(1, speed(slope + getSlopeOffset(currentSpeed)) / currentSpeed);
    }

    /**
     * A current speed below the base speed (e.g. due to a rough surface) is partly interpreted as an
     * increased rolling resistance: the equivalent crr follows from the power balance on the flat at the
     * current speed. The increase is capped as the speed reduction of the profile is mostly a comfort
     * limit and not an energy loss (the comfort limit is handled via the minimum in getBikeClimbFactor).
     * Rolling resistance and slope enter the power balance only via their sum, so the increase is
     * returned as slope offset (in percent) for the table.
     */
    double getSlopeOffset(double currentSpeed) {
        if (currentSpeed >= baseSpeed) return 0;
        double v = currentSpeed / 3.6;
        double crrEq = (power - aero * v * v * v) / (mass * G * v);
        return 100 * Math.min(MAX_CRR_INCREASE, crrEq - crr);
    }

    public double speed(double slope) {
        if (slope < 0)
            throw new IllegalArgumentException("negative slope: " + slope);
        if (slope >= walkSlope)
            return walkingSpeedKmh(slope);
        double t = slope * INV_STEP;
        int i = (int) t;
        return tab[i] + (t - i) * (tab[i + 1] - tab[i]);
    }
}
