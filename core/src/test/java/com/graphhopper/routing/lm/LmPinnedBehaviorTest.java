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
package com.graphhopper.routing.lm;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GHDirectory;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins subtle landmark-preparation semantics that no other test covers, see docs/pinned-behavior.md.
 */
public class LmPinnedBehaviorTest {

    /**
     * The automatically estimated maximum weight (and thus the persisted 'factor') must be stable:
     * it depends on the exact java.util.Random(0) start-node sampling in
     * LandmarkStorage.estimateMaxWeight, the exploration of exactly 3 temporary test landmarks,
     * the final *1.008 slack and factor = maxWeight / PRECISION (2^16). The existing tests only
     * assert factor-multiplied weight products, which are insensitive to small factor drift
     * (e.g. a different Random implementation or slack). The exact factor decides how the
     * landmark weights are quantized on disk.
     */
    @Test
    public void estimatedFactorIsStable() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager encodingManager = new EncodingManager.Builder().add(speedEnc).add(Subnetwork.create("car")).build();
        BaseGraph graph = new BaseGraph.Builder(encodingManager).create();

        // same deterministic 15x15 grid as PrepareLandmarksTest.testLandmarkStorageAndRouting
        Random rand = new Random(0);
        int width = 15, height = 15;
        for (int hIndex = 0; hIndex < height; hIndex++) {
            for (int wIndex = 0; wIndex < width; wIndex++) {
                int node = wIndex + hIndex * width;
                double speed = 20 + rand.nextDouble() * 30;
                if (wIndex + 1 < width)
                    graph.edge(node, node + 1).set(speedEnc, speed);
                if (hIndex + 1 < height)
                    graph.edge(node, node + width).set(speedEnc, speed);
                updateDistancesFor(graph, node, -hIndex / 50.0, wIndex / 50.0);
            }
        }

        Directory dir = new GHDirectory("", DAType.RAM);
        Weighting weighting = new SpeedWeighting(speedEnc);
        LandmarkStorage store = new LandmarkStorage(graph, encodingManager, dir, new LMConfig("car", weighting), 5);
        store.setMinimumNodes(2);
        store.createLandmarks();

        // exact value produced by the pre-migration Java implementation; must never drift
        assertEquals(0.238849365234375, store.getFactor(), 0.0);
    }
}
