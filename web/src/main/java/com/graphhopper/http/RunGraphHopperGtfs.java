package com.graphhopper.http;

import com.google.inject.Guice;
import com.google.inject.util.Modules;
import com.graphhopper.util.CmdArgs;

public class RunGraphHopperGtfs {

    public static void main(String[] args) throws Exception {
        final CmdArgs cmdArgs = CmdArgs.read(args);
        new GHServer(cmdArgs).start(Guice.createInjector(
                Modules.override(new DefaultModule(cmdArgs)).with(new GraphHopperGtfsModule()),
                new GHServletModule(cmdArgs)));
    }
}
