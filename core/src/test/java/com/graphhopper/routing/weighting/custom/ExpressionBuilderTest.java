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

import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionBuilderTest {
    @Test
    void setPriorityForRoadClass() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(encoder);
        CustomModel customModel = new CustomModel();
        customModel.getPriority().put("road_class == PRIMARY", 0.5);
        // todo: can we get rid of this line here?
        double maxSpeedFallback = customModel.getMaxSpeedFallback() == null ? encoder.getMaxSpeed() : customModel.getMaxSpeedFallback();
        SpeedAndAccessProvider speedAndAccessProvider = ExpressionBuilder.create(customModel, encodingManager, encoder.getMaxSpeed(), maxSpeedFallback, encoder.getAverageSpeedEnc());

        EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        GraphHopperStorage graph = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(100).set(roadClassEnc, RoadClass.PRIMARY);
        EdgeIteratorState edge2 = graph.edge(1, 2).setDistance(100).set(roadClassEnc, RoadClass.SECONDARY);

        assertEquals(0.5, speedAndAccessProvider.getPriority(edge1, false), 1.e-6);
        assertEquals(1.0, speedAndAccessProvider.getPriority(edge2, false), 1.e-6);
    }
}