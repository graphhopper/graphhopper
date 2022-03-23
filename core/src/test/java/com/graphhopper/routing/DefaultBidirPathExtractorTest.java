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

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 * @author easbar
 */
public class DefaultBidirPathExtractorTest {
    private final CarFlagEncoder carEncoder = new CarFlagEncoder(5, 5, 10);
    private final EncodingManager encodingManager = EncodingManager.create(carEncoder);

    BaseGraph createGraph() {
        return new BaseGraph.Builder(encodingManager).create();
    }

    @Test
    public void testExtract() {
        Graph graph = createGraph();
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(1, 2).setDistance(10));
        SPTEntry fwdEntry = new SPTEntry(0, 2, 0);
        fwdEntry.parent = new SPTEntry(EdgeIterator.NO_EDGE, 1, 10);
        SPTEntry bwdEntry = new SPTEntry(EdgeIterator.NO_EDGE, 2, 0);
        Path p = DefaultBidirPathExtractor.extractPath(graph, new FastestWeighting(carEncoder), fwdEntry, bwdEntry, 0);
        assertEquals(IntArrayList.from(1, 2), p.calcNodes());
        assertEquals(10, p.getDistance(), 1e-4);
    }

    @Test
    public void testExtract2() {
        // 1->2->3
        Graph graph = createGraph();
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(1, 2).setDistance(10));
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(2, 3).setDistance(20));
        // add some turn costs at node 2 where fwd&bwd searches meet. these costs have to be included in the
        // weight and the time of the path
        TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
        DecimalEncodedValue turnCostEnc = encodingManager.getDecimalEncodedValue(TurnCost.key(carEncoder.toString()));
        turnCostStorage.set(turnCostEnc, 0, 2, 1, 5);

        SPTEntry fwdEntry = new SPTEntry(0, 2, 0.6);
        fwdEntry.parent = new SPTEntry(EdgeIterator.NO_EDGE, 1, 0);

        SPTEntry bwdEntry = new SPTEntry(1, 2, 1.2);
        bwdEntry.parent = new SPTEntry(EdgeIterator.NO_EDGE, 3, 0);

        Path p = DefaultBidirPathExtractor.extractPath(graph, new FastestWeighting(carEncoder, new DefaultTurnCostProvider(carEncoder, turnCostStorage)), fwdEntry, bwdEntry, 0);
        p.setWeight(5 + 1.8);

        assertEquals(IntArrayList.from(1, 2, 3), p.calcNodes());
        assertEquals(30, p.getDistance(), 1e-4);
        assertEquals(5 + 1.8, p.getWeight(), 1e-4);
        assertEquals(5000 + 1800, p.getTime(), 1.e-6);
    }
}
