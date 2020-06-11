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

package com.graphhopper.routing.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CustomModelTest {

    static CustomModel setValue(CustomModel model, String op, String encodedValue, double value) {
        Map<String, Object> map = new HashMap<>();
        map.put(op + value, 0);
        model.getPriority().put(encodedValue, map);
        return model;
    }

    Object getValue(CustomModel model, String encodedValue) {
        Map map = (Map) model.getPriority().get(encodedValue);
        if (map == null || map.isEmpty())
            return null;
        return map.keySet().iterator().next();
    }

    @Test
    public void testMergeComparisonKeys() {
        CustomModel truck = setValue(new CustomModel(), "<", "max_width", 3);
        CustomModel car = setValue(new CustomModel(), "<", "max_width", 2);
        CustomModel bike = setValue(new CustomModel(), "<", "max_weight", 0.02);

        assertEquals("<2.0", getValue(CustomModel.merge(bike, car), "max_width"));
        assertNull(getValue(bike, "max_width"));
        assertNull(getValue(car, "max_weight"));

        assertEquals("<3.0", getValue(CustomModel.merge(car, truck), "max_width"));
        try {
            CustomModel.merge(truck, car);
            fail("car is incompatible to truck (base)");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("max_width: only use a comparison key with a bigger value than 3.0 but was 2.0"), ex.getMessage());
        }

        CustomModel car2 = setValue(new CustomModel(), ">", "max_width", 2);
        try {
            CustomModel.merge(truck, car2);
            fail("car is incompatible to car2 (base)");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("max_width: comparison keys must match but did not: "), ex.getMessage());
        }

        Map<String, Object> map = new HashMap<>();
        map.put("<2.0", 0.5);
        CustomModel customModel = new CustomModel();
        customModel.getPriority().put("max_width", map);
        try {
            CustomModel.merge(customModel, customModel);
            fail("models are incompatible");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("only blocking comparisons are allowed, but query was 0.5 and server side: 0.5"), ex.getMessage());
        }
    }

    @Test
    public void testMergeEmptyModel() {
        CustomModel emptyCar = new CustomModel();
        CustomModel car = new CustomModel();
        car.getPriority().put("road_class", createMap("primary", 2.0, "tertiary", 1.2));
        // empty entries means "extend base" profile, i.e. primary=1
        Map map = (Map) CustomModel.merge(emptyCar, car).getPriority().get("road_class");
        assertEquals(2.0, map.get("primary"));
        assertEquals(1.2, map.get("tertiary"));

        map = (Map) CustomModel.merge(car, emptyCar).getPriority().get("road_class");
        assertEquals(2.0, map.get("primary"));
        assertEquals(1.2, map.get("tertiary"));
    }

    @Test
    public void testMergeMap() {
        CustomModel truck = new CustomModel();
        truck.getPriority().put("road_class", createMap("primary", 1.5, "secondary", 2.0));
        CustomModel car = new CustomModel();
        car.getPriority().put("road_class", createMap("primary", 2.0, "tertiary", 1.2));

        Map map = (Map) CustomModel.merge(truck, car).getPriority().get("road_class");
        assertEquals(1.5 * 2.0, map.get("primary"));
        assertEquals(2.0, map.get("secondary"));
        assertEquals(1.2, map.get("tertiary"));

        // do not change argument of merge method
        assertEquals(1.5, (Double) ((Map) truck.getPriority().get("road_class")).get("primary"), .1);

        truck.getPriority().put("road_class", createMap("primary", "incompatible"));
        try {
            CustomModel.merge(car, truck);
            fail("we cannot merge this");
        } catch (Exception ex) {
        }
    }

    Map createMap(Object... objects) {
        Map map = new HashMap();
        for (int i = 0; i < objects.length; i += 2) {
            map.put(objects[i], objects[i + 1]);
        }
        return map;
    }
}