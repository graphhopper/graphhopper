package com.graphhopper.http;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.util.Modules;
import com.graphhopper.util.CmdArgs;

public class RunGraphHopperGtfs {

    public static void main(String[] args) throws Exception {
        final CmdArgs cmdArgs = CmdArgs.read(args);
        new GHServer(cmdArgs).start(Guice.createInjector(
                new CmdArgsModule(cmdArgs),
                new GraphHopperGtfsModule(),
                new GHServletModule(cmdArgs)));
    }
}
