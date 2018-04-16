package com.graphhopper.http;

import com.github.dirkraft.dropwizard.fileassets.FileAssetsBundle;
import io.dropwizard.setup.Bootstrap;

public class GraphHopperDebugApplication extends GraphHopperApplication {
    public static void main(String[] args) throws Exception {
        new GraphHopperDebugApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<GraphHopperServerConfiguration> bootstrap) {
        super.initialize(bootstrap);
        bootstrap.addBundle(new FileAssetsBundle("web/src/main/resources/assets", "/maps/", "index.html"));
    }
}
