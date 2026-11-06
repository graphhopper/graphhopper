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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
        assertEquals(120.0, cm.getParameters().get("power").value());
        assertEquals(95.5, cm.getParameters().get("mass").value());
        assertEquals(true, cm.getParameters().get("electric").value());
        assertEquals(1, cm.getSpeed().size());
    }

    @Test
    public void testParameterRangeFromJson() throws Exception {
        CustomModel cm = Jackson.newObjectMapper().readValue(
                "{\"parameters\": {\"width\": {\"value\": 3, \"min\": 2, \"max\": 5}, \"power\": 120}}",
                CustomModel.class);
        assertEquals(3.0, cm.getParameters().get("width").value());
        assertEquals(2, cm.getParameters().get("width").min());
        assertEquals(5, cm.getParameters().get("width").max());
        // without an explicit range [0, Infinity) is used
        assertEquals(0, cm.getParameters().get("power").min());
        assertEquals(Double.POSITIVE_INFINITY, cm.getParameters().get("power").max());
        // the range survives serialization
        assertTrue(Jackson.newObjectMapper().writeValueAsString(cm).contains("\"width\":{\"value\":3.0,\"min\":2.0,\"max\":5.0}"));

        Exception ex = assertThrows(Exception.class, () -> Jackson.newObjectMapper().readValue(
                "{\"parameters\": {\"width\": {\"value\": 7, \"min\": 2, \"max\": 5}}}", CustomModel.class));
        assertTrue(ex.getMessage().contains("within its range"), ex.getMessage());

        // adding a range keeps toString identical, so it requires no re-import or new preparation
        CustomModel bare = Jackson.newObjectMapper().readValue("{\"parameters\": {\"weight\": 5}}", CustomModel.class);
        CustomModel ranged = Jackson.newObjectMapper().readValue(
                "{\"parameters\": {\"weight\": {\"value\": 5, \"min\": 1, \"max\": 10}}}", CustomModel.class);
        assertEquals(bare.toString(), ranged.toString());
        // only the value is printed, unlike the default toString of a record, as it is part of the profile version
        assertTrue(ranged.toString().endsWith("|parameters={weight=5.0}"), ranged.toString());
        assertEquals("5.0", new CustomModel.Parameter(5.0, 1, 10).toString());
        assertEquals("true", new CustomModel.Parameter(true).toString());
    }

    @Test
    public void testSetParameterFromJson() throws Exception {
        CustomModel cm = Jackson.newObjectMapper().readValue("{\"parameters\": {\"ways\": [5, 3, 5]}}", CustomModel.class);
        assertEquals(List.of(3, 5), cm.getParameters().get("ways").value());
        assertTrue(Jackson.newObjectMapper().writeValueAsString(cm).contains("\"ways\":[3,5]"));
        // the order does not change toString, which is part of the profile version
        assertEquals(cm.toString(), new CustomModel().setParameter("ways", List.of(3, 5)).toString());
        // the class key ignores the members
        assertEquals(cm.createClassKey(), new CustomModel().setParameter("ways", List.of()).createClassKey());
        assertNotEquals(cm.createClassKey(), new CustomModel().setParameter("ways", 3).createClassKey());
        assertEquals("[3, 5]", cm.getParameters().get("ways").toString());

        // a large set is printed as its size and digest, which must not change as it is part of the profile version
        List<Integer> large = IntStream.range(0, 21).boxed().toList();
        assertEquals("set(size=21, sha256=5f87bef3c8f6057a)", new CustomModel.Parameter(large).toString());
        List<Integer> changed = IntStream.rangeClosed(1, 21).boxed().toList();
        assertNotEquals(new CustomModel().setParameter("ways", large).toString(), new CustomModel().setParameter("ways", changed).toString());
        // lists with the same hashCode, like [1, 100] and [2, 69], still differ
        List<Integer> hundreds = IntStream.rangeClosed(1, 21).map(i -> 100 * i).boxed().toList();
        List<Integer> collision = new ArrayList<>(hundreds);
        collision.set(0, 101);
        collision.set(1, 169);
        assertEquals(hundreds.hashCode(), collision.hashCode());
        assertNotEquals(new CustomModel.Parameter(hundreds).toString(), new CustomModel.Parameter(collision).toString());

        for (String invalid : List.of("[1.5]", "[4294967296]", "[2147483648]", "[-2147483649]", "[\"1\"]", "[true]", "[null]", "[[1]]")) {
            Exception ex = assertThrows(Exception.class, () -> Jackson.newObjectMapper().readValue(
                    "{\"parameters\": {\"ways\": " + invalid + "}}", CustomModel.class));
            assertTrue(ex.getMessage().contains("whole numbers"), ex.getMessage());
        }
        assertEquals(List.of(Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE),
                new CustomModel().setParameter("ways", List.of(Integer.MAX_VALUE, 0L, -1, Integer.MIN_VALUE)).getParameters().get("ways").value());
        // a range is only for numbers
        Exception ex = assertThrows(IllegalArgumentException.class, () -> new CustomModel().setParameter("ways", Map.of("value", List.of(1))));
        assertTrue(ex.getMessage().contains("a number 'value' is required"), ex.getMessage());

        // a set is printed in full up to 20 members
        List<Integer> twenty = IntStream.range(0, 20).boxed().toList();
        assertEquals(twenty.toString(), new CustomModel.Parameter(twenty).toString());
    }

    @Test
    public void testLargeSetIdentity() {
        // toString is part of the profile version: a large set printed by its digest ignores the order and duplicates
        List<Integer> large = IntStream.range(0, 30).boxed().toList();
        List<Integer> reordered = new ArrayList<>(large.reversed());
        reordered.addAll(large.subList(0, 10));
        CustomModel model = new CustomModel().setParameter("ways", large);
        assertEquals(model.toString(), new CustomModel().setParameter("ways", reordered).toString());
        assertEquals(model.getParameters(), new CustomModel().setParameter("ways", reordered).getParameters());

        // but every changed, added or removed member changes it, also the last one
        List<Integer> lastChanged = new ArrayList<>(large);
        lastChanged.set(29, 30);
        List<Integer> added = new ArrayList<>(large);
        added.add(30);
        for (List<Integer> other : List.of(lastChanged, added, large.subList(0, 29), large.subList(1, 30)))
            assertNotEquals(model.toString(), new CustomModel().setParameter("ways", other).toString(), other.toString());
    }

    @Test
    public void testParameterTypes() {
        CustomModel cm = new CustomModel().setParameter("flag", true).setParameter("width", 3).setParameter("ways", List.of(1)).
                setParameters(Map.of("text", "abc"));
        assertEquals(true, cm.getParameters().get("flag").value());
        assertEquals(3.0, cm.getParameters().get("width").value());
        assertEquals(CustomModel.Parameter.Type.BOOLEAN, cm.getParameters().get("flag").type());
        assertEquals(CustomModel.Parameter.Type.DOUBLE, cm.getParameters().get("width").type());
        assertEquals(CustomModel.Parameter.Type.INT_SET, cm.getParameters().get("ways").type());
        // rejected later by CustomModelParser, like before
        assertNull(cm.getParameters().get("text").type());
        // the types determine the class key, as they determine the field types of the compiled class
        assertTrue(cm.createClassKey().endsWith("|parameterTypes={flag=BOOLEAN, text=null, ways=INT_SET, width=DOUBLE}"), cm.createClassKey());
    }

    @Test
    public void testMergeParameters() {
        CustomModel base = new CustomModel().setParameter("power", 120.0).setParameter("mass", 95.0);
        CustomModel query = new CustomModel().setParameter("power", 100.0).setParameter("extra", 0.5);

        CustomModel merged = CustomModel.merge(base, query);
        assertEquals(100.0, merged.getParameters().get("power").value());
        assertEquals(95.0, merged.getParameters().get("mass").value());
        assertEquals(0.5, merged.getParameters().get("extra").value());
        // the second model's ranges are never merged, so a query model cannot change them
        assertTrue(CustomModel.merge(base, new CustomModel().setParameter("power", 100.0, 50, 150)).getParameters().get("power").hasDefaultRange());
        // the input models are unchanged
        assertEquals(120.0, base.getParameters().get("power").value());
        assertEquals(2, query.getParameters().size());

        // the class key ignores the parameter values but not the types (they determine the field types)
        assertEquals(base.createClassKey(), new CustomModel(base).setParameter("power", 130.0).createClassKey());
        assertNotEquals(base.createClassKey(), merged.createClassKey());
        assertNotEquals(base.createClassKey(), new CustomModel(base).setParameter("power", true).createClassKey());
        assertNotEquals(base.toString(), new CustomModel(base).setParameter("power", 130.0).toString());

        // a set is replaced, not combined
        CustomModel setBase = new CustomModel().setParameter("ways", List.of(1, 2));
        assertEquals(List.of(3), CustomModel.merge(setBase, new CustomModel().setParameter("ways", List.of(3))).getParameters().get("ways").value());
        assertEquals(List.of(1, 2), setBase.getParameters().get("ways").value());
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
