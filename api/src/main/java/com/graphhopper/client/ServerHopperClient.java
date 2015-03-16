package com.graphhopper.client;


import com.graphhopper.internal.HopperEngine;
import com.graphhopper.internal.HopperEngineConfiguration;

public class ServerHopperClient extends AbstractHopperClient {

    public ServerHopperClient(HopperEngine engine) {
        super(engine);
    }

    @Override
    protected void inizializeEngine(HopperEngine engine) {
        // Inizializes the hopper with custom configuration
        engine.inizialize(new HopperEngineConfiguration().setSimplifyResponse(true).setWeightLimit(3));
    }
}
