package com.graphhopper.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TurnCostsConfig {
    public static final int INFINITE_U_TURN_COSTS = -1;
    private double leftCost; // in seconds
    private double leftSharpCost; // in seconds
    private double straightCost;
    private double rightCost;
    private double rightSharpCost;

    // As "right" and "left" turns are symmetric and for "right" the negated values are used.
    // From 0 to minAngle no turn cost is added.
    // From minAngle to minSharpAngle the turn cost leftCost or rightCost is added.
    // From minSharpAngle to maxAngle the turn cost leftSharpCost or rightSharpCost is added.
    private double minAngle = 25, minSharpAngle = 110, maxAngle = 180;

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
        leftCost = copy.leftCost;
        leftSharpCost = copy.leftSharpCost;
        straightCost = copy.straightCost;
        rightCost = copy.rightCost;
        rightSharpCost = copy.rightSharpCost;

        minAngle = copy.minAngle;
        minSharpAngle = copy.minSharpAngle;
        maxAngle = copy.maxAngle;
        uTurnCosts = copy.uTurnCosts;
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

    public TurnCostsConfig setVehicleTypes(List<String> vehicleTypes) {
        this.vehicleTypes = check(vehicleTypes);
        return this;
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

    @JsonProperty("vehicle_types")
    public List<String> getVehicleTypes() {
        check(vehicleTypes);
        return vehicleTypes;
    }

    /**
     * @param uTurnCosts the costs of a u-turn in seconds, for {@link TurnCostsConfig#INFINITE_U_TURN_COSTS} the u-turn costs
     *                   will be infinite
     */
    public TurnCostsConfig setUTurnCosts(int uTurnCosts) {
        this.uTurnCosts = uTurnCosts;
        return this;
    }

    @JsonProperty("u_turn_costs")
    public int getUTurnCosts() {
        return uTurnCosts;
    }

    public boolean hasLeftRightStraight() {
        return leftCost != 0 || leftSharpCost != 0 || straightCost != 0 || rightCost != 0 || rightSharpCost != 0;
    }

    public TurnCostsConfig setLeftCost(double leftCost) {
        this.leftCost = leftCost;
        return this;
    }

    @JsonProperty("left")
    public double getLeftCost() {
        return leftCost;
    }

    public TurnCostsConfig setLeftSharpCost(double leftSharpCost) {
        this.leftSharpCost = leftSharpCost;
        return this;
    }

    @JsonProperty("left_sharp")
    public double getLeftSharpCost() {
        return leftSharpCost;
    }

    public TurnCostsConfig setRightCost(double rightCost) {
        this.rightCost = rightCost;
        return this;
    }

    @JsonProperty("right")
    public double getRightCost() {
        return rightCost;
    }

    public TurnCostsConfig setRightSharpCost(double rightSharpCost) {
        this.rightSharpCost = rightSharpCost;
        return this;
    }

    @JsonProperty("right_sharp")
    public double getRightSharpCost() {
        return rightSharpCost;
    }

    public TurnCostsConfig setStraightCost(double straightCost) {
        this.straightCost = straightCost;
        return this;
    }

    @JsonProperty("straight")
    public double getStraightCost() {
        return straightCost;
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

    @JsonProperty("max_angle")
    public TurnCostsConfig setMaxAngle(double maxAngle) {
        this.maxAngle = maxAngle;
        return this;
    }

    public double getMaxAngle() {
        return maxAngle;
    }

    @Override
    public String toString() {
        return "left=" + leftCost + ", leftSharp=" + leftSharpCost
                + ", straight=" + straightCost
                + ", right=" + rightCost + ", rightSharp=" + rightSharpCost
                + ", minAngle=" + minAngle + ", minSharpAngle=" + minSharpAngle + ", maxAngle=" + maxAngle
                + ", uTurnCosts=" + uTurnCosts + ", vehicleTypes=" + vehicleTypes;
    }
}
