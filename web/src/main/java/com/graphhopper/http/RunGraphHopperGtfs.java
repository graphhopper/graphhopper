package com.graphhopper.http;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.graphhopper.GraphHopper;
import com.graphhopper.http.GHServer;
import com.graphhopper.http.GHServletModule;
import com.graphhopper.http.RouteSerializer;
import com.graphhopper.http.SimpleRouteSerializer;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.TranslationMap;

public class RunGraphHopperGtfs {

	private static final String graphFileFoot = "target/vbb";

	public static void main(String[] args) throws Exception {
		GraphHopperGtfs graphHopper = new GraphHopperGtfs();
		graphHopper.setGtfsFile("/Users/michaelzilske/wurst/vbb/380248.zip");
		graphHopper.setGraphHopperLocation(graphFileFoot);
		graphHopper.importOrLoad();
		CmdArgs cmdArgs = new CmdArgs();
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
