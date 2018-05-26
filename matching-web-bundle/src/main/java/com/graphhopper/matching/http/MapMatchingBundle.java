package com.graphhopper.matching.http;

import io.dropwizard.Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class MapMatchingBundle implements Bundle {

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }

    @Override
    public void run(Environment environment) {
        environment.jersey().register(MapMatchingResource.class);
    }

}
