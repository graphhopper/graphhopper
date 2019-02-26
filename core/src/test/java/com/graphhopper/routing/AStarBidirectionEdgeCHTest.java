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
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.Parameters;

import java.util.Collections;
import java.util.List;

public class AStarBidirectionEdgeCHTest extends AbstractRoutingAlgorithmTester {
    @Override
    protected AlgorithmOptions createAlgoOptions() {
        return AlgorithmOptions.start()
                .algorithm(Parameters.Algorithms.ASTAR_BI)
                .weighting(new ShortestWeighting(carEncoder)).build();
    }

    @Override
    protected CHGraph getGraph(GraphHopperStorage ghStorage, Weighting weighting) {
        return ghStorage.getGraph(CHGraph.class, weighting);
    }

    @Override
    protected GraphHopperStorage createGHStorage(
            EncodingManager em, List<? extends Weighting> weightings, boolean is3D) {
        return new GraphHopperStorage(Collections.<Weighting>emptyList(), weightings, new RAMDirectory(), em, is3D, new TurnCostExtension()).create(1000);
    }

    @Override
    public RoutingAlgorithmFactory createFactory(GraphHopperStorage ghStorage, AlgorithmOptions opts) {
        ghStorage.freeze();
        PrepareContractionHierarchies ch = PrepareContractionHierarchies.fromGraphHopperStorage(
                ghStorage, opts.getWeighting(), TraversalMode.EDGE_BASED_2DIR);
        ch.doWork();
        return ch;
    }

}
