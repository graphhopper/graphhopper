package com.graphhopper.client;


import com.graphhopper.internal.HopperEngine;
import com.graphhopper.internal.HopperEngineConfiguration;

public class ServerHopperClient extends AbstractHopperClient {

    public ServerHopperClient(HopperEngine engine) {
        super(engine);
    }

    @Override
    protected HopperEngineConfiguration getConfiguration() {
        return null;// see DesktopHopperClient for an example
    }
}
