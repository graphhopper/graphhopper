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
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.SPTEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
@RunWith(Parameterized.class)
public class AStarBidirectionTest extends AbstractRoutingAlgorithmTester {
    private final TraversalMode traversalMode;
    private final boolean allowUTurns;

    public AStarBidirectionTest(TraversalMode tMode, boolean allowUTurns) {
        this.traversalMode = tMode;
        this.allowUTurns = allowUTurns;
    }

    /**
     * Runs the same test with each of the supported traversal modes
     */
    @Parameters(name = "{0} {1}")
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][]{
                {TraversalMode.NODE_BASED, false},
                {TraversalMode.EDGE_BASED, false},
                {TraversalMode.EDGE_BASED, true}
        });
    }

    @Override
    public RoutingAlgorithmFactory createFactory(GraphHopperStorage prepareGraph, AlgorithmOptions prepareOpts) {
        return new RoutingAlgorithmFactory() {
            @Override
            public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
                Weighting w = opts.getWeighting();
                if (traversalMode.isEdgeBased()) {
                    double uTurnCost = allowUTurns ? 40 : Double.POSITIVE_INFINITY;
                    w = new TurnWeighting(w, g.getTurnCostExtension(), uTurnCost);
                }
                return new AStarBidirection(g, w, traversalMode);
            }
        };
    }

    @Test
    public void testInitFromAndTo() {
        Graph g = createGHStorage(false);
        g.edge(0, 1, 1, true);
        updateDistancesFor(g, 0, 0.00, 0.00);
        updateDistancesFor(g, 1, 0.01, 0.01);

        final AtomicReference<SPTEntry> fromRef = new AtomicReference<>();
        final AtomicReference<SPTEntry> toRef = new AtomicReference<>();
        AStarBidirection astar = new AStarBidirection(g, new ShortestWeighting(carEncoder), traversalMode) {
            @Override
            public void init(int from, double fromWeight, int to, double toWeight) {
                super.init(from, fromWeight, to, toWeight);
                fromRef.set(currFrom);
                toRef.set(currTo);
            }
        };
        astar.init(0, 1, 1, 0.5);

        assertEquals(1, ((AStar.AStarEntry) fromRef.get()).weightOfVisitedPath, .1);
        assertEquals(787.3, fromRef.get().weight, .1);

        assertEquals(0.5, ((AStar.AStarEntry) toRef.get()).weightOfVisitedPath, .1);
        assertEquals(786.8, toRef.get().weight, .1);
    }
}
