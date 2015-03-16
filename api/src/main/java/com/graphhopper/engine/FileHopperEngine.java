package com.graphhopper.engine;

import com.graphhopper.HopperEngine;
import com.graphhopper.HopperRequest;
import com.graphhopper.routing.Path;

import java.util.ArrayList;
import java.util.List;

// All the "internal" low-API goes here
// This class should containts the "import" logic only
public class FileHopperEngine implements HopperEngine {

    private String osmFile;
    private HopperEngineConfiguration configuration;

    public FileHopperEngine(String osmFile) {
        this(osmFile, new HopperEngineConfiguration());
    }

    public FileHopperEngine(String osmFile, HopperEngineConfiguration configuration) {
        this.osmFile = osmFile;
        this.configuration = configuration;
    }

    @Override
    public HopperEngine inizialize() {
        return null;
    }

    @Override
    public List<Path> route(HopperRequest request) {
        return new ArrayList<Path>();
    }

    @Override
    public HopperEngineConfiguration getConfiguration() {
        return configuration;
    }
}
