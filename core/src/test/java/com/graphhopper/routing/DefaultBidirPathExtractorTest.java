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
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 * @author easbar
 */
public class DefaultBidirPathExtractorTest {
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
    private final DecimalEncodedValue turnCostEnc = TurnCost.create("car", 10);
    private final EncodingManager encodingManager = EncodingManager.start().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();

    BaseGraph createGraph() {
        return new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
    }

    @Test
    public void testExtract() {
        Graph graph = createGraph();
        graph.edge(1, 2).setDistance(10).set(speedEnc, 60, 60);
        SPTEntry fwdEntry = new SPTEntry(0, 2, 0, new SPTEntry(1, 10));
        SPTEntry bwdEntry = new SPTEntry(2, 0);
        Path p = DefaultBidirPathExtractor.extractPath(graph, new SpeedWeighting(speedEnc), fwdEntry, bwdEntry, 0);
        assertEquals(IntArrayList.from(1, 2), p.calcNodes());
        assertEquals(10, p.getDistance(), 1e-4);
    }

    @Test
    public void testExtract2() {
        // 1->2->3
        Graph graph = createGraph();
        graph.edge(1, 2).setDistance(10).set(speedEnc, 60, 0);
        graph.edge(2, 3).setDistance(20).set(speedEnc, 60, 0);
        // add some turn costs at node 2 where fwd&bwd searches meet. these costs have to be included in the
        // weight and the time of the path
        TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
        turnCostStorage.set(turnCostEnc, 0, 2, 1, 5);

        SPTEntry fwdEntry = new SPTEntry(0, 2, 0.6, new SPTEntry(1, 0));
        SPTEntry bwdEntry = new SPTEntry(1, 2, 1.2, new SPTEntry(3, 0));

        Path p = DefaultBidirPathExtractor.extractPath(graph, new SpeedWeighting(speedEnc, turnCostEnc, turnCostStorage, Double.POSITIVE_INFINITY), fwdEntry, bwdEntry, 0);
        p.setWeight(5 + 1.8);

        assertEquals(IntArrayList.from(1, 2, 3), p.calcNodes());
        assertEquals(30, p.getDistance(), 1e-4);
        assertEquals(5 + 1.8, p.getWeight(), 1e-4);
        assertEquals(5000 + 1800, p.getTime(), 1.e-6);
    }
}
