package com.graphhopper.http;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.TranslationMap;

public class RunGraphHopperGtfs {

    public static void main(String[] args) throws Exception {
        CmdArgs cmdArgs = CmdArgs.read(args);
        GraphHopperGtfs graphHopper = new GraphHopperGtfs();
        graphHopper.setGtfsFile(cmdArgs.get("datareader.file", ""));
        graphHopper.setGraphHopperLocation(cmdArgs.get("graph.location", "target/tmp"));
        graphHopper.importOrLoad();

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TranslationMap.class).toInstance(graphHopper.getTranslationMap());

                bind(Long.class).annotatedWith(Names.named("timeout")).toInstance(3000L);
                bind(Boolean.class).annotatedWith(Names.named("jsonp_allowed")).toInstance(false);

                bind(RouteSerializer.class).toInstance(new SimpleRouteSerializer(graphHopper.getGraphHopperStorage().getBounds()));

                install(new GHServletModule(cmdArgs));
                bind(GraphHopper.class).toInstance(graphHopper);
            }
        });
        new GHServer(cmdArgs).start(injector);
    }
}
