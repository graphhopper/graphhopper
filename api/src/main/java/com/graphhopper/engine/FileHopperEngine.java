package com.graphhopper.engine;

import com.graphhopper.HopperEngine;
import com.graphhopper.HopperRequest;
import com.graphhopper.engine.configuration.EngineConfiguration;
import com.graphhopper.routing.Path;

import java.util.ArrayList;
import java.util.List;

// All the "internal" low-API goes here
// This class should containts the "import" logic only
public class FileHopperEngine implements HopperEngine {

    private String osmFile;
    private EngineConfiguration configuration;

    public FileHopperEngine(String osmFile) {
        this.osmFile = osmFile;
    }

    @Override
    public HopperEngine inizialize(EngineConfiguration configuration) {
        return null;
    }

    @Override
    public List<Path> route(HopperRequest request) {
        return new ArrayList<Path>();
    }
}
