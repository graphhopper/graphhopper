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
import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.TranslationMap;

import javax.inject.Named;

public class GraphHopperModule extends AbstractModule {
    private final CmdArgs args;

    public GraphHopperModule(CmdArgs args) {
        this.args = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");
    }

    @Override
    protected void configure() {
        install(new CmdArgsModule(args));
        bind(GHJson.class).toInstance(new GHJsonFactory().create());
        bind(GraphHopper.class).toProvider(GraphHopperService.class);
        bind(GraphHopperAPI.class).to(GraphHopper.class);
    }

    @Provides
    TranslationMap getTranslationMap(GraphHopper graphHopper) {
        return graphHopper.getTranslationMap();
    }

    @Provides
    RouteSerializer getRouteSerializer(GraphHopper graphHopper) {
        return new SimpleRouteSerializer(graphHopper.getGraphHopperStorage().getBounds());
    }

    @Provides
    GraphHopperStorage getGraphHopperStorage(GraphHopper graphHopper) {
        return graphHopper.getGraphHopperStorage();
    }

    @Provides
    EncodingManager getEncodingManager(GraphHopper graphHopper) {
        return graphHopper.getEncodingManager();
    }

    @Provides
    LocationIndex getLocationIndex(GraphHopper graphHopper) {
        return graphHopper.getLocationIndex();
    }

    @Provides
    @Named("hasElevation")
    boolean hasElevation(GraphHopper graphHopper) {
        return graphHopper.hasElevation();
    }

}
