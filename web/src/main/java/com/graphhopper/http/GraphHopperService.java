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
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.util.spatialrules.DefaultSpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookupBuilder;
import com.graphhopper.spatialrules.SpatialRuleLookupHelper;
import com.graphhopper.util.Parameters;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

@Singleton
class GraphHopperService implements Provider<GraphHopper>, Managed {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopper graphHopper;

    @Inject
    GraphHopperService(GraphHopperConfiguration configuration) {
        // the ruleLookup splits certain areas from each other but avoids making this a permanent change so that other algorithms still can route through these regions.
        graphHopper = new GraphHopperOSM() {
            @Override
            protected void loadOrPrepareLM() {
                if (!getLMFactoryDecorator().isEnabled() || getLMFactoryDecorator().getPreparations().isEmpty())
                    return;

                try {
                    String location = configuration.cmdArgs.get(Parameters.Landmark.PREPARE + "split_area_location", "");
                    Reader reader = location.isEmpty() ? new InputStreamReader(LandmarkStorage.class.getResource("map.geo.json").openStream()) : new FileReader(location);
                    JsonFeatureCollection jsonFeatureCollection = new GHJsonFactory().create().fromJson(reader, JsonFeatureCollection.class);
                    if (!jsonFeatureCollection.getFeatures().isEmpty()) {
                        SpatialRuleLookup ruleLookup = SpatialRuleLookupBuilder.buildIndex(jsonFeatureCollection, "area", (id, polygons) -> new DefaultSpatialRule() {
                            @Override
                            public String getId() {
                                return id;
                            }
                        }.setBorders(polygons));
                        for (PrepareLandmarks prep : getLMFactoryDecorator().getPreparations()) {
                            // the ruleLookup splits certain areas from each other but avoids making this a permanent change so that other algorithms still can route through these regions.
                            if (ruleLookup != null && ruleLookup.size() > 0) {
                                prep.setSpatialRuleLookup(ruleLookup);
                            }
                        }
                    }
                } catch (IOException ex) {
                    logger.error("Problem while reading border map GeoJSON. Skipping this.", ex);
                }

                super.loadOrPrepareLM();
            }
        }.forServer();

        SpatialRuleLookupHelper.buildAndInjectSpatialRuleIntoGH(graphHopper, configuration.cmdArgs);

        graphHopper.init(configuration.cmdArgs);
    }

    @Override
    public void start() {
        graphHopper.importOrLoad();
        logger.info("loaded graph at:" + graphHopper.getGraphHopperLocation()
                + ", data_reader_file:" + graphHopper.getDataReaderFile()
                + ", flag_encoders:" + graphHopper.getEncodingManager()
                + ", " + graphHopper.getGraphHopperStorage().toDetailsString());
    }

    @Override
    public GraphHopper get() {
        return graphHopper;
    }

    @Override
    public void stop() throws Exception {
        graphHopper.close();
    }

}
