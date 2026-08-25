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

package com.graphhopper.util;

import com.graphhopper.jackson.Jackson;
import com.graphhopper.json.Statement;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class CustomModelTest {

    @Test
    public void testMergeComparisonKeys() {
        CustomModel truck = new CustomModel();
        truck.addToPriority(If("max_width < 3", MULTIPLY, "0"));
        CustomModel car = new CustomModel();
        car.addToPriority(If("max_width<2", MULTIPLY, "0"));
        CustomModel bike = new CustomModel();
        bike.addToPriority(If("max_weight<0.02", MULTIPLY, "0"));

        assertEquals(2, CustomModel.merge(bike, car).getPriority().size());
        assertEquals(1, bike.getPriority().size());
        assertEquals(1, car.getPriority().size());
    }

    @Test
    public void testMergeElse() {
        CustomModel truck = new CustomModel();
        truck.addToPriority(If("max_width < 3", MULTIPLY, "0"));

        CustomModel car = new CustomModel();
        car.addToPriority(If("max_width < 2", MULTIPLY, "0"));

        CustomModel merged = CustomModel.merge(truck, car);
        assertEquals(2, merged.getPriority().size());
        assertEquals(1, car.getPriority().size());
    }

    @Test
    public void testParametersFromJson() throws Exception {
        CustomModel cm = Jackson.newObjectMapper().readValue(
                "{\"parameters\": {\"power\": 120, \"mass\": 95.5, \"electric\": true}, \"speed\": [{\"if\": \"true\", \"limit_to\": \"car_average_speed\"}]}",
                CustomModel.class);
        assertEquals(120, cm.getParameters().get("power"));
        assertEquals(95.5, cm.getParameters().get("mass"));
        assertEquals(true, cm.getParameters().get("electric"));
        assertEquals(1, cm.getSpeed().size());
    }

    @Test
    public void testMergeParameters() {
        CustomModel base = new CustomModel().setParameter("power", 120.0).setParameter("mass", 95.0);
        CustomModel query = new CustomModel().setParameter("power", 100.0).setParameter("extra", 0.5);

        CustomModel merged = CustomModel.merge(base, query);
        assertEquals(100.0, merged.getParameters().get("power"));
        assertEquals(95.0, merged.getParameters().get("mass"));
        assertEquals(0.5, merged.getParameters().get("extra"));
        // the input models are unchanged
        assertEquals(120.0, base.getParameters().get("power"));
        assertEquals(2, query.getParameters().size());

        // the class key ignores the parameter values but not the types (they determine the field types)
        assertEquals(base.createClassKey(), new CustomModel(base).setParameter("power", 130.0).createClassKey());
        assertNotEquals(base.createClassKey(), merged.createClassKey());
        assertNotEquals(base.createClassKey(), new CustomModel(base).setParameter("power", true).createClassKey());
        assertNotEquals(base.toString(), new CustomModel(base).setParameter("power", 130.0).toString());
    }

    @Test
    public void testMergeEmptyModel() {
        CustomModel emptyCar = new CustomModel();
        CustomModel car = new CustomModel();
        car.addToPriority(If("road_class==primary", MULTIPLY, "0.5"));
        car.addToPriority(ElseIf("road_class==tertiary", MULTIPLY, "0.8"));

        Iterator<Statement> iter = CustomModel.merge(emptyCar, car).getPriority().iterator();
        assertEquals("0.5", iter.next().value());
        assertEquals("0.8", iter.next().value());

        iter = CustomModel.merge(car, emptyCar).getPriority().iterator();
        assertEquals("0.5", iter.next().value());
        assertEquals("0.8", iter.next().value());
    }
}
