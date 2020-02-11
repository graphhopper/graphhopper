/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.tardur;

import com.conveyal.osmlib.OSM;
import com.graphhopper.http.GraphHopperBundle;
import com.graphhopper.tardur.resources.ConditionalAccessRestrictionsResource;
import com.graphhopper.tardur.resources.ConditionalTurnRestrictionsResource;
import com.graphhopper.tardur.resources.RootResource;
import com.graphhopper.timezone.core.TimeZones;
import io.dropwizard.Application;
import io.dropwizard.bundles.assets.ConfiguredAssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.hk2.api.Factory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public final class TardurApplication extends Application<TardurConfiguration> {

    static class OSMFactory implements Factory<OSM> {

        @Inject
        TardurGraphHopperManaged graphHopperManaged;


        @Override
        public OSM provide() {
            return graphHopperManaged.getOsm();
        }

        @Override
        public void dispose(OSM instance) {

        }
    }

    static class TimeZonesFactory implements Factory<TimeZones> {
        @Inject
        TardurGraphHopperManaged graphHopperManaged;


        @Override
        public TimeZones provide() {
            return graphHopperManaged.getTimeZones();
        }

        @Override
        public void dispose(TimeZones instance) {

        }
    }



    public static void main(String[] args) throws Exception {
        new TardurApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<TardurConfiguration> bootstrap) {
        bootstrap.addBundle(new GraphHopperBundle());

        Map<String, String> resourceToURIMappings = new HashMap<>();
        resourceToURIMappings.put("/assets/", "/maps/");
        resourceToURIMappings.put("/META-INF/resources/webjars", "/webjars"); // https://www.webjars.org/documentation#dropwizard
        bootstrap.addBundle(new ConfiguredAssetsBundle(resourceToURIMappings, "index.html"));
    }

    @Override
    public void run(TardurConfiguration configuration, Environment environment) throws Exception {
        environment.jersey().register(new GHJerseyViolationExceptionMapper());
        environment.jersey().register(new RootResource());
        environment.jersey().register(ConditionalTurnRestrictionsResource.class);
        environment.jersey().register(ConditionalAccessRestrictionsResource.class);
    }
}
