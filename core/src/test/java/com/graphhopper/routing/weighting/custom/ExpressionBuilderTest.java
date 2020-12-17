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

import com.graphhopper.json.Clause;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.ev.RoadClass.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpressionBuilderTest {

    CarFlagEncoder encoder;
    Graph graph;
    EncodingManager encodingManager;
    EnumEncodedValue<RoadClass> roadClassEnc;
    DecimalEncodedValue avgSpeedEnc;

    @BeforeEach
    void setup() {
        encoder = new CarFlagEncoder();
        encodingManager = EncodingManager.create(encoder);
        graph = new GraphBuilder(encodingManager).create();
        avgSpeedEnc = encoder.getAverageSpeedEnc();
        roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
    }

    @Test
    void setPriorityForRoadClass() {
        CustomModel customModel = new CustomModel();
        customModel.getPriority().add(Clause.createIf("road_class == PRIMARY", 0.5));
        SpeedAndAccessProvider speedAndAccessProvider = ExpressionBuilder.create(customModel, encodingManager,
                encoder.getMaxSpeed(), avgSpeedEnc);

        GraphHopperStorage graph = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(100).set(roadClassEnc, RoadClass.PRIMARY);
        EdgeIteratorState edge2 = graph.edge(1, 2).setDistance(100).set(roadClassEnc, RoadClass.SECONDARY);

        assertEquals(0.5, speedAndAccessProvider.getPriority(edge1, false), 1.e-6);
        assertEquals(1.0, speedAndAccessProvider.getPriority(edge2, false), 1.e-6);
    }

    @Test
    void testPriority() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avgSpeedEnc, 80).set(encoder.getAccessEnc(), true, true);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avgSpeedEnc, 70).set(encoder.getAccessEnc(), true, true);
        EdgeIteratorState tertiary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, TERTIARY).set(avgSpeedEnc, 70).set(encoder.getAccessEnc(), true, true);

        CustomModel customModel = new CustomModel();
        customModel.getPriority().add(Clause.createIf("road_class == PRIMARY", 0.5));
        customModel.getPriority().add(Clause.createElseIf("road_class == SECONDARY", 0.7));
        customModel.getPriority().add(Clause.createElse(0.9));
        customModel.getPriority().add(Clause.createIf("road_environment != FERRY", 0.8));

        SpeedAndAccessProvider speedAndAccessProvider = ExpressionBuilder.create(customModel, encodingManager,
                encoder.getMaxSpeed(), avgSpeedEnc);

        assertEquals(0.5 * 0.8, speedAndAccessProvider.getPriority(primary, false), 0.01);
        assertEquals(0.7 * 0.8, speedAndAccessProvider.getPriority(secondary, false), 0.01);
        assertEquals(0.9 * 0.8, speedAndAccessProvider.getPriority(tertiary, false), 0.01);

        // force integer value
        customModel = new CustomModel();
        customModel.getPriority().add(Clause.createIf("road_class == PRIMARY", 1));
        customModel.getPriority().add(Clause.createIf("road_class == SECONDARY", 0.9));
        speedAndAccessProvider = ExpressionBuilder.create(customModel, encodingManager,
                encoder.getMaxSpeed(), avgSpeedEnc);
        assertEquals(1, speedAndAccessProvider.getPriority(primary, false), 0.01);
        assertEquals(0.9, speedAndAccessProvider.getPriority(secondary, false), 0.01);
    }

    @Test
    void testIllegalOrder() {
        CustomModel customModel = new CustomModel();
        customModel.getPriority().add(Clause.createElse(0.9));
        customModel.getPriority().add(Clause.createIf("road_environment != FERRY", 0.8));
        assertThrows(IllegalArgumentException.class, () -> ExpressionBuilder.create(customModel, encodingManager,
                encoder.getMaxSpeed(), avgSpeedEnc));

        CustomModel customModel2 = new CustomModel();
        customModel2.getPriority().add(Clause.createElseIf("road_environment != FERRY", 0.9));
        customModel2.getPriority().add(Clause.createIf("road_class != PRIMARY", 0.8));
        assertThrows(IllegalArgumentException.class, () -> ExpressionBuilder.create(customModel2, encodingManager,
                encoder.getMaxSpeed(), avgSpeedEnc));
    }
}