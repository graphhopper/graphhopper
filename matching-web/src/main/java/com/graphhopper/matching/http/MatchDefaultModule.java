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

import com.google.inject.name.Names;
import com.graphhopper.http.DefaultModule;
import com.graphhopper.matching.LocationIndexMatch;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.CmdArgs;

/**
 *
 * @author Peter Karich
 */
public class MatchDefaultModule extends DefaultModule {

    public MatchDefaultModule(CmdArgs args) {
        super(args);
    }

    @Override
    protected void configure() {
        super.configure();

        LocationIndexMatch locationMatch = new LocationIndexMatch(getGraphHopper().getGraphHopperStorage(),
                (LocationIndexTree) getGraphHopper().getLocationIndex());
        bind(LocationIndexMatch.class).toInstance(locationMatch);

        Double timeout = args.getDouble("web.gps.max_accuracy", 100);
        bind(Double.class).annotatedWith(Names.named("gps.max_accuracy")).toInstance(timeout);
    }
}
