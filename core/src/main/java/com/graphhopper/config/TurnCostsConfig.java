package com.graphhopper.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TurnCostsConfig {
    public static final int INFINITE_U_TURN_COSTS = -1;
    private int uTurnCosts = INFINITE_U_TURN_COSTS;
    private String restriction;

    // jackson
    public TurnCostsConfig() {
    }

    public TurnCostsConfig(String restriction) {
        this.restriction = restriction;
    }

    public TurnCostsConfig(String restriction, int uTurnCost) {
        this.restriction = restriction;
        this.uTurnCosts = uTurnCost;
    }

    public void setRestriction(String restriction) {
        this.restriction = restriction;
    }

    public String getRestriction() {
        return restriction;
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
        return "restriction=" + restriction + ", uTurnCosts=" + uTurnCosts;
    }
}
