package com.graphhopper.client;


import com.graphhopper.HopperClient;
import com.graphhopper.HopperRequest;
import com.graphhopper.HopperResponse;
import com.graphhopper.bean.RouteInstruction;
import com.graphhopper.bean.RoutePoint;
import com.graphhopper.internal.HopperEngine;
import com.graphhopper.internal.HopperEngineConfiguration;
import com.graphhopper.routing.Path;
import com.graphhopper.util.Translation;

import java.util.ArrayList;
import java.util.List;


public abstract class AbstractHopperClient implements HopperClient {

    private final HopperEngine engine;
    private Translation translation;
    // We need a TranslationMap too and some other classes here....

    public AbstractHopperClient(HopperEngine engine) {
        this.engine = engine;
        engine.inizialize(getConfiguration());
    }

    // All child-classes must provide a customized engine configuration
    protected abstract HopperEngineConfiguration getConfiguration();

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
