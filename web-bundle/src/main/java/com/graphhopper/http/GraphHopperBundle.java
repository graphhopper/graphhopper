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

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.gtfs.*;
import com.graphhopper.http.health.GraphHopperHealthCheck;
import com.graphhopper.isochrone.algorithm.JTSTriangulator;
import com.graphhopper.isochrone.algorithm.Triangulator;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.resources.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.core.util.PMap;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Inject;

public class GraphHopperBundle implements ConfiguredBundle<GraphHopperBundleConfiguration> {

    static class TranslationMapFactory implements Factory<TranslationMap> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public TranslationMap provide() {
            return graphHopper.getTranslationMap();
        }

        @Override
        public void dispose(TranslationMap instance) {

        }
    }

    static class BaseGraphFactory implements Factory<BaseGraph> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public BaseGraph provide() {
            return graphHopper.getBaseGraph();
        }

        @Override
        public void dispose(BaseGraph instance) {

        }
    }

    static class GtfsStorageFactory implements Factory<GtfsStorage> {

        @Inject
        GraphHopperGtfs graphHopper;

        @Override
        public GtfsStorage provide() {
            return graphHopper.getGtfsStorage();
        }

        @Override
        public void dispose(GtfsStorage instance) {

        }
    }

    static class EncodingManagerFactory implements Factory<EncodingManager> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public EncodingManager provide() {
            return graphHopper.getEncodingManager();
        }

        @Override
        public void dispose(EncodingManager instance) {

        }
    }

    static class LocationIndexFactory implements Factory<LocationIndex> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public LocationIndex provide() {
            return graphHopper.getLocationIndex();
        }

        @Override
        public void dispose(LocationIndex instance) {

        }
    }

    static class ProfileResolverFactory implements Factory<ProfileResolver> {
        @Inject
        GraphHopper graphHopper;

        @Override
        public ProfileResolver provide() {
            return new ProfileResolver(graphHopper.getProfiles());
        }

        @Override
        public void dispose(ProfileResolver instance) {

        }
    }

    static class PathDetailsBuilderFactoryFactory implements Factory<PathDetailsBuilderFactory> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public PathDetailsBuilderFactory provide() {
            return graphHopper.getPathDetailsBuilderFactory();
        }

        @Override
        public void dispose(PathDetailsBuilderFactory profileResolver) {

        }
    }

    static class MapMatchingRouterFactoryFactory implements Factory<MapMatchingResource.MapMatchingRouterFactory> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public MapMatchingResource.MapMatchingRouterFactory provide() {
            return new MapMatchingResource.MapMatchingRouterFactory() {
                @Override
                public MapMatching.Router createMapMatchingRouter(PMap hints) {
                    return MapMatching.routerFromGraphHopper(graphHopper, hints);
                }
            };
        }

        @Override
        public void dispose(MapMatchingResource.MapMatchingRouterFactory mapMatchingRouterFactory) {

        }
    }

    static class HasElevation implements Factory<Boolean> {

        @Inject
        GraphHopper graphHopper;

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
        // See #1440: avoids warning regarding com.fasterxml.jackson.module.afterburner.util.MyClassLoader
        bootstrap.setObjectMapper(io.dropwizard.jackson.Jackson.newMinimalObjectMapper());
        // avoids warning regarding com.fasterxml.jackson.databind.util.ClassUtil
        bootstrap.getObjectMapper().registerModule(new Jdk8Module());

        Jackson.initObjectMapper(bootstrap.getObjectMapper());
        bootstrap.getObjectMapper().setDateFormat(new StdDateFormat());
        // See https://github.com/dropwizard/dropwizard/issues/1558
        bootstrap.getObjectMapper().enable(MapperFeature.ALLOW_EXPLICIT_PROPERTY_RENAMING);
    }

    @Override
    public void run(GraphHopperBundleConfiguration configuration, Environment environment) {
        for (Object k : System.getProperties().keySet()) {
            if (k instanceof String && ((String) k).startsWith("graphhopper."))
                throw new IllegalArgumentException("You need to prefix system parameters with '-Ddw.graphhopper.' instead of '-Dgraphhopper.' see #1879 and #1897");
        }

        // When Dropwizard's Hibernate Validation misvalidates a query parameter,
        // a JerseyViolationException is thrown.
        // With this mapper, we use our custom format for that (backwards compatibility),
        // and also coerce the media type of the response to JSON, so we can return JSON error
        // messages from methods that normally have a different return type.
        // That's questionable, but on the other hand, Dropwizard itself does the same thing,
        // not here, but in a different place (the custom parameter parsers).
        // So for the moment we have to assume that both mechanisms
        // a) always return JSON error messages, and
        // b) there's no need to annotate the method with media type JSON for that.
        //
        // However, for places that throw IllegalArgumentException or MultiException,
        // we DO need to use the media type JSON annotation, because
        // those are agnostic to the media type (could be GPX!), so the server needs to know
        // that a JSON error response is supported. (See below.)
        environment.jersey().register(new GHJerseyViolationExceptionMapper());

        // If the "?type=gpx" parameter is present, sets a corresponding media type header
        environment.jersey().register(new TypeGPXFilter());

        // Together, these two take care that MultiExceptions thrown from RouteResource
        // come out as JSON or GPX, depending on the media type
        environment.jersey().register(new MultiExceptionMapper());
        environment.jersey().register(new MultiExceptionGPXMessageBodyWriter());

        // This makes an IllegalArgumentException come out as a MultiException with
        // a single entry.
        environment.jersey().register(new IllegalArgumentExceptionMapper());

        final GraphHopperManaged graphHopperManaged = new GraphHopperManaged(configuration.getGraphHopperConfiguration());
        environment.lifecycle().manage(graphHopperManaged);
        final GraphHopper graphHopper = graphHopperManaged.getGraphHopper();
        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(configuration.getGraphHopperConfiguration()).to(GraphHopperConfig.class);
                bind(graphHopper).to(GraphHopper.class);

                bind(new JTSTriangulator(graphHopper.getRouterConfig())).to(Triangulator.class);
                bindFactory(MapMatchingRouterFactoryFactory.class).to(MapMatchingResource.MapMatchingRouterFactory.class);
                bindFactory(PathDetailsBuilderFactoryFactory.class).to(PathDetailsBuilderFactory.class);
                bindFactory(ProfileResolverFactory.class).to(ProfileResolver.class);
                bindFactory(HasElevation.class).to(Boolean.class).named("hasElevation");
                bindFactory(LocationIndexFactory.class).to(LocationIndex.class);
                bindFactory(TranslationMapFactory.class).to(TranslationMap.class);
                bindFactory(EncodingManagerFactory.class).to(EncodingManager.class);
                bindFactory(BaseGraphFactory.class).to(BaseGraph.class);
                bindFactory(GtfsStorageFactory.class).to(GtfsStorage.class);
            }
        });

        environment.jersey().register(MVTResource.class);
        environment.jersey().register(NearestResource.class);
        environment.jersey().register(RouteResource.class);
        environment.jersey().register(IsochroneResource.class);
        environment.jersey().register(MapMatchingResource.class);
        if (configuration.getGraphHopperConfiguration().has("gtfs.file")) {
            // These are pt-specific implementations of /route and /isochrone, but the same API.
            // We serve them under different paths (/route-pt and /isochrone-pt), and forward
            // requests for ?vehicle=pt there.
            environment.jersey().register(new AbstractBinder() {
                @Override
                protected void configure() {
                    if (configuration.getGraphHopperConfiguration().getBool("gtfs.free_walk", false)) {
                        bind(PtRouterFreeWalkImpl.class).to(PtRouter.class);
                    } else {
                        bind(PtRouterImpl.class).to(PtRouter.class);
                    }
                }
            });
            environment.jersey().register(PtRouteResource.class);
            environment.jersey().register(PtIsochroneResource.class);
            environment.jersey().register(PtMVTResource.class);
            environment.jersey().register(PtRedirectFilter.class);
        }
        environment.jersey().register(SPTResource.class);
        environment.jersey().register(I18NResource.class);
        environment.jersey().register(InfoResource.class);
        environment.healthChecks().register("graphhopper", new GraphHopperHealthCheck(graphHopper));
        environment.jersey().register(environment.healthChecks());
        environment.jersey().register(HealthCheckResource.class);
    }
}
