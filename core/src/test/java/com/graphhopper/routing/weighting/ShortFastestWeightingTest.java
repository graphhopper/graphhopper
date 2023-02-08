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
package com.graphhopper.routing.weighting;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.core.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Peter Karich
 */
public class ShortFastestWeightingTest {
    private final BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
    private final EncodingManager encodingManager = EncodingManager.start().add(accessEnc).add(speedEnc).build();
    private final BaseGraph graph = new BaseGraph.Builder(encodingManager).create();

    @Test
    public void testShort() {
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(10);
        GHUtility.setSpeed(50, 0, accessEnc, speedEnc, edge);
        Weighting instance = new ShortFastestWeighting(accessEnc, speedEnc, null, new PMap("short_fastest.distance_factor=0.03"), TurnCostProvider.NO_TURN_COST_PROVIDER);
        assertEquals(1.02, instance.calcEdgeWeight(edge, false), 1e-6);

        // more influence from distance
        instance = new ShortFastestWeighting(accessEnc, speedEnc, null, new PMap("short_fastest.distance_factor=0.1"), TurnCostProvider.NO_TURN_COST_PROVIDER);
        assertEquals(1.72, instance.calcEdgeWeight(edge, false), 1e-6);
    }

    @Test
    public void testTooSmall() {
        assertThrows(Exception.class, () -> new ShortFastestWeighting(accessEnc, speedEnc, null, new PMap("short_fastest.distance_factor=0|short_fastest.time_factor=0"),
                TurnCostProvider.NO_TURN_COST_PROVIDER));
    }
}
