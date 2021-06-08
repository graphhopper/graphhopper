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

import com.graphhopper.json.Statement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.util.CustomModel.findMax;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CustomModelTest {

    @Test
    public void testCheck() {
        CustomModel queryModel = new CustomModel();
        queryModel.addToPriority(If("max_width < 3", MULTIPLY, 10));
        assertEquals(1, CustomModel.merge(queryModel, new CustomModel()).getPriority().size());
        // priority bigger than 1 is not ok for CustomModel of query
        assertThrows(IllegalArgumentException.class, () -> queryModel.checkLMConstraints(new CustomModel()));
    }

    @Test
    public void testFindMax() {
        List<Statement> statements = new ArrayList<>();
        statements.add(If("true", LIMIT, 100));
        assertEquals(100, findMax(statements, 120, "speed"));

        statements.add(Else(LIMIT, 20));
        assertEquals(100, findMax(statements, 120, "speed"));

        statements = new ArrayList<>();
        statements.add(If("road_environment == BRIDGE", LIMIT, 85));
        statements.add(Else(LIMIT, 100));
        assertEquals(100, findMax(statements, 120, "speed"));

        // find bigger speed than stored max_speed in server-side custom_models
        double storedMaxSpeed = 30;
        statements = new ArrayList<>();
        statements.add(If("true", MULTIPLY, 2));
        statements.add(If("true", LIMIT, 35));
        assertEquals(35, findMax(statements, 30, "speed"));
    }

    @Test
    public void findMax_limitAndMultiply() {
        List<Statement> statements = Arrays.asList(
                If("road_class == TERTIARY", LIMIT, 90),
                ElseIf("road_class == SECONDARY", MULTIPLY, 1.0),
                ElseIf("road_class == PRIMARY", LIMIT, 30),
                Else(LIMIT, 3)
        );
        assertEquals(140, findMax(statements, 140, "speed"));
    }

    @Test
    public void testFindMaxPriority() {
        List<Statement> statements = new ArrayList<>();
        statements.add(If("true", MULTIPLY, 2));
        assertEquals(2, findMax(statements, 1, "priority"));

        statements = new ArrayList<>();
        statements.add(If("true", MULTIPLY, 0.5));
        assertEquals(0.5, findMax(statements, 1, "priority"));
    }

    @Test
    public void findMax_multipleBlocks() {
        List<Statement> statements = Arrays.asList(
                If("road_class == TERTIARY", MULTIPLY, 0.2),
                ElseIf("road_class == SECONDARY", LIMIT, 25),
                If("road_environment == TUNNEL", LIMIT, 60),
                ElseIf("road_environment == BRIDGE", LIMIT, 50),
                Else(MULTIPLY, 0.8)
        );
        assertEquals(120, findMax(statements, 150, "speed"));
        assertEquals(80, findMax(statements, 100, "speed"));
        assertEquals(60, findMax(statements, 60, "speed"));

        statements = Arrays.asList(
                If("road_class == TERTIARY", MULTIPLY, 0.2),
                ElseIf("road_class == SECONDARY", LIMIT, 25),
                Else(LIMIT, 40),
                If("road_environment == TUNNEL", MULTIPLY, 0.8),
                ElseIf("road_environment == BRIDGE", LIMIT, 30)
        );
        assertEquals(40, findMax(statements, 150, "speed"));
        assertEquals(40, findMax(statements, 40, "speed"));
    }

    @Test
    public void testMergeComparisonKeys() {
        CustomModel truck = new CustomModel();
        truck.addToPriority(If("max_width < 3", MULTIPLY, 0));
        CustomModel car = new CustomModel();
        car.addToPriority(If("max_width<2", MULTIPLY, 0));
        CustomModel bike = new CustomModel();
        bike.addToPriority(If("max_weight<0.02", MULTIPLY, 0));

        assertEquals(2, CustomModel.merge(bike, car).getPriority().size());
        assertEquals(1, bike.getPriority().size());
        assertEquals(1, car.getPriority().size());
    }

    @Test
    public void testMergeElse() {
        CustomModel truck = new CustomModel();
        truck.addToPriority(If("max_width < 3", MULTIPLY, 0));

        CustomModel car = new CustomModel();
        car.addToPriority(If("max_width < 2", MULTIPLY, 0));

        CustomModel merged = CustomModel.merge(truck, car);
        assertEquals(2, merged.getPriority().size());
        assertEquals(1, car.getPriority().size());
    }

    @Test
    public void testMergeEmptyModel() {
        CustomModel emptyCar = new CustomModel();
        CustomModel car = new CustomModel();
        car.addToPriority(If("road_class==primary", MULTIPLY, 0.5));
        car.addToPriority(ElseIf("road_class==tertiary", MULTIPLY, 0.8));

        Iterator<Statement> iter = CustomModel.merge(emptyCar, car).getPriority().iterator();
        assertEquals(0.5, iter.next().getValue());
        assertEquals(0.8, iter.next().getValue());

        iter = CustomModel.merge(car, emptyCar).getPriority().iterator();
        assertEquals(0.5, iter.next().getValue());
        assertEquals(0.8, iter.next().getValue());
    }
}