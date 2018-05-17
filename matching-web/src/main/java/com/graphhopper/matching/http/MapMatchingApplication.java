package com.graphhopper.matching.http;

import com.graphhopper.http.GraphHopperBundle;
import com.graphhopper.http.cli.ImportCommand;
import io.dropwizard.Application;
import io.dropwizard.bundles.assets.ConfiguredAssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class MapMatchingApplication extends Application<MapMatchingServerConfiguration> {

    public static void main(String[] args) throws Exception {
        new MapMatchingApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<MapMatchingServerConfiguration> bootstrap) {
        bootstrap.addBundle(new GraphHopperBundle());
        bootstrap.addCommand(new ImportCommand());
        bootstrap.addBundle(new ConfiguredAssetsBundle("/assets/mapmatching-webapp/", "/app/", "index.html"));
    }

    @Override
    public void run(MapMatchingServerConfiguration mapMatchingServerConfiguration, Environment environment) {
        environment.jersey().register(MapMatchingResource.class);
        environment.jersey().register(new RootResource());
    }

}
