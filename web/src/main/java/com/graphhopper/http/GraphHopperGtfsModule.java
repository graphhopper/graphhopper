package com.graphhopper.http;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.CmdArgs;

import javax.inject.Singleton;

public final class GraphHopperGtfsModule extends AbstractModule {

    @Provides
    @Singleton
    GraphHopperAPI createGraphHopper(CmdArgs args) {
        GraphHopperGtfs graphHopper = new GraphHopperGtfs();
        graphHopper.setGtfsFile(args.get("datareader.file", ""));
        graphHopper.setCreateWalkNetwork(args.getBool("gtfs.createwalknetwork", false));
        graphHopper.load(args.get("graph.location", "target/tmp"));
        return graphHopper;
    }

    @Override
    protected void configure() {}

}
