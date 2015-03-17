package com.graphhopper.engine.configuration;

// Cointains all (and only) the configuration for an HopperEngine
public abstract class EngineConfiguration {

    private String graphLocation = "";

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

    public String getGraphLocation() {
        return graphLocation;
    }

    public EngineConfiguration setGraphLocation(String graphLocation) {
        this.graphLocation = graphLocation;
        return this;
    }
}
