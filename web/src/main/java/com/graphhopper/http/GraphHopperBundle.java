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

package com.graphhopper.http;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.reader.gtfs.RealtimeFeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.TranslationMap;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Inject;
import javax.servlet.DispatcherType;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

public class GraphHopperBundle implements ConfiguredBundle<GraphHopperConfiguration> {

    static class TranslationMapFactory implements Factory<TranslationMap> {

        @Inject GraphHopper graphHopper;

        @Override
        public TranslationMap provide() {
            return graphHopper.getTranslationMap();
        }

        @Override
        public void dispose(TranslationMap instance) {

        }
    }
    static class GraphHopperStorageProvider implements Factory<GraphHopperStorage> {

        @Inject GraphHopper graphHopper;

        @Override
        public GraphHopperStorage provide() {
            return graphHopper.getGraphHopperStorage();
        }

        @Override
        public void dispose(GraphHopperStorage instance) {

        }
    }

    static class EncodingManagerProvider implements Factory<EncodingManager> {

        @Inject GraphHopper graphHopper;

        @Override
        public EncodingManager provide() {
            return graphHopper.getEncodingManager();
        }

        @Override
        public void dispose(EncodingManager instance) {

        }
    }

    static class LocationIndexProvider implements Factory<LocationIndex> {

        @Inject GraphHopper graphHopper;

        @Override
        public LocationIndex provide() {
            return graphHopper.getLocationIndex();
        }

        @Override
        public void dispose(LocationIndex instance) {

        }
    }

    static class HasElevation implements Factory<Boolean> {

        @Inject GraphHopper graphHopper;

        @Override
        public Boolean provide() {
            return graphHopper.hasElevation();
        }

        @Override
        public void dispose(Boolean instance) {

        }
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }

    @Override
    public void run(GraphHopperConfiguration configuration, Environment environment) throws Exception {
        if (configuration.cmdArgs.has("gtfs.file")) {
            // switch to different API implementation when using Pt
            runPtGraphHopper(configuration, environment);
        } else {
            runRegularGraphHopper(configuration, environment);
        }

        environment.servlets().addFilter("cors", CORSFilter.class).addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "*");
        environment.servlets().addFilter("ipfilter", new IPFilter(configuration.cmdArgs.get("jetty.whiteips", ""), configuration.cmdArgs.get("jetty.blackips", ""))).addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "*");

    }

    private void runPtGraphHopper(GraphHopperConfiguration configuration, Environment environment) {
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        final GHDirectory ghDirectory = GraphHopperGtfs.createGHDirectory(configuration.cmdArgs.get("graph.location", "target/tmp"));
        final GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        final EncodingManager encodingManager = new EncodingManager(Arrays.asList(ptFlagEncoder), 8);
        final GraphHopperStorage graphHopperStorage = GraphHopperGtfs.createOrLoad(ghDirectory, encodingManager, ptFlagEncoder, gtfsStorage,
                configuration.cmdArgs.getBool("gtfs.createwalknetwork", false),
                configuration.cmdArgs.has("gtfs.file") ? Arrays.asList(configuration.cmdArgs.get("gtfs.file", "").split(",")) : Collections.emptyList(),
                configuration.cmdArgs.has("datareader.file") ? Arrays.asList(configuration.cmdArgs.get("datareader.file", "").split(",")) : Collections.emptyList());
        final TranslationMap translationMap = GraphHopperGtfs.createTranslationMap();
        final LocationIndex locationIndex = GraphHopperGtfs.createOrLoadIndex(ghDirectory, graphHopperStorage, ptFlagEncoder);
        final GraphHopperAPI graphHopper = new GraphHopperGtfs(ptFlagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.empty());
        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(configuration).to(GraphHopperConfiguration.class);
                bind(graphHopper).to(GraphHopperAPI.class);
                bind(false).to(Boolean.class).named("hasElevation");
                bind(locationIndex).to(LocationIndex.class);
                bind(translationMap).to(TranslationMap.class);
                bind(encodingManager).to(EncodingManager.class);
                bind(graphHopperStorage).to(GraphHopperStorage.class);
            }
        });
        environment.jersey().register(NearestResource.class);
        environment.jersey().register(RouteResource.class);
        environment.jersey().register(I18NResource.class);
        environment.jersey().register(InfoResource.class);
        environment.lifecycle().manage(new Managed() {
            @Override
            public void start() throws Exception {}

            @Override
            public void stop() throws Exception {
                locationIndex.close();
                graphHopperStorage.close();
            }
        });
    }

    private void runRegularGraphHopper(GraphHopperConfiguration configuration, Environment environment) {
        final GraphHopperManaged graphHopperManaged = new GraphHopperManaged(configuration);
        environment.lifecycle().manage(graphHopperManaged);
        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(configuration).to(GraphHopperConfiguration.class);
                bind(graphHopperManaged).to(GraphHopperManaged.class);
                bind(graphHopperManaged.getGraphHopper()).to(GraphHopper.class);
                bind(graphHopperManaged.getGraphHopper()).to(GraphHopperAPI.class);

                bindFactory(HasElevation.class).to(Boolean.class).named("hasElevation");
                bindFactory(LocationIndexProvider.class).to(LocationIndex.class);
                bindFactory(TranslationMapFactory.class).to(TranslationMap.class);
                bindFactory(EncodingManagerProvider.class).to(EncodingManager.class);
                bindFactory(GraphHopperStorageProvider.class).to(GraphHopperStorage.class);
            }
        });

        environment.jersey().register(ChangeGraphResource.class);
        environment.jersey().register(NearestResource.class);
        environment.jersey().register(RouteResource.class);
        environment.jersey().register(I18NResource.class);
        environment.jersey().register(InfoResource.class);
    }

}
