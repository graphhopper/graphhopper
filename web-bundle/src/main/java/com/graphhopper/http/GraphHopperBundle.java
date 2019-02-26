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

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.http.health.GraphHopperHealthCheck;
import com.graphhopper.http.health.GraphHopperStorageHealthCheck;
import com.graphhopper.isochrone.algorithm.DelaunayTriangulationIsolineBuilder;
import com.graphhopper.jackson.GraphHopperModule;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.reader.gtfs.RealtimeFeed;
import com.graphhopper.resources.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.TranslationMap;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Inject;
import javax.ws.rs.ext.WriterInterceptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    static class GraphHopperStorageFactory implements Factory<GraphHopperStorage> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public GraphHopperStorage provide() {
            return graphHopper.getGraphHopperStorage();
        }

        @Override
        public void dispose(GraphHopperStorage instance) {

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

    static class RasterHullBuilderFactory implements Factory<DelaunayTriangulationIsolineBuilder> {

        DelaunayTriangulationIsolineBuilder builder = new DelaunayTriangulationIsolineBuilder();

        @Override
        public DelaunayTriangulationIsolineBuilder provide() {
            return builder;
        }

        @Override
        public void dispose(DelaunayTriangulationIsolineBuilder delaunayTriangulationIsolineBuilder) {
        }
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        bootstrap.getObjectMapper().setDateFormat(new StdDateFormat());
        bootstrap.getObjectMapper().registerModule(new JtsModule());
        bootstrap.getObjectMapper().registerModule(new GraphHopperModule());
        bootstrap.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // Because VirtualEdgeIteratorState has getters which throw Exceptions.
        // http://stackoverflow.com/questions/35359430/how-to-make-jackson-ignore-properties-if-the-getters-throw-exceptions
        bootstrap.getObjectMapper().registerModule(new SimpleModule().setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
                return beanProperties.stream().map(bpw -> new BeanPropertyWriter(bpw) {
                    @Override
                    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
                        try {
                            super.serializeAsField(bean, gen, prov);
                        } catch (Exception e) {
                            // Ignoring expected exception, see above.
                        }
                    }
                }).collect(Collectors.toList());
            }
        }));
    }

    @Override
    public void run(GraphHopperBundleConfiguration configuration, Environment environment) {
        configuration.getGraphHopperConfiguration().merge(CmdArgs.readFromSystemProperties());

        // If the "?type=gpx" parameter is present, sets a corresponding media type header
        environment.jersey().register(new TypeGPXFilter());

        // Together, these two take care that MultiExceptions thrown from RouteResource
        // come out as JSON or GPX, depending on the media type
        environment.jersey().register(new MultiExceptionMapper());
        environment.jersey().register(new MultiExceptionGPXMessageBodyWriter());

        environment.jersey().register(new IllegalArgumentExceptionMapper());
        environment.jersey().register(new GHPointConverterProvider());

        if (configuration.getGraphHopperConfiguration().has("gtfs.file")) {
            // switch to different API implementation when using Pt
            runPtGraphHopper(configuration.getGraphHopperConfiguration(), environment);
        } else {
            runRegularGraphHopper(configuration.getGraphHopperConfiguration(), environment);
        }
    }

    private void runPtGraphHopper(CmdArgs configuration, Environment environment) {
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        final GHDirectory ghDirectory = GraphHopperGtfs.createGHDirectory(configuration.get("graph.location", "target/tmp"));
        final GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        final EncodingManager encodingManager = new EncodingManager.Builder(8).add(ptFlagEncoder).add(new FootFlagEncoder()).add(new CarFlagEncoder()).build();
        final GraphHopperStorage graphHopperStorage = GraphHopperGtfs.createOrLoad(ghDirectory, encodingManager, ptFlagEncoder, gtfsStorage,
                configuration.has("gtfs.file") ? Arrays.asList(configuration.get("gtfs.file", "").split(",")) : Collections.emptyList(),
                configuration.has("datareader.file") ? Arrays.asList(configuration.get("datareader.file", "").split(",")) : Collections.emptyList());
        final TranslationMap translationMap = GraphHopperGtfs.createTranslationMap();
        final LocationIndex locationIndex = GraphHopperGtfs.createOrLoadIndex(ghDirectory, graphHopperStorage);
        final GraphHopperAPI graphHopper = new GraphHopperGtfs(ptFlagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.empty(gtfsStorage));
        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(configuration).to(CmdArgs.class);
                bind(graphHopper).to(GraphHopperAPI.class);
                bind(false).to(Boolean.class).named("hasElevation");
                bind(locationIndex).to(LocationIndex.class);
                bind(translationMap).to(TranslationMap.class);
                bind(encodingManager).to(EncodingManager.class);
                bind(graphHopperStorage).to(GraphHopperStorage.class);
                bindFactory(RasterHullBuilderFactory.class).to(DelaunayTriangulationIsolineBuilder.class);
            }
        });
        environment.jersey().register(NearestResource.class);
        environment.jersey().register(RouteResource.class);
        environment.jersey().register(new PtIsochroneResource(gtfsStorage, encodingManager, graphHopperStorage, locationIndex));
        environment.jersey().register(I18NResource.class);
        environment.jersey().register(InfoResource.class);
        // Say we only support pt, even though we now have several flag encoders. Yes, I know, we're almost there.
        environment.jersey().register((WriterInterceptor) context -> {
            if (context.getEntity() instanceof InfoResource.Info) {
                InfoResource.Info info = (InfoResource.Info) context.getEntity();
                info.supported_vehicles = new String[]{"pt"};
                info.features.remove("car");
                info.features.remove("foot");
                context.setEntity(info);
            }
            context.proceed();
        });
        environment.lifecycle().manage(new Managed() {
            @Override
            public void start() throws Exception {
            }

            @Override
            public void stop() throws Exception {
                locationIndex.close();
                graphHopperStorage.close();
            }
        });
        environment.healthChecks().register("graphhopper-storage", new GraphHopperStorageHealthCheck(graphHopperStorage));
    }

    private void runRegularGraphHopper(CmdArgs configuration, Environment environment) {
        final GraphHopperManaged graphHopperManaged = new GraphHopperManaged(configuration, environment.getObjectMapper());
        environment.lifecycle().manage(graphHopperManaged);
        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(configuration).to(CmdArgs.class);
                bind(graphHopperManaged).to(GraphHopperManaged.class);
                bind(graphHopperManaged.getGraphHopper()).to(GraphHopper.class);
                bind(graphHopperManaged.getGraphHopper()).to(GraphHopperAPI.class);

                bindFactory(HasElevation.class).to(Boolean.class).named("hasElevation");
                bindFactory(LocationIndexFactory.class).to(LocationIndex.class);
                bindFactory(TranslationMapFactory.class).to(TranslationMap.class);
                bindFactory(EncodingManagerFactory.class).to(EncodingManager.class);
                bindFactory(GraphHopperStorageFactory.class).to(GraphHopperStorage.class);
                bindFactory(RasterHullBuilderFactory.class).to(DelaunayTriangulationIsolineBuilder.class);
            }
        });

        if (configuration.getBool("web.change_graph.enabled", false)) {
            environment.jersey().register(ChangeGraphResource.class);
        }
        environment.jersey().register(NearestResource.class);
        environment.jersey().register(RouteResource.class);
        environment.jersey().register(IsochroneResource.class);
        environment.jersey().register(I18NResource.class);
        environment.jersey().register(InfoResource.class);
        environment.healthChecks().register("graphhopper", new GraphHopperHealthCheck(graphHopperManaged.getGraphHopper()));
    }

}
