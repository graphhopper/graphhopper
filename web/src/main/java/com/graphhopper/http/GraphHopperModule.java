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
import com.google.inject.Provides;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookupBuilder;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.spatialrules.DefaultSpatialRule;
import com.graphhopper.routing.util.spatialrules.Polygon;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.spatialrules.SpatialRuleLookupHelper;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.TranslationMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

public class GraphHopperModule extends AbstractModule {
    protected final CmdArgs args;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public GraphHopperModule(CmdArgs args) {
        this.args = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");
    }

    @Override
    protected void configure() {
        install(new CmdArgsModule(args));
        bind(GHJson.class).toInstance(new GHJsonFactory().create());
        bind(GraphHopperAPI.class).to(GraphHopper.class);
    }

    @Provides
    @Singleton
    GraphHopper createGraphHopper(CmdArgs args) {
        GraphHopper graphHopper = new GraphHopperOSM(
                SpatialRuleLookupHelper.createLandmarkSplittingFeatureCollection(args.get(Parameters.Landmark.PREPARE + "split_area_location", ""))
        ).forServer();
        SpatialRuleLookupHelper.buildAndInjectSpatialRuleIntoGH(graphHopper, args);

        graphHopper.init(args);
        return graphHopper;
    }

    @Provides
    @Singleton
    TranslationMap getTranslationMap(GraphHopper graphHopper) {
        return graphHopper.getTranslationMap();
    }

    @Provides
    @Singleton
    RouteSerializer getRouteSerializer(GraphHopper graphHopper) {
        return new SimpleRouteSerializer(graphHopper.getGraphHopperStorage().getBounds());
    }

    @Provides
    @Singleton
    GraphHopperStorage getGraphHopperStorage(GraphHopper graphHopper) {
        return graphHopper.getGraphHopperStorage();
    }

    @Provides
    @Singleton
    EncodingManager getEncodingManager(GraphHopper graphHopper) {
        return graphHopper.getEncodingManager();
    }

    @Provides
    @Singleton
    LocationIndex getLocationIndex(GraphHopper graphHopper) {
        return graphHopper.getLocationIndex();
    }

    @Provides
    @Singleton
    @Named("hasElevation")
    boolean hasElevation(GraphHopper graphHopper) {
        return graphHopper.hasElevation();
    }

    @Provides
    GraphHopperService getGraphHopperService(GraphHopper graphHopper) {
        return new GraphHopperService() {
            @Override
            public void start() {
                graphHopper.importOrLoad();
                logger.info("loaded graph at:" + graphHopper.getGraphHopperLocation()
                        + ", data_reader_file:" + graphHopper.getDataReaderFile()
                        + ", flag_encoders:" + graphHopper.getEncodingManager()
                        + ", " + graphHopper.getGraphHopperStorage().toDetailsString());

            }

            @Override
            public void close() throws Exception {
                graphHopper.close();
            }
        };
    }

}
