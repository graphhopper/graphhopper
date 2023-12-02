package com.graphhopper.util;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TurnCostsConfig {
    public static final String U_TURN_COSTS = "u_turn_costs";
    public static final int INFINITE_U_TURN_COSTS = -1;
    private TransportationMode transportationMode;
    private boolean restrictions;
    private int uTurnCosts = INFINITE_U_TURN_COSTS;

    // jackson
    TurnCostsConfig() {
    }

    public TurnCostsConfig(TransportationMode tm) {
        this.restrictions = true;
        this.transportationMode = tm;
    }

    public TurnCostsConfig(TransportationMode tm, int uTurnCost) {
        this.restrictions = true;
        this.transportationMode = tm;
        this.uTurnCosts = uTurnCost;
    }

    public TurnCostsConfig(TurnCostsConfig turnCostsConfig) {
        restrictions = turnCostsConfig.restrictions;
        uTurnCosts = turnCostsConfig.uTurnCosts;
        transportationMode = turnCostsConfig.transportationMode;
    }

    public void setTransportationMode(TransportationMode transportationMode) {
        this.transportationMode = transportationMode;
    }

    public TransportationMode getTransportationMode() {
        return transportationMode;
    }

    public boolean isRestrictions() {
        return restrictions;
    }

    public TurnCostsConfig setRestrictions(boolean restrictions) {
        this.restrictions = restrictions;
        return this;
    }

    @JsonProperty("u_turn_costs")
    public int getUTurnCosts() {
        return uTurnCosts;
    }

    public TurnCostsConfig setUTurnCosts(int uTurnCosts) {
        this.uTurnCosts = uTurnCosts;
        return this;
    }
}
