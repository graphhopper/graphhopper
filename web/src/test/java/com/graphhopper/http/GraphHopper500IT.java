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
import com.google.inject.Module;
import com.google.inject.Provides;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;

/**
 * This class makes it possible to test for 500 exceptions.
 *
 * @author Robin Boldt
 */
public class GraphHopper500IT extends BaseServletTester {
    private static final String DIR = "./target/andorra-gh/";
    CmdArgs args;

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
        shutdownJetty(true);
    }

    @Before
    public void setUp() {
        args = new CmdArgs().
                put("config", "../config-example.properties").
                put("prepare.min_network_size", "0").
                put("prepare.min_one_way_network_size", "0").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR);

        setUpJetty(args);
    }

    @Override
    public void setUpGuice(Module... modules) {
        super.setUpGuice(new AbstractModule() {
            @Override
            protected void configure() {
                binder().requireExplicitBindings();
                install(new AbstractModule() {
                    private final Logger logger = LoggerFactory.getLogger(getClass());

                    @Override
                    protected void configure() {
                        install(new CmdArgsModule(args));
                        bind(GHJson.class).toInstance(new GHJsonFactory().create());
                        bind(GraphHopperAPI.class).to(GraphHopper.class);
                    }

                    @Provides
                    @Singleton
                    GraphHopper createGraphHopper(CmdArgs args) {
                        GraphHopper graphHopper = new GraphHopperOSM() {
                            @Override
                            public GHResponse route(GHRequest request) {
                                if (request.getWeighting().equals("illegalargument")) {
                                    throw new IllegalArgumentException("This was expected, why would you pass this?");
                                }
                                throw new RuntimeException("Very bad and unexpected exception");
                            }
                        }.forServer();
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

                });
                install(new GraphHopperServletModule(args));
            }
        });
    }


    @Test
    public void testExceptions() throws Exception {
        query("point=55.99022,29.129734&point=56.001069,29.150848", 500);
        query("point=55.99022,29.129734&point=56.001069,29.150848&weighting=illegalargument", 400);
    }

}