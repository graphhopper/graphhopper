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

import com.graphhopper.routing.weighting.custom.CustomWeighting;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CustomModelTest {

    @Test
    public void testTooBigFactor() {
        CustomModel truck = new CustomModel();
        truck.getPriority().put("max_width < 3", 10);
        // it is ok server-side CustomModel
        assertEquals(1, CustomModel.merge(truck, new CustomModel()).getPriority().size());
        // but not for query CustomModel
        assertThrows(IllegalArgumentException.class, () -> CustomModel.merge(new CustomModel(), truck));
    }

    @Test
    public void testMergeComparisonKeys() {
        CustomModel truck = new CustomModel();
        truck.getPriority().put("max_width < 3", 0);
        CustomModel car = new CustomModel();
        car.getPriority().put("max_width<2", 0);
        CustomModel bike = new CustomModel();
        bike.getPriority().put("max_weight<0.02", 0);

        assertEquals(2, CustomModel.merge(bike, car).getPriority().size());
        assertEquals(1, bike.getPriority().size());
        assertEquals(1, car.getPriority().size());
    }

    @Test
    public void testFirstMatch() {
        CustomModel truck = new CustomModel();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("max_width < 3", 0);
        truck.getPriority().put(CustomWeighting.FIRST_MATCH, map);

        CustomModel car = new CustomModel();
        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("max_width < 2", 0);
        car.getPriority().put(CustomWeighting.FIRST_MATCH, map2);
        CustomModel merged = CustomModel.merge(truck, car);
        assertEquals(1, merged.getPriority().size());
        assertEquals(2, ((Map) merged.getPriority().get(CustomWeighting.FIRST_MATCH)).size());
        assertEquals(1, CustomModel.merge(new CustomModel(), car).getPriority().size());
        assertEquals(1, CustomModel.merge(car, new CustomModel()).getPriority().size());
    }

    @Test
    public void testMergeEmptyModel() {
        CustomModel emptyCar = new CustomModel();
        CustomModel car = new CustomModel();
        car.getPriority().putAll(createMap("road_class==primary", 0.5, "road_class==tertiary", 0.8));

        Iterator<Map.Entry<String, Object>> iter = CustomModel.merge(emptyCar, car).getPriority().entrySet().iterator();
        assertEquals(0.5, iter.next().getValue());
        assertEquals(0.8, iter.next().getValue());

        iter = CustomModel.merge(car, emptyCar).getPriority().entrySet().iterator();
        assertEquals(0.5, iter.next().getValue());
        assertEquals(0.8, iter.next().getValue());
    }

    @Test
    public void testMergeMap() {
        CustomModel truck = new CustomModel();
        truck.getPriority().putAll(createMap("road_class==primary", 0.6, "road_class==secondary", 0.5));
        CustomModel car = new CustomModel();
        car.getPriority().putAll(createMap("road_class==primary", 0.5, "road_class==tertiary", 0.8));

        Iterator<Map.Entry<String, Object>> iter = CustomModel.merge(truck, car).getPriority().entrySet().iterator();
        assertEquals(0.6 * 0.5, iter.next().getValue());
        assertEquals(0.5, iter.next().getValue());
        assertEquals(0.8, iter.next().getValue());

        // do not change the truck variable - it is only an argument of the merge method
        assertEquals(0.6, truck.getPriority().entrySet().iterator().next().getValue());

        truck.getPriority().putAll(createMap("road_class==primary", "incompatible"));
        assertThrows(IllegalArgumentException.class, () -> CustomModel.merge(car, truck));
    }

    Map createMap(Object... objects) {
        Map map = new LinkedHashMap();
        for (int i = 0; i < objects.length; i += 2) {
            map.put(objects[i], objects[i + 1]);
        }
        return map;
    }
}