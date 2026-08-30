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

import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultWeightingFactoryTest {

    @Test
    public void testParameterOverridesAreValidated() {
        DecimalEncodedValue avSpeedEnc = VehicleSpeed.create("car", 5, 5, true);
        EncodingManager encodingManager = new EncodingManager.Builder().add(avSpeedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(encodingManager).create();

        CustomModel customModel = new CustomModel().
                setParameter("a", 0.9, 0.5, 1).setParameter("b", 0.0, 0, 0.6);
        customModel.addToSpeed(If("true", LIMIT, "car_average_speed"));
        customModel.addToPriority(If("true", MULTIPLY, "p_a - p_b"));
        Profile profile = new Profile("car").setCustomModel(customModel);
        DefaultWeightingFactory factory = new DefaultWeightingFactory(graph, encodingManager);
        // compile and cache the class with valid values
        factory.createWeighting(profile, new PMap(), false);

        factory.createWeighting(profile, new PMap().putObject(CustomModel.KEY,
                new CustomModel().setParameter("a", 0.7).setParameter("b", 0.1)), false);

        // in-range values, but the combination gives a negative priority - rejected despite the cache hit
        PMap hints = new PMap().putObject(CustomModel.KEY,
                new CustomModel().setParameter("a", 0.5).setParameter("b", 0.6));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.createWeighting(profile, hints, false));
        assertTrue(ex.getMessage().contains("negative"), ex.getMessage());
    }
}
