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
package com.graphhopper.matching.http;

import com.google.inject.AbstractModule;

import com.google.inject.Provides;
import com.graphhopper.GraphHopper;
import com.graphhopper.matching.LocationIndexMatch;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.CmdArgs;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * @author Peter Karich
 */
public class MapMatchingModule extends AbstractModule {

    private final CmdArgs args;

    public MapMatchingModule(CmdArgs args) {
        this.args = args;
    }

    @Provides
    @Singleton
    LocationIndexMatch createLocationIndexMatch(GraphHopper hopper, LocationIndex index) {
        return new LocationIndexMatch(hopper.getGraphHopperStorage(), (LocationIndexTree) index);
    }

    @Provides
    @Singleton
    @Named("gps.max_accuracy")
    double getMaxGPSAccuracy() {
        return args.getDouble("web.gps.max_accuracy", 100);
    }

    @Override
    protected void configure() {
    }
}
