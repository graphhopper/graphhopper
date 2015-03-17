package com.graphhopper.engine.configuration;

import com.graphhopper.util.CmdArgs;

public class CmdArgsEngineConfiguration extends EngineConfiguration {

    public CmdArgsEngineConfiguration(String configFilePath, CmdArgs args) {
        this(CmdArgs.readFromConfigAndMerge(args, "config", configFilePath));
    }

    public CmdArgsEngineConfiguration(CmdArgs args) {
        setGraphLocation(args.get("graph.location", getGraphLocation()));
        // etc...
    }
}
