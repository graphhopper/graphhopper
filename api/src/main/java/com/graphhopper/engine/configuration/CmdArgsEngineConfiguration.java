package com.graphhopper.engine.configuration;

import com.graphhopper.util.CmdArgs;

public class CmdArgsEngineConfiguration extends EngineConfiguration {

    public CmdArgsEngineConfiguration(CmdArgs args) {
        // the merge with graphhopper.config can be made outside (must be documented)
        String graphHopperFolder = args.get("graph.location", getGraphLocation());

        // etc...
    }
}
