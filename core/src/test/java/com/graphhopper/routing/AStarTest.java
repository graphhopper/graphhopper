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

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostExtension;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;


/**
 * @author Peter Karich
 */
@RunWith(Parameterized.class)
public class AStarTest extends AbstractRoutingAlgorithmTester {
    private final TraversalMode traversalMode;
    private final boolean allowUTurns;

    public AStarTest(TraversalMode tMode, boolean allowUTurns) {
        this.traversalMode = tMode;
        this.allowUTurns = allowUTurns;
    }

    /**
     * Runs the same test with each of the supported traversal modes
     */
    @Parameters(name = "{0}")
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][]{
                {TraversalMode.NODE_BASED, false},
                {TraversalMode.EDGE_BASED, false},
                {TraversalMode.EDGE_BASED, true}
        });
    }

    @Override
    public RoutingAlgorithmFactory createFactory(final GraphHopperStorage prepareGraph, final AlgorithmOptions prepareOpts) {
        return new RoutingAlgorithmFactory() {
            @Override
            public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
                Weighting w = opts.getWeighting();
                if (traversalMode.isEdgeBased()) {
                    double uTurnCost = allowUTurns ? 40 : Double.POSITIVE_INFINITY;
                    w = new TurnWeighting(w, (TurnCostExtension) g.getExtension(), uTurnCost);
                }
                return new AStar(g, w, traversalMode);
            }
        };
    }
}
