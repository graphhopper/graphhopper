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

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.SPTEntry;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.Assert.assertEquals;

/**
 * Run some tests specific for {@link AStarBidirection}
 *
 * @author Peter Karich
 * @see RoutingAlgorithmTest for test cases covering standard node- and edge-based routing with this algorithm
 * @see EdgeBasedRoutingAlgorithmTest for test cases covering routing with turn costs with this algorithm
 */
public class AStarBidirectionTest {
    private final EncodingManager encodingManager = EncodingManager.create("car");
    private final FlagEncoder carEncoder = encodingManager.getEncoder("car");

    @Test
    public void testInitFromAndTo() {
        doTestInitFromAndTo(TraversalMode.NODE_BASED);
        doTestInitFromAndTo(TraversalMode.EDGE_BASED);
    }

    private void doTestInitFromAndTo(final TraversalMode traversalMode) {
        Graph g = new GraphBuilder(encodingManager).create();
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
