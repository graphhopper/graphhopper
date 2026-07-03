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
package com.graphhopper.routing.weighting.custom.generate;

import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.routing.weighting.custom.CustomWeightingBackends;
import com.graphhopper.routing.weighting.custom.JaninoBackend;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SEMANTIC end-to-end proof for stage 5: the two PRE-GENERATED classes checked into
 * src/test/kotlin (written by {@link CustomWeightingSourceGenerator}, byte-locked by
 * {@link SourceGeneratorTest}) are compiled by the regular test build, registered in the
 * {@link GeneratedWeightingRegistry} and then compared edge-by-edge against the Janino
 * back-end with EXACT equality — weights and millis in both directions for every edge of a
 * randomized (seeded) graph, turn penalties for every incident edge pair,
 * calcMinWeightPerDistance and the distance_influence/heading_penalty defaulting.
 */
public class RegistryBackendDifferentialTest {

    static EncodingManager em;
    static BaseGraph graph;
    static CustomModel carModel;
    static CustomModel kitchenSinkModel;

    @BeforeAll
    static void setup() {
        em = Stage5Fixtures.createEncodingManager();
        graph = Stage5Fixtures.createRandomGraph(em, new Random(Stage5Fixtures.GRAPH_SEED));
        carModel = Stage5Fixtures.carModel();
        kitchenSinkModel = Stage5Fixtures.kitchenSinkModel();
        // what an app does at startup: register the build-time generated classes
        GeneratedWeightingRegistry.register(carModel, GeneratedCarCustomWeighting::new);
        GeneratedWeightingRegistry.register(kitchenSinkModel, GeneratedKitchenSinkCustomWeighting::new);
    }

    @AfterEach
    void restoreDefaultBackend() {
        CustomWeightingBackends.setDefault(JaninoBackend.INSTANCE);
    }

    @Test
    public void carModel_generatedClassMatchesJaninoExactly() {
        assertModelParity("car.json", carModel);
    }

    @Test
    public void kitchenSinkModel_generatedClassMatchesJaninoExactly() {
        assertModelParity("kitchen sink", kitchenSinkModel);
    }

    @Test
    public void registryBackendSelectableViaDefault() {
        CustomWeightingBackends.setDefault(RegistryBackend.INSTANCE);
        Weighting viaSeam = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, carModel);
        Weighting janino = new CustomWeighting(NO_TURN_COST_PROVIDER,
                JaninoBackend.INSTANCE.createParameters(carModel, em));
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            assertEquals(janino.calcEdgeWeight(iter, false), viaSeam.calcEdgeWeight(iter, false));
            assertEquals(janino.calcEdgeWeight(iter, true), viaSeam.calcEdgeWeight(iter, true));
        }
    }

    @Test
    public void unregisteredModelThrowsClearError() {
        CustomModel unregistered = new CustomModel();
        unregistered.addToSpeed(If("true", LIMIT, "car_average_speed"));
        unregistered.addToPriority(If("road_class == SECONDARY", MULTIPLY, "0.5"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RegistryBackend.INSTANCE.createParameters(unregistered, em));
        assertTrue(ex.getMessage().contains("No generated custom weighting registered"), ex.getMessage());
        assertTrue(ex.getMessage().contains("GeneratedWeightingRegistry.register"), ex.getMessage());
    }

    // ------------------------------------------------------------------

    /** Same shape as ClosureBackendDifferentialTest.assertModelParity, with the registry backend. */
    static void assertModelParity(String info, CustomModel model) {
        CustomWeighting.Parameters pj = JaninoBackend.INSTANCE.createParameters(model, em);
        CustomWeighting.Parameters pr = RegistryBackend.INSTANCE.createParameters(model, em);

        assertEquals(pj.getDistanceInfluence(), pr.getDistanceInfluence(), info);
        assertEquals(pj.getHeadingPenaltySeconds(), pr.getHeadingPenaltySeconds(), info);

        Weighting wj = new CustomWeighting(NO_TURN_COST_PROVIDER, pj);
        Weighting wr = new CustomWeighting(NO_TURN_COST_PROVIDER, pr);

        // exercises the max speed/priority calculators
        assertEquals(wj.calcMinWeightPerDistance(), wr.calcMinWeightPerDistance(), () -> info + ": calcMinWeightPerDistance differs");

        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            for (boolean reverse : new boolean[]{false, true}) {
                double weightJ = wj.calcEdgeWeight(iter, reverse);
                double weightR = wr.calcEdgeWeight(iter, reverse);
                int edgeId = iter.getEdge();
                boolean rev = reverse;
                assertEquals(weightJ, weightR,
                        () -> info + ": weight differs at edge " + edgeId + " reverse=" + rev);
                assertEquals(wj.calcEdgeMillis(iter, reverse), wr.calcEdgeMillis(iter, reverse),
                        () -> info + ": millis differ at edge " + edgeId + " reverse=" + rev);
            }
        }

        if (!model.getTurnPenalty().isEmpty()) {
            CustomWeighting.TurnPenaltyMapping tj = pj.getTurnPenaltyMapping();
            CustomWeighting.TurnPenaltyMapping tr = pr.getTurnPenaltyMapping();
            EdgeIntAccess edgeIntAccess = graph.getEdgeAccess();
            EdgeExplorer explorer = graph.createEdgeExplorer();
            for (int node = 0; node < Stage5Fixtures.NODES; node++) {
                List<Integer> edgeIds = new ArrayList<>();
                EdgeIterator it = explorer.setBaseNode(node);
                while (it.next()) edgeIds.add(it.getEdge());
                for (int inEdge : edgeIds) {
                    for (int outEdge : edgeIds) {
                        double penaltyJ = tj.get(graph, edgeIntAccess, inEdge, node, outEdge);
                        double penaltyR = tr.get(graph, edgeIntAccess, inEdge, node, outEdge);
                        int viaNode = node;
                        assertEquals(penaltyJ, penaltyR, () -> info + ": turn penalty differs at "
                                + inEdge + "->" + viaNode + "->" + outEdge);
                    }
                }
            }
        }
    }
}
