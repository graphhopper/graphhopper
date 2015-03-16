package com.graphhopper.internal;

import com.graphhopper.HopperRequest;
import com.graphhopper.routing.Path;

import java.util.ArrayList;
import java.util.List;

// All the "internal" low-API goes here
// This class should containts the "import" logic only
public class HopperEngine {

    private String osmFile;

    public HopperEngine(String osmFile) {
        this.osmFile = osmFile;
    }

    public HopperEngine inizialize(HopperEngineConfiguration configuration) {
        // Process configuration and inizialize the graph (load, or get the cached value etc...)
        // Usually called automatically from the *HopperClient construction and not by the user

        return this;
    }

    public List<Path> route(HopperRequest request) {
        // Get the basic base route data.
        // This must allow to build RouteInstruction and RoutePoint and RouteError
        // in AbstractHopperClient.route()
        //
        // I'm not actually sure that List<Path> is actually sufficient, but anyway...

        return new ArrayList<Path>();
    }
}
