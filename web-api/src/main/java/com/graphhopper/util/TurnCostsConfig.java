package com.graphhopper.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TurnCostsConfig {
    public static final int INFINITE_U_TURN_COSTS = -1;
    private double leftTurnCosts; // in seconds
    private double sharpLeftTurnCosts; // in seconds
    private double straightCosts;
    private double rightTurnCosts;
    private double sharpRightTurnCosts;

    // The "right" and "left" turns are symmetric and the negative values are used for "left_turn_costs".
    // From 0 to minTurnAngle no turn costs are added.
    // From minTurnAngle to minSharpTurnAngle the rightTurnCosts (or leftTurnCosts) are added.
    // From minSharpTurnAngle to minUTurnAngle the rightSharpTurnCosts (or leftSharpTurnCosts) are added.
    // And beyond minUTurnAngle the uTurnCosts are added.
    private double minTurnAngle = 25, minSharpTurnAngle = 80, minUTurnAngle = 180;

    private int uTurnCosts = INFINITE_U_TURN_COSTS;
    private List<String> vehicleTypes;
    // ensure that no typos can occur like motor_car vs motorcar or bike vs bicycle
    private static final Set<String> ALL_SUPPORTED = Set.of(
            "agricultural", "atv", "auto_rickshaw",
            "bdouble", "bicycle", "bus", "caravan", "carpool", "coach", "delivery", "destination",
            "emergency", "foot", "golf_cart", "goods", "hazmat", "hgv", "hgv:trailer", "hov",
            "minibus", "mofa", "moped", "motorcar", "motorcycle", "motor_vehicle", "motorhome",
            "nev", "ohv", "psv", "residents",
            "share_taxi", "small_electric_vehicle", "speed_pedelec",
            "taxi", "trailer", "tourist_bus");

    public static TurnCostsConfig car() {
        return new TurnCostsConfig(List.of("motorcar", "motor_vehicle"));
    }

    public static TurnCostsConfig bike() {
        return new TurnCostsConfig(List.of("bicycle"));
    }

    public TurnCostsConfig() {
    }

    public TurnCostsConfig(TurnCostsConfig copy) {
        leftTurnCosts = copy.leftTurnCosts;
        sharpLeftTurnCosts = copy.sharpLeftTurnCosts;
        straightCosts = copy.straightCosts;
        rightTurnCosts = copy.rightTurnCosts;
        sharpRightTurnCosts = copy.sharpRightTurnCosts;
        uTurnCosts = copy.uTurnCosts;

        minTurnAngle = copy.minTurnAngle;
        minSharpTurnAngle = copy.minSharpTurnAngle;
        minUTurnAngle = copy.minUTurnAngle;
        if (copy.vehicleTypes != null)
            vehicleTypes = new ArrayList<>(copy.vehicleTypes);
    }

    public TurnCostsConfig(List<String> vehicleTypes) {
        this.vehicleTypes = check(vehicleTypes);
    }

    public TurnCostsConfig(List<String> vehicleTypes, int uTurnCost) {
        this.vehicleTypes = check(vehicleTypes);
        this.uTurnCosts = uTurnCost;
    }

    List<String> check(List<String> restrictions) {
        if (restrictions == null || restrictions.isEmpty())
            throw new IllegalArgumentException("turn_costs cannot have empty vehicle_types");
        for (String r : restrictions) {
            if (!ALL_SUPPORTED.contains(r))
                throw new IllegalArgumentException("Currently we do not support the restriction: " + r);
        }
        return restrictions;
    }

    public TurnCostsConfig setVehicleTypes(List<String> vehicleTypes) {
        this.vehicleTypes = check(vehicleTypes);
        return this;
    }

    @JsonProperty("vehicle_types")
    public List<String> getVehicleTypes() {
        check(vehicleTypes);
        return vehicleTypes;
    }

    /**
     * @param uTurnCosts the costs of an u-turn in seconds, for {@link TurnCostsConfig#INFINITE_U_TURN_COSTS}
     *                   the u-turn costs will be infinite
     */
    public TurnCostsConfig setUTurnCosts(int uTurnCosts) {
        this.uTurnCosts = uTurnCosts;
        return this;
    }

    @JsonProperty("u_turn_costs")
    public int getUTurnCosts() {
        return uTurnCosts;
    }

    public boolean hasLeftRightStraightCosts() {
        return leftTurnCosts != 0 || sharpLeftTurnCosts != 0 || straightCosts != 0 || rightTurnCosts != 0 || sharpRightTurnCosts != 0;
    }

    public TurnCostsConfig setLeftTurnCosts(double leftTurnCosts) {
        this.leftTurnCosts = leftTurnCosts;
        return this;
    }

    @JsonProperty("left_turn_costs")
    public double getLeftTurnCosts() {
        return leftTurnCosts;
    }

    public TurnCostsConfig setSharpLeftTurnCosts(double sharpLeftTurnCosts) {
        this.sharpLeftTurnCosts = sharpLeftTurnCosts;
        return this;
    }

    @JsonProperty("sharp_left_turn_costs")
    public double getSharpLeftTurnCosts() {
        return sharpLeftTurnCosts;
    }

    public TurnCostsConfig setRightTurnCosts(double rightTurnCosts) {
        this.rightTurnCosts = rightTurnCosts;
        return this;
    }

    @JsonProperty("right_turn_costs")
    public double getRightTurnCosts() {
        return rightTurnCosts;
    }

    public TurnCostsConfig setSharpRightTurnCosts(double sharpRightTurnCosts) {
        this.sharpRightTurnCosts = sharpRightTurnCosts;
        return this;
    }

    @JsonProperty("sharp_right_turn_costs")
    public double getSharpRightTurnCosts() {
        return sharpRightTurnCosts;
    }

    public TurnCostsConfig setStraightCosts(double straightCosts) {
        this.straightCosts = straightCosts;
        return this;
    }

    @JsonProperty("straight_costs")
    public double getStraightCosts() {
        return straightCosts;
    }

    @JsonProperty("min_turn_angle")
    public TurnCostsConfig setMinTurnAngle(double minTurnAngle) {
        this.minTurnAngle = minTurnAngle;
        return this;
    }

    public double getMinTurnAngle() {
        return minTurnAngle;
    }

    @JsonProperty("min_sharp_turn_angle")
    public TurnCostsConfig setMinSharpTurnAngle(double minSharpTurnAngle) {
        this.minSharpTurnAngle = minSharpTurnAngle;
        return this;
    }

    public double getMinSharpTurnAngle() {
        return minSharpTurnAngle;
    }

    @JsonProperty("min_u_turn_angle")
    public TurnCostsConfig setMinUTurnAngle(double minUTurnAngle) {
        this.minUTurnAngle = minUTurnAngle;
        return this;
    }

    public double getMinUTurnAngle() {
        return minUTurnAngle;
    }

    @Override
    public String toString() {
        return "leftTurnCosts=" + leftTurnCosts + ", sharpLeftTurnCosts=" + sharpLeftTurnCosts
                + ", straightCosts=" + straightCosts
                + ", rightTurnCosts=" + rightTurnCosts + ", sharpRightTurnCosts=" + sharpRightTurnCosts
                + ", minTurnAngle=" + minTurnAngle
                + ", minSharpTurnAngle=" + minSharpTurnAngle
                + ", minUTurnAngle=" + minUTurnAngle
                + ", uTurnCosts=" + uTurnCosts + ", vehicleTypes=" + vehicleTypes;
    }
}
