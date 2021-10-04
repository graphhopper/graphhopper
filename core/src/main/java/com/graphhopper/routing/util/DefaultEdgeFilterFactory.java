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
package com.graphhopper.routing.util;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.PMap;


/**
 * This class creates FlagEncoders that are already included in the GraphHopper distribution.
 *
 * @author Peter Karich
 */
@Deprecated // TODO ORS: this class isn't part of GH anymore
public class DefaultEdgeFilterFactory implements EdgeFilterFactory {
    @Override
    public EdgeFilter createEdgeFilter(PMap opts, FlagEncoder flagEncoder, GraphHopperStorage gs) {
        // ORS orig: return DefaultEdgeFilter.allEdges(flagEncoder);
        return EdgeFilter.ALL_EDGES; // TODO ORS: this is only a quick hack to make it compile
    }
}
