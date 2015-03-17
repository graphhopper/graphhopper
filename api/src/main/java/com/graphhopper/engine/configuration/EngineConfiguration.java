package com.graphhopper.engine.configuration;

// Cointains all (and only) the configuration for an HopperEngine
// Usually each HopperClient provides its customized configuration
public abstract class EngineConfiguration {

    private double weightLimit = Double.MAX_VALUE;

    private boolean simplifyResponse;

    // and more options here...

    public double getWeightLimit() {
        return weightLimit;
    }

    public EngineConfiguration setWeightLimit(double weightLimit) {
        this.weightLimit = weightLimit;

        return this;
    }

    public boolean isSimplifyResponse() {
        return simplifyResponse;
    }

    public EngineConfiguration setSimplifyResponse(boolean simplifyResponse) {
        this.simplifyResponse = simplifyResponse;

        return this;
    }
}
