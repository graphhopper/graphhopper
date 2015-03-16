package com.graphhopper.internal;

// Cointains all (and only) the configuration for an HopperEngine
// Usually each HopperClient provides its customized configuration
public class HopperEngineConfiguration {

    private double weightLimit = Double.MAX_VALUE;

    private boolean simplifyResponse;

    // and more options here...

    public double getWeightLimit() {
        return weightLimit;
    }

    public HopperEngineConfiguration setWeightLimit(double weightLimit) {
        this.weightLimit = weightLimit;

        return this;
    }

    public boolean isSimplifyResponse() {
        return simplifyResponse;
    }

    public HopperEngineConfiguration setSimplifyResponse(boolean simplifyResponse) {
        this.simplifyResponse = simplifyResponse;

        return this;
    }
}
