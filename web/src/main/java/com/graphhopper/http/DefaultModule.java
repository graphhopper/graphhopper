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
import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.json.geo.GeoJsonPolygon;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.util.spatialrules.Polygon;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.TranslationMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.graphhopper.json.GHJson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

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
        GraphHopper tmp = new GraphHopperOSM(){
            @Override
            protected void prepareLM() {
                if(getLMFactoryDecorator().getPreparations().isEmpty())
                    return;

                try {
                    GHJson ghJson = new GHJsonBuilder().create();
                    JsonFeatureCollection jsonFeatureCollection = ghJson.fromJson(
                            new InputStreamReader(LandmarkStorage.class.getResource("map.geo.json").openStream()),
                            JsonFeatureCollection.class);
                    Map<String, Polygon> map = new HashMap<>();
                    for (JsonFeature feature : jsonFeatureCollection.getFeatures()) {
                        String name = (String) feature.getProperties().get("country");
                        if (name == null)
                            name = "unnamed";

                        if (feature.hasGeometry()) {
                            if (feature.getGeometry() instanceof GeoJsonPolygon) {
                                Polygon res = map.put(name, ((GeoJsonPolygon) feature.getGeometry()).getPolygons().get(0));
                                if (res != null)
                                    throw new RuntimeException("Duplicate JsonFeature " + name + " in collection");
                            }
                        }
                    }

                    for (PrepareLandmarks prep : getLMFactoryDecorator().getPreparations()) {
                        prep.setBorderMap(map);
                    }
                } catch (IOException ex) {
                    logger.error("Problem while reading splitting map GeoJSON. Using empty map.", ex);
                }

                super.prepareLM();
            }
        }.forServer().init(args);

        //TODO, move to a more appropriate place
        // tmp.setSpatialRuleLookup(SpatialRuleLookupBuilder.build());

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
