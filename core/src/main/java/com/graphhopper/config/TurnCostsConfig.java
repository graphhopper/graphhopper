package com.graphhopper.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

public class TurnCostsConfig {
    public static final int INFINITE_U_TURN_COSTS = -1;
    private int uTurnCosts = INFINITE_U_TURN_COSTS;
    private List<String> restrictions;
    // ensure that no typos can occur like motor_car vs motorcar or bike vs bicycle
    private static final Set<String> ALL_SUPPORTED = Set.of(
            "agricultural", "atv", "auto_rickshaw",
            "bdouble", "bicycle", "bus", "caravan", "carpool", "coach",
            "emergency", "foot", "golf_cart", "goods", "hazmat", "hgv", "hov",
            "minibus", "mofa", "moped", "motorcar", "motorcycle", "motor_vehicle", "motorhome",
            "nev", "ohv", "psv", "share_taxi", "small_electric_vehicle", "speed_pedelec",
            "taxi", "trailer", "tourist_bus");

    // jackson
    public TurnCostsConfig() {
    }

    public TurnCostsConfig(List<String> restrictions) {
        this.restrictions = check(restrictions);
    }

    public TurnCostsConfig(List<String> restrictions, int uTurnCost) {
        this.restrictions = check(restrictions);
        this.uTurnCosts = uTurnCost;
    }

    public void setRestrictions(List<String> restrictions) {
        this.restrictions = check(restrictions);
    }

    List<String> check(List<String> restrictions) {
        for (String r : restrictions) {
            if (!ALL_SUPPORTED.contains(r))
                throw new IllegalArgumentException("Currently we do not support the restriction: " + r);
        }
        return restrictions;
    }

    public List<String> getRestrictions() {
        return restrictions;
    }

    public TurnCostsConfig setUTurnCosts(int uTurnCosts) {
        this.uTurnCosts = uTurnCosts;
        return this;
    }

    @JsonProperty("u_turn_costs")
    public int getUTurnCosts() {
        return uTurnCosts;
    }

    @Override
    public String toString() {
        return "restrictions=" + restrictions + ", uTurnCosts=" + uTurnCosts;
    }
}
