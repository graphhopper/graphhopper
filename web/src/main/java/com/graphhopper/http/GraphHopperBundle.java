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

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.http.health.GraphHopperHealthCheck;
import com.graphhopper.http.health.GraphHopperStorageHealthCheck;
import com.graphhopper.http.resources.*;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.reader.gtfs.RealtimeFeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetail;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Inject;
import javax.servlet.DispatcherType;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

public class GraphHopperBundle implements ConfiguredBundle<HasGraphHopperConfiguration> {

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
    static class GraphHopperStorageFactory implements Factory<GraphHopperStorage> {

        @Inject GraphHopper graphHopper;

        @Override
        public GraphHopperStorage provide() {
            return graphHopper.getGraphHopperStorage();
        }

        @Override
        public void dispose(GraphHopperStorage instance) {

        }
    }

    static class EncodingManagerFactory implements Factory<EncodingManager> {

        @Inject GraphHopper graphHopper;

        @Override
        public EncodingManager provide() {
            return graphHopper.getEncodingManager();
        }

        @Override
        public void dispose(EncodingManager instance) {

        }
    }

    static class LocationIndexFactory implements Factory<LocationIndex> {

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
    public void run(HasGraphHopperConfiguration configuration, Environment environment) throws Exception {
        configuration.graphhopper().merge(CmdArgs.readFromConfigAndMerge(configuration.graphhopper()));

        if (configuration.graphhopper().has("gtfs.file")) {
            // switch to different API implementation when using Pt
            runPtGraphHopper(configuration.graphhopper(), environment);
        } else {
            runRegularGraphHopper(configuration.graphhopper(), environment);
        }

        environment.servlets().addFilter("cors", CORSFilter.class).addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "*");
        environment.servlets().addFilter("ipfilter", new IPFilter(configuration.graphhopper().get("jetty.whiteips", ""), configuration.graphhopper().get("jetty.blackips", ""))).addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "*");

    }

    private void runPtGraphHopper(CmdArgs configuration, Environment environment) {
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        final GHDirectory ghDirectory = GraphHopperGtfs.createGHDirectory(configuration.get("graph.location", "target/tmp"));
        final GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        final EncodingManager encodingManager = new EncodingManager(Arrays.asList(ptFlagEncoder), 8);
        final GraphHopperStorage graphHopperStorage = GraphHopperGtfs.createOrLoad(ghDirectory, encodingManager, ptFlagEncoder, gtfsStorage,
                configuration.getBool("gtfs.createwalknetwork", false),
                configuration.has("gtfs.file") ? Arrays.asList(configuration.get("gtfs.file", "").split(",")) : Collections.emptyList(),
                configuration.has("datareader.file") ? Arrays.asList(configuration.get("datareader.file", "").split(",")) : Collections.emptyList());
        final TranslationMap translationMap = GraphHopperGtfs.createTranslationMap();
        final LocationIndex locationIndex = GraphHopperGtfs.createOrLoadIndex(ghDirectory, graphHopperStorage, ptFlagEncoder);
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
        environment.healthChecks().register("graphhopper-storage", new GraphHopperStorageHealthCheck(graphHopperStorage));
    }

    private void runRegularGraphHopper(CmdArgs configuration, Environment environment) {
        final GraphHopperManaged graphHopperManaged = new GraphHopperManaged(configuration);
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
            }
        });

        if (configuration.getBool("web.change_graph.enabled", false)) {
            environment.jersey().register(ChangeGraphResource.class);
        }
        environment.jersey().register(NearestResource.class);
        environment.jersey().register(RouteResource.class);
        environment.jersey().register(I18NResource.class);
        environment.jersey().register(InfoResource.class);

        SimpleModule pathDetailModule = new SimpleModule();
        pathDetailModule.addSerializer(PathDetail.class, new PathDetailSerializer());
        pathDetailModule.addDeserializer(PathDetail.class, new PathDetailDeserializer());
        environment.getObjectMapper().registerModule(pathDetailModule);
        environment.healthChecks().register("graphhopper", new GraphHopperHealthCheck(graphHopperManaged.getGraphHopper()));
    }

    public static class PathDetailSerializer extends JsonSerializer<PathDetail> {

        @Override
        public void serialize(PathDetail value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartArray();

            gen.writeNumber(value.getFirst());
            gen.writeNumber(value.getLast());

            if (value.getValue() instanceof Double)
                gen.writeNumber((Double) value.getValue());
            else if (value.getValue() instanceof Long)
                gen.writeNumber((Long) value.getValue());
            else if (value.getValue() instanceof Integer)
                gen.writeNumber((Integer) value.getValue());
            else if (value.getValue() instanceof Boolean)
                gen.writeBoolean((Boolean) value.getValue());
            else if (value.getValue() instanceof String)
                gen.writeString((String) value.getValue());
            else
                throw new JsonGenerationException("Unsupported type for PathDetail.value" + value.getValue().getClass(), gen);

            gen.writeEndArray();
        }
    }

    public static class PathDetailDeserializer extends JsonDeserializer<PathDetail> {

        @Override
        public PathDetail deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode pathDetail = jp.readValueAsTree();
            if (pathDetail.size() != 3)
                throw new JsonParseException(jp, "PathDetail array must have exactly 3 entries but was " + pathDetail.size());

            JsonNode from = pathDetail.get(0);
            JsonNode to = pathDetail.get(1);
            JsonNode val = pathDetail.get(2);

            PathDetail pd;
            if (val.isBoolean())
                pd = new PathDetail(val.asBoolean());
            else if (val.isLong())
                pd = new PathDetail(val.asLong());
            else if (val.isDouble())
                pd = new PathDetail(val.asDouble());
            else if (val.isTextual())
                pd = new PathDetail(val.asText());
            else
                throw new JsonParseException(jp, "Unsupported type of PathDetail value " + pathDetail.getNodeType().name());

            pd.setFirst(from.asInt());
            pd.setLast(to.asInt());
            return pd;
        }
    }

}
