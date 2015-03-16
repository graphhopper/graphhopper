package com.graphhopper.client;


import com.graphhopper.HopperClient;
import com.graphhopper.HopperRequest;
import com.graphhopper.HopperResponse;
import com.graphhopper.bean.RouteInstruction;
import com.graphhopper.bean.RoutePoint;
import com.graphhopper.internal.HopperEngine;
import com.graphhopper.routing.Path;

import java.util.ArrayList;
import java.util.List;

// Please do not throw an error if hasError (with check everywhere)
// Throws errors on errors is an anti-pattern
public abstract class AbstractHopperClient implements HopperClient {

    private final HopperEngine engine;
    // We need a TranslationMap too and some other classes here....

    public AbstractHopperClient(HopperEngine engine) {
        this.engine = engine;
        inizializeEngine(engine);
    }

    // All child-classes must provide a customized engine configuration
    protected abstract void inizializeEngine(HopperEngine engine);

    @Override
    public HopperResponse route(HopperRequest request) {
        HopperResponse response = new HopperResponse();
        List<RouteInstruction> instructions = new ArrayList<RouteInstruction>();
        List<RoutePoint> points = new ArrayList<RoutePoint>();

        for(Path path : engine.route(request)) {
            // populating and translating the beans somehow
            // without instantiating InstructionList or PointList (hopefully)
        }

        if(request.isFetchInstructions()) {
            // Example
            response.setInstructions(instructions);
        }

        if(request.isFetchPoints()) {
            // Example
            response.setPoints(points);
        }

        // etc....

        return response;
    }
}
