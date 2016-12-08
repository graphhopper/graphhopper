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
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.TranslationMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * @author Peter Karich
 */
public final class DefaultModule extends AbstractModule {
    private final CmdArgs args;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public DefaultModule(CmdArgs args) {
        this.args = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");
    }

    @Provides
    @Singleton
    GraphHopper createGraphHopper(CmdArgs args) {
        GraphHopper tmp = new GraphHopperOSM().forServer().init(args);
        tmp.importOrLoad();
        logger.info("loaded graph at:" + tmp.getGraphHopperLocation()
                + ", data_reader_file:" + tmp.getDataReaderFile()
                + ", flag_encoders:" + tmp.getEncodingManager()
                + ", " + tmp.getGraphHopperStorage().toDetailsString());
        return tmp;
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

    @Override
    protected void configure() {
        install(new CmdArgsModule(args));
        bind(GraphHopperAPI.class).to(GraphHopper.class);
    }

}
