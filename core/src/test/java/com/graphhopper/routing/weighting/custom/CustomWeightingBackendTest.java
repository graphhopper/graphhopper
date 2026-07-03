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

package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.CustomModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static org.junit.jupiter.api.Assertions.*;

class CustomWeightingBackendTest {

    @AfterEach
    void restoreDefaultBackend() {
        // no global leakage into other tests
        CustomWeightingBackends.setDefault(JaninoBackend.INSTANCE);
    }

    @Test
    void defaultBackendIsJanino() {
        assertSame(JaninoBackend.INSTANCE, CustomWeightingBackends.getDefault());
    }

    @Test
    void customBackendIsUsed() {
        CustomWeighting.Parameters sentinel = new CustomWeighting.Parameters(
                (edge, reverse) -> 10, () -> 10,
                (edge, reverse) -> 1, () -> 1,
                (graph, edgeIntAccess, inEdge, viaNode, outEdge) -> 0,
                0, 0);
        AtomicInteger calls = new AtomicInteger();
        // the seam accepts a plain Java lambda
        CustomWeightingBackends.setDefault((customModel, lookup) -> {
            calls.incrementAndGet();
            return sentinel;
        });

        EncodingManager em = new EncodingManager.Builder()
                .add(VehicleAccess.create("car")).add(VehicleSpeed.create("car", 5, 5, false)).build();
        CustomModel customModel = new CustomModel();
        customModel.addToSpeed(If("true", LIMIT, "100"));

        assertSame(sentinel, CustomModelParser.createWeightingParameters(customModel, em));
        assertEquals(1, calls.get());

        // the public createWeighting entry point goes through the seam as well
        CustomWeighting weighting = CustomModelParser.createWeighting(em, TurnCostProvider.NO_TURN_COST_PROVIDER, customModel);
        assertNotNull(weighting);
        assertEquals(2, calls.get());
    }
}
