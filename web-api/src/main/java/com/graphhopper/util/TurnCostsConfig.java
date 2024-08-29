package com.graphhopper.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TurnCostsConfig {
    public static final int INFINITE_U_TURN_COSTS = -1;
    private double leftCost; // in seconds
    private double rightCost;
    private double straightCost;

    // Define when "left" turn start and end. As "right" turn is symmetric and the negated values are used.
    private double minLeftAngle = 25, maxLeftAngle = 180;

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
        rightCost = copy.rightCost;
        straightCost = copy.straightCost;
        minLeftAngle = copy.minLeftAngle;
        maxLeftAngle = copy.maxLeftAngle;
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

    public void setVehicleTypes(List<String> vehicleTypes) {
        this.vehicleTypes = check(vehicleTypes);
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
        return leftCost != 0 || rightCost != 0 || straightCost != 0;
    }

    public TurnCostsConfig setLeftCost(double leftCost) {
        this.leftCost = leftCost;
        return this;
    }

    @JsonProperty("left")
    public double getLeftCost() {
        return leftCost;
    }

    public TurnCostsConfig setRightCost(double rightCost) {
        this.rightCost = rightCost;
        return this;
    }

    @JsonProperty("right")
    public double getRightCost() {
        return rightCost;
    }

    public TurnCostsConfig setStraightCost(double straightCost) {
        this.straightCost = straightCost;
        return this;
    }

    @JsonProperty("straight")
    public double getStraightCost() {
        return straightCost;
    }

    @JsonProperty("min_left_angle")
    public void setMinLeftAngle(double minLeftAngle) {
        this.minLeftAngle = minLeftAngle;
    }

    public double getMinLeftAngle() {
        return minLeftAngle;
    }

    @JsonProperty("max_left_angle")
    public void setMaxLeftAngle(double maxLeftAngle) {
        this.maxLeftAngle = maxLeftAngle;
    }

    public double getMaxLeftAngle() {
        return maxLeftAngle;
    }

    @Override
    public String toString() {
        return "vehicleTypes=" + vehicleTypes + ", uTurnCosts=" + uTurnCosts
                + ", leftCost=" + leftCost + ", rightCost=" + rightCost + ", straightCost=" + straightCost
                + ", minLeftAngle=" + minLeftAngle + ", maxLeftAngle=" + maxLeftAngle;
    }
}
