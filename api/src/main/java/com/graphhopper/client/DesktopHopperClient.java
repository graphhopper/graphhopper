package com.graphhopper.client;


import com.graphhopper.HopperEngine;

public class DesktopHopperClient extends AbstractHopperClient {

    public DesktopHopperClient(HopperEngine engine) {
        super(engine);

        // Example
        engine.getConfiguration().setSimplifyResponse(true);
    }
}
