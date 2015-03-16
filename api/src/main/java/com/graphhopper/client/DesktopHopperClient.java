package com.graphhopper.client;


import com.graphhopper.internal.HopperEngine;
import com.graphhopper.internal.HopperEngineConfiguration;

public class DesktopHopperClient extends AbstractHopperClient {

    private final HopperEngineConfiguration configuration = new HopperEngineConfiguration()
            .setSimplifyResponse(true)
            .setWeightLimit(3);

    public DesktopHopperClient(HopperEngine engine) {
        super(engine);
    }

    @Override
    protected HopperEngineConfiguration getConfiguration() {
        return configuration;
    }


}
