package com.graphhopper.matching.http;

import com.graphhopper.http.GraphHopperBundle;
import com.graphhopper.matching.cli.GetBoundsCommand;
import com.graphhopper.matching.cli.ImportCommand;
import com.graphhopper.matching.cli.MatchCommand;
import com.graphhopper.matching.cli.MeasurementCommand;
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
        bootstrap.addCommand(new MatchCommand());
        bootstrap.addCommand(new GetBoundsCommand());
        bootstrap.addCommand(new MeasurementCommand());
        bootstrap.addBundle(new ConfiguredAssetsBundle("/assets/mapmatching-webapp/", "/app/", "index.html"));
    }

    @Override
    public void run(MapMatchingServerConfiguration graphHopperServerConfiguration, Environment environment) {
        environment.jersey().register(MapMatchingResource.class);
        environment.jersey().register(new RootResource());
    }

}
