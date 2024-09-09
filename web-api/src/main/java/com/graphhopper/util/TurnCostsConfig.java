package com.graphhopper.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TurnCostsConfig {
    public static final int INFINITE_U_TURN_COSTS = -1;
    private double leftCosts; // in seconds
    private double leftSharpCosts; // in seconds
    private double straightCosts;
    private double rightCosts;
    private double rightSharpCosts;

    // The "right" and "left" turns are symmetric and the negative values are used for "left".
    // From 0 to minAngle no turn costs are added.
    // From minAngle to minSharpAngle the rightCosts (or leftCosts) are added.
    // From minSharpAngle to minUTurnAngle the rightSharpCosts (or leftSharpCosts) are added.
    // And beyond minUTurnAngle the uTurnCosts are added.
    private double minAngle = 25, minSharpAngle = 80, minUTurnAngle = 180;

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
        leftCosts = copy.leftCosts;
        leftSharpCosts = copy.leftSharpCosts;
        straightCosts = copy.straightCosts;
        rightCosts = copy.rightCosts;
        rightSharpCosts = copy.rightSharpCosts;
        uTurnCosts = copy.uTurnCosts;

        minAngle = copy.minAngle;
        minSharpAngle = copy.minSharpAngle;
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
        return leftCosts != 0 || leftSharpCosts != 0 || straightCosts != 0 || rightCosts != 0 || rightSharpCosts != 0;
    }

    public TurnCostsConfig setLeftCosts(double leftCosts) {
        this.leftCosts = leftCosts;
        return this;
    }

    @JsonProperty("left_costs")
    public double getLeftCosts() {
        return leftCosts;
    }

    public TurnCostsConfig setLeftSharpCosts(double leftSharpCosts) {
        this.leftSharpCosts = leftSharpCosts;
        return this;
    }

    @JsonProperty("left_sharp_costs")
    public double getLeftSharpCosts() {
        return leftSharpCosts;
    }

    public TurnCostsConfig setRightCosts(double rightCosts) {
        this.rightCosts = rightCosts;
        return this;
    }

    @JsonProperty("right_costs")
    public double getRightCosts() {
        return rightCosts;
    }

    public TurnCostsConfig setRightSharpCosts(double rightSharpCosts) {
        this.rightSharpCosts = rightSharpCosts;
        return this;
    }

    @JsonProperty("right_sharp_costs")
    public double getRightSharpCosts() {
        return rightSharpCosts;
    }

    public TurnCostsConfig setStraightCosts(double straightCosts) {
        this.straightCosts = straightCosts;
        return this;
    }

    @JsonProperty("straight_costs")
    public double getStraightCosts() {
        return straightCosts;
    }

    @JsonProperty("min_angle")
    public TurnCostsConfig setMinAngle(double minAngle) {
        this.minAngle = minAngle;
        return this;
    }

    public double getMinAngle() {
        return minAngle;
    }

    @JsonProperty("min_sharp_angle")
    public TurnCostsConfig setMinSharpAngle(double minSharpAngle) {
        this.minSharpAngle = minSharpAngle;
        return this;
    }

    public double getMinSharpAngle() {
        return minSharpAngle;
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
        return "left=" + leftCosts + ", leftSharp=" + leftSharpCosts
                + ", straight=" + straightCosts
                + ", right=" + rightCosts + ", rightSharp=" + rightSharpCosts
                + ", minAngle=" + minAngle + ", minSharpAngle=" + minSharpAngle + ", minUTurnAngle=" + minUTurnAngle
                + ", uTurnCosts=" + uTurnCosts + ", vehicleTypes=" + vehicleTypes;
    }
}
