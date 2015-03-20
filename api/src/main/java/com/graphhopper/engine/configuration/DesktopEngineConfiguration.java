package com.graphhopper.engine.configuration;


public class DesktopEngineConfiguration extends EngineConfiguration {

    public DesktopEngineConfiguration() {
        setSimplifyResponse(false);
        setWeightLimit(2);
    }
}
