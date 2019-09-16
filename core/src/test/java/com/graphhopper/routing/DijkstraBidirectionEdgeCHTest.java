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

import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.routing.weighting.TurnWeighting.INFINITE_U_TURN_COSTS;

public class DijkstraBidirectionEdgeCHTest extends AbstractRoutingAlgorithmTester {
    @Override
    protected CHGraph getGraph(GraphHopperStorage ghStorage, Weighting weighting) {
        return ghStorage.getCHGraph(CHProfile.edgeBased(weighting, INFINITE_U_TURN_COSTS));
    }

    @Override
    protected GraphHopperStorage createGHStorage(
            EncodingManager em, List<? extends Weighting> weightings, boolean is3D) {
        List<CHProfile> chProfiles = new ArrayList<>(weightings.size());
        for (Weighting w : weightings) {
            chProfiles.add(CHProfile.edgeBased(w, INFINITE_U_TURN_COSTS));
        }
        return new GraphHopperStorage(chProfiles, new RAMDirectory(), em, is3D, new TurnCostExtension()).create(1000);
    }

    @Override
    public RoutingAlgorithmFactory createFactory(GraphHopperStorage ghStorage, AlgorithmOptions opts) {
        ghStorage.freeze();
        PrepareContractionHierarchies ch = PrepareContractionHierarchies.fromGraphHopperStorage(
                ghStorage, CHProfile.edgeBased(opts.getWeighting(), INFINITE_U_TURN_COSTS));
        ch.doWork();
        return ch;
    }

}
