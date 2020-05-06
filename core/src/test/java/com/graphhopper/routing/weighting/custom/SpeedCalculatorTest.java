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
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpeedCalculatorTest {

    private final EncodingManager em;
    private final EdgeIteratorState edge;

    SpeedCalculatorTest() {
        // in this test we use the same edge for every test and check the calculated speed for different custom models
        // and depending on additional properties of the edge we set in the tests
        em = EncodingManager.create("car");
        edge = new GraphBuilder(em).create().edge(0, 1, 1000, true);
    }

    @Test
    public void maxSpeedFallback() {
        // default speed for car is 60km/h
        assertEquals(60, calcSpeed(edge, new CustomModel()));
        // use global fallback to limit speed
        assertEquals(30, calcSpeed(edge, new CustomModel().setMaxSpeedFallback(30.0)));
    }

    @Test
    public void maxSpeed() {
        // here we use max_speed to limit speed
        CustomModel model = new CustomModel();
        Map<String, Object> map = new HashMap<>();
        map.put("*", 25);
        model.getMaxSpeed().put(RoadClass.KEY, map);
        assertEquals(25, calcSpeed(edge, model));
        // ... but setting it to a higher value than the default has no effect
        map.put("*", 70);
        assertEquals(60, calcSpeed(edge, model));
        // also our edge might or might not be affected
        EnumEncodedValue<RoadClass> roadClass = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        edge.set(roadClass, RoadClass.PRIMARY);
        model.getMaxSpeed().clear();
        assertEquals(60, calcSpeed(edge, new CustomModel()));
        map.clear();
        map.put(RoadClass.PRIMARY.toString(), 10);
        model.getMaxSpeed().put(RoadClass.KEY, map);
        assertEquals(10, calcSpeed(edge, model));
    }

    @Test
    public void invalidMaxSpeed() {
        CustomModel model = new CustomModel();
        Map<String, Object> map = new HashMap<>();
        map.put("*", 300);
        model.getMaxSpeed().put(RoadClass.KEY, map);
        try {
            assertEquals(60, calcSpeed(edge, model));
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("max_speed.road_class cannot be bigger than 140.0, was 300.0"), e.getMessage());
        }
        // negative values are not allowed
        try {
            map.put("*", -5);
            assertEquals(60, calcSpeed(edge, model));
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("max_speed.road_class cannot be smaller than 0.0, was -5.0"), e.getMessage());
        }
    }

    @Test
    public void maxSpeedMultiple() {
        EnumEncodedValue<RoadClass> roadClass = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<RoadEnvironment> roadEnvironment = em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        edge.set(roadClass, RoadClass.PRIMARY);
        edge.set(roadEnvironment, RoadEnvironment.BRIDGE);
        Map<String, Object> roadClassMap = new HashMap<>();
        roadClassMap.put(RoadClass.PRIMARY.toString(), 40);
        Map<String, Object> roadEnvironmentMap = new HashMap<>();
        roadEnvironmentMap.put(RoadEnvironment.BRIDGE.toString(), 20);
        CustomModel model = new CustomModel();
        model.getMaxSpeed().put(RoadClass.KEY, roadClassMap);
        model.getMaxSpeed().put(RoadEnvironment.KEY, roadEnvironmentMap);
        // the more restrictive max speed 'wins'
        assertEquals(20, calcSpeed(edge, model));
    }

    @Test
    public void speedFactor() {
        // here we use speed_factor to adjust speed
        CustomModel model = new CustomModel();
        Map<String, Object> map = new HashMap<>();
        map.put("*", 0.1);
        model.getSpeedFactor().put(RoadClass.KEY, map);
        assertEquals(6, calcSpeed(edge, model));
        // our edge might or might not be affected
        EnumEncodedValue<RoadClass> roadClass = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        edge.set(roadClass, RoadClass.PRIMARY);
        model.getSpeedFactor().clear();
        assertEquals(60, calcSpeed(edge, new CustomModel()));
        map.clear();
        map.put(RoadClass.PRIMARY.toString(), 0.2);
        model.getSpeedFactor().put(RoadClass.KEY, map);
        assertEquals(12, calcSpeed(edge, model));
    }

    @Test
    public void invalidSpeedFactor() {
        CustomModel model = new CustomModel();
        Map<String, Object> map = new HashMap<>();
        map.put("*", 1.1);
        model.getSpeedFactor().put(RoadClass.KEY, map);
        try {
            assertEquals(6, calcSpeed(edge, model));
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("speed_factor.road_class cannot be bigger than 1.0, was 1.1"), e.getMessage());
        }
    }

    @Test
    public void speedFactorMultiple() {
        // use speed_factor to adjust speed
        EnumEncodedValue<RoadClass> roadClass = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<RoadEnvironment> roadEnvironment = em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        edge.set(roadClass, RoadClass.PRIMARY);
        edge.set(roadEnvironment, RoadEnvironment.BRIDGE);
        Map<String, Object> roadClassMap = new HashMap<>();
        roadClassMap.put(RoadClass.PRIMARY.toString(), 0.7);
        Map<String, Object> roadEnvironmentMap = new HashMap<>();
        roadEnvironmentMap.put(RoadEnvironment.BRIDGE.toString(), 0.5);
        CustomModel model = new CustomModel();
        model.getSpeedFactor().put(RoadClass.KEY, roadClassMap);
        model.getSpeedFactor().put(RoadEnvironment.KEY, roadEnvironmentMap);
        assertEquals(0.35 * 60, calcSpeed(edge, model));
        // the global max speed fallback can still overwrite our speed factor settings
        model.setMaxSpeedFallback(5.0);
        assertEquals(5, calcSpeed(edge, model));
    }

    private double calcSpeed(EdgeIteratorState edge, CustomModel model) {
        FlagEncoder encoder = em.fetchEdgeEncoders().iterator().next();
        SpeedCalculator speedCalculator = new SpeedCalculator(encoder.getMaxSpeed(), model, encoder.getAverageSpeedEnc(), em);
        return speedCalculator.calcSpeed(edge, false);
    }

}