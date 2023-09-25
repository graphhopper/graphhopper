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

package com.graphhopper.reader.osm;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrepareDeadEndsTest {

    private BooleanEncodedValue subnetworkEnc;
    private BooleanEncodedValue deadEndEnc;
    private DecimalEncodedValue speedEnc;
    private BaseGraph graph;
    private Weighting weighting;
    private PrepareDeadEnds prepareDeadEnds;

    @BeforeEach
    void setup() {
        subnetworkEnc = Subnetwork.create("car");
        deadEndEnc = DeadEnd.create("car");
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        EncodingManager encodingManager = EncodingManager.start()
                .add(speedEnc)
                .add(subnetworkEnc)
                .addTurnCostEncodedValue(deadEndEnc)
                .build();
        graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        prepareDeadEnds = new PrepareDeadEnds(graph);
        weighting = new SpeedWeighting(speedEnc);
    }


    @Test
    void basic() {
        // 0 - 1 - 2 - 3
        graph.edge(0, 1).set(speedEnc, 10, 10);
        graph.edge(1, 2).set(speedEnc, 10, 10);
        graph.edge(2, 3).set(speedEnc, 10, 10);
        prepareDeadEnds.findDeadEndUTurns(weighting, deadEndEnc, subnetworkEnc);
        assertFalse(graph.getTurnCostStorage().get(deadEndEnc, 1, 2, 1));
        assertTrue(graph.getTurnCostStorage().get(deadEndEnc, 2, 3, 2));
        assertFalse(graph.getTurnCostStorage().get(deadEndEnc, 2, 2, 2));
    }

    @Test
    void oneway() {
        // 0 - 1 - 2 - 3
        //     |       |
        //     --------4
        graph.edge(0, 1).set(speedEnc, 10, 10);
        graph.edge(1, 2).set(speedEnc, 10, 10);
        graph.edge(2, 3).set(speedEnc, 10, 10);
        graph.edge(1, 4).set(speedEnc, 10, 10);
        graph.edge(4, 3).set(speedEnc, 10, 0);
        prepareDeadEnds.findDeadEndUTurns(weighting, deadEndEnc, subnetworkEnc);
        // arriving at 3 coming from 2 is a dead-end bc 4-3 is a one-way
        // but arriving at 3 coming from 4 is not
        assertTrue(graph.getTurnCostStorage().get(deadEndEnc, 2, 3, 2));
        assertFalse(graph.getTurnCostStorage().get(deadEndEnc, 4, 3, 4));
    }

    @Test
    void inaccessibleEdge() {
        // 0 - 1 - 2
        //  \--    |
        //     \---3
        graph.edge(0, 1).set(speedEnc, 10, 10);
        graph.edge(1, 2).set(speedEnc, 10, 10);
        // 2-3 is not accessible, so there is a dead-end at node 2. This is often the case when a
        // residential road ends in a path for example
        graph.edge(2, 3).set(speedEnc, 0, 0);
        graph.edge(0, 3).set(speedEnc, 10, 10);
        prepareDeadEnds.findDeadEndUTurns(weighting, deadEndEnc, subnetworkEnc);
        assertTrue(graph.getTurnCostStorage().get(deadEndEnc, 1, 2, 1));
        assertFalse(graph.getTurnCostStorage().get(deadEndEnc, 1, 1, 1));
    }

    @Test
    void subnetwork() {
        // 0 - 1 - 2 - 3
        graph.edge(0, 1).set(speedEnc, 10, 10);
        graph.edge(1, 2).set(speedEnc, 10, 10);
        // Here edge 2->3 is a oneway dead-end and thus forms another subnetwork.
        // In this case there is also a dead-end at node 2, because going to 2-3 is not
        // an option.
        graph.edge(2, 3).set(speedEnc, 10, 0).set(subnetworkEnc, true);
        prepareDeadEnds.findDeadEndUTurns(weighting, deadEndEnc, subnetworkEnc);
        assertTrue(graph.getTurnCostStorage().get(deadEndEnc, 1, 2, 1));
        assertFalse(graph.getTurnCostStorage().get(deadEndEnc, 1, 1, 1));
        assertFalse(graph.getTurnCostStorage().get(deadEndEnc, 2, 2, 2));
    }

}
