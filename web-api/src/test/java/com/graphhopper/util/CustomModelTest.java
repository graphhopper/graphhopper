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
import java.util.Map;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals(120.0, cm.getParameters().get("power"));
        assertEquals(95.5, cm.getParameters().get("mass"));
        assertEquals(true, cm.getParameters().get("electric"));
        assertEquals(1, cm.getSpeed().size());
    }

    @Test
    public void testParameterRangeFromJson() throws Exception {
        CustomModel cm = Jackson.newObjectMapper().readValue(
                "{\"parameters\": {\"width\": {\"value\": 3, \"min\": 2, \"max\": 5}, \"power\": 120}}",
                CustomModel.class);
        assertEquals(3.0, cm.getParameters().get("width"));
        assertEquals(2, cm.getParameterRange("width").min);
        assertEquals(5, cm.getParameterRange("width").max);
        // without an explicit range [0, Infinity) is used
        assertEquals(0, cm.getParameterRange("power").min);
        assertEquals(Double.POSITIVE_INFINITY, cm.getParameterRange("power").max);

        Exception ex = assertThrows(Exception.class, () -> Jackson.newObjectMapper().readValue(
                "{\"parameters\": {\"width\": {\"value\": 7, \"min\": 2, \"max\": 5}}}", CustomModel.class));
        assertTrue(ex.getMessage().contains("within its range"), ex.getMessage());

        // adding a range keeps toString identical, so it requires no re-import or new preparation
        CustomModel bare = Jackson.newObjectMapper().readValue("{\"parameters\": {\"weight\": 5}}", CustomModel.class);
        CustomModel ranged = Jackson.newObjectMapper().readValue(
                "{\"parameters\": {\"weight\": {\"value\": 5, \"min\": 1, \"max\": 10}}}", CustomModel.class);
        assertEquals(bare.toString(), ranged.toString());
    }

    @Test
    public void testCheckParameterOverrides() {
        CustomModel base = new CustomModel().setParameter("width", 3.0, 2, 5).setParameter("push", true);
        CustomModel.checkParameterOverrides(base, new CustomModel().setParameter("width", 4.0));

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModel.checkParameterOverrides(base, new CustomModel().setParameter("height", 2.0)));
        assertTrue(ex.getMessage().contains("not defined in the server-side custom model"), ex.getMessage());
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModel.checkParameterOverrides(base, new CustomModel().setParameter("width", true)));
        assertTrue(ex.getMessage().contains("same type"), ex.getMessage());
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModel.checkParameterOverrides(base, new CustomModel().setParameter("push", 1.0)));
        assertTrue(ex.getMessage().contains("same type"), ex.getMessage());
        // a String value (e.g. a quoted number in JSON) is rejected here and not deep in the parser
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModel.checkParameterOverrides(base, new CustomModel().setParameters(Map.of("width", "4"))));
        assertTrue(ex.getMessage().contains("same type"), ex.getMessage());
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModel.checkParameterOverrides(base, new CustomModel().setParameter("width", 7.0)));
        assertTrue(ex.getMessage().contains("within its range"), ex.getMessage());
        // a range can only be specified in a server-side custom model
        ex = assertThrows(IllegalArgumentException.class,
                () -> CustomModel.checkParameterOverrides(base, new CustomModel().setParameter("width", 4.0, 2, 5)));
        assertTrue(ex.getMessage().contains("server-side"), ex.getMessage());
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
