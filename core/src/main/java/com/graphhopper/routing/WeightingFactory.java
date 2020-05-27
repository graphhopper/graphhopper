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

package com.graphhopper.routing;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.PMap;

public interface WeightingFactory {
    /**
     * @param profile          The profile for which the weighting shall be created
     * @param hints            Additional hints that can be used to further specify the weighting that shall be created
     * @param disableTurnCosts Can be used to explicitly create the weighting without turn costs. This is sometimes
     *                         needed when the weighting shall be used by some algorithm that can or should only be run
     *                         with node-based graph traversal, like LM preparation
     */
    Weighting createWeighting(Profile profile, PMap hints, boolean disableTurnCosts);
}
