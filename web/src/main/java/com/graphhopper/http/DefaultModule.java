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

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.graphhopper.GraphHopper;
import com.graphhopper.countries.Countries;
import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FlagEncoderFactory;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookupBuilder;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * @author Peter Karich
 */
public class DefaultModule extends AbstractModule {
    protected final CmdArgs args;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private GraphHopper graphHopper;

    public DefaultModule(CmdArgs args) {
        this.args = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");
    }

    public GraphHopper getGraphHopper() {
        if (graphHopper == null)
            throw new IllegalStateException("createGraphHopper not called");

        return graphHopper;
    }

    /**
     * @return an initialized GraphHopper instance
     */
    protected GraphHopper createGraphHopper(CmdArgs args) {
        GraphHopper tmp = new GraphHopperOSM() {
            @Override
            protected void loadOrPrepareLM() {
//                if (!getLMFactoryDecorator().isEnabled() || getLMFactoryDecorator().getPreparations().isEmpty())
//                    return;
//
//                try {
//                    String location = args.get(Parameters.Landmark.PREPARE + "split_area_location", "");
//                    Reader reader = location.isEmpty() ? new InputStreamReader(LandmarkStorage.class.getResource("map.geo.json").openStream()) : new FileReader(location);
//                    JsonFeatureCollection jsonFeatureCollection = new GHJsonBuilder().create().fromJson(reader, JsonFeatureCollection.class);
//                    if (!jsonFeatureCollection.getFeatures().isEmpty()) {
//                        SpatialRuleLookup ruleLookup = new SpatialRuleLookupBuilder().build("country",
//                                new SpatialRuleLookupBuilder.SpatialRuleDefaultFactory(), jsonFeatureCollection,
//                                0.1, true);
//                        for (PrepareLandmarks prep : getLMFactoryDecorator().getPreparations()) {
//                            // the ruleLookup splits certain areas from each other but avoids making this a permanent change so that other algorithms still can route through these regions.
//                            if (ruleLookup != null && ruleLookup.size() > 0) {
//                                prep.setCutEdges(new EdgeFilter() {
//                                    @Override
//                                    public boolean accept(EdgeIteratorState edgeState) {
//                                        int adjNode = edgeState.getAdjNode();
//                                        SpatialRule ruleAdj = ruleLookup.lookupRule(getGraphHopperStorage().getNodeAccess().getLatitude(adjNode), getGraphHopperStorage().getNodeAccess().getLongitude(adjNode));
//
//                                        int baseNode = edgeState.getBaseNode();
//                                        SpatialRule ruleBase = ruleLookup.lookupRule(getGraphHopperStorage().getNodeAccess().getLatitude(baseNode), getGraphHopperStorage().getNodeAccess().getLongitude(baseNode));
//
//                                        return edgeState.isForward(prep.getWeighting().getFlagEncoder()) && ruleAdj == ruleBase;
//                                    }
//                                });
//                            }
//                        }
//                    }
//                } catch (IOException ex) {
//                    logger.error("Problem while reading border map GeoJSON. Skipping this.", ex);
//                }
//
//                super.loadOrPrepareLM();
            }
        }.forServer();

        String spatialRuleLocation = args.get("spatial_rules.location", "");
        if (!spatialRuleLocation.isEmpty()) {
            try {
                String ruleFQN = args.get("spatial_rules.fqn", "");
                final SpatialRuleLookup index = Countries.buildIndex(new FileReader(spatialRuleLocation), ruleFQN);
                logger.info("Set spatial rule lookup with " + index.size() + " rules and the following Rules " + ruleFQN);
                final FlagEncoderFactory oldFEF = tmp.getFlagEncoderFactory();
                tmp.setFlagEncoderFactory(new FlagEncoderFactory() {
                    @Override
                    public FlagEncoder createFlagEncoder(String name, PMap configuration) {
                        if (name.equals(GENERIC)) {
                            return new DataFlagEncoder(configuration).setSpatialRuleLookup(index);
                        }

                        return oldFEF.createFlagEncoder(name, configuration);
                    }
                });
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }


        tmp.init(args);


        tmp.importOrLoad();
        logger.info("loaded graph at:" + tmp.getGraphHopperLocation()
                + ", data_reader_file:" + tmp.getDataReaderFile()
                + ", flag_encoders:" + tmp.getEncodingManager()
                + ", " + tmp.getGraphHopperStorage().toDetailsString());
        return tmp;
    }

    @Override
    protected void configure() {
        try {
            graphHopper = createGraphHopper(args);
            bind(GraphHopper.class).toInstance(graphHopper);
            bind(TranslationMap.class).toInstance(graphHopper.getTranslationMap());

            long timeout = args.getLong("web.timeout", 3000);
            bind(Long.class).annotatedWith(Names.named("timeout")).toInstance(timeout);
            boolean jsonpAllowed = args.getBool("web.jsonp_allowed", false);

            bind(Boolean.class).annotatedWith(Names.named("jsonp_allowed")).toInstance(jsonpAllowed);

            bind(RouteSerializer.class).toInstance(new SimpleRouteSerializer(graphHopper.getGraphHopperStorage().getBounds()));

            // should be thread safe
            bind(GHJson.class).toInstance(new GHJsonBuilder().create());
        } catch (Exception ex) {
            throw new IllegalStateException("Couldn't load graph", ex);
        }
    }

}
