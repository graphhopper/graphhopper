package com.graphhopper.engine.configuration;


public class MobileEngineConfiguration extends EngineConfiguration {

    public MobileEngineConfiguration() {
        setSimplifyResponse(true);
        setWeightLimit(2);
        // ...
    }
}
