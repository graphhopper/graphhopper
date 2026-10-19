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
package com.graphhopper.matrix;

import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatrixRequestTest {
    @Test
    void preservesOrderDuplicatesAndDefensivelyCopiesMutablePoints() {
        GHPoint point = new GHPoint(43, 7);
        List<GHPoint> origins = new ArrayList<>(List.of(point, new GHPoint(44, 8), point));
        MatrixRequest request = new MatrixRequest("car", origins, List.of(point), List.of("times_ms"), false, "flexible");
        origins.clear();
        point.lat = 0;
        assertEquals(3, request.getOrigins().size());
        assertEquals(43, request.getOrigins().get(0).lat);
        assertEquals(44, request.getOrigins().get(1).lat);
        assertEquals(43, request.getOrigins().get(2).lat);
        request.getOrigins().get(0).lat = 1;
        assertEquals(43, request.getOrigins().get(0).lat);
        assertThrows(UnsupportedOperationException.class, () -> request.getDestinations().clear());
    }

    @Test
    void rejectsInvalidCoreInputsIndependentlyOfHttp() {
        List<GHPoint> points = List.of(new GHPoint(43, 7));
        assertThrows(MatrixException.class, () -> new MatrixRequest("", points, points, List.of("times"), true, "auto"));
        assertThrows(MatrixException.class, () -> new MatrixRequest("car", List.of(), points, List.of("times"), true, "auto"));
        assertThrows(MatrixException.class, () -> new MatrixRequest("car", points, points, List.of("times", "times"), true, "auto"));
        assertThrows(MatrixException.class, () -> new MatrixRequest("car", points, points, List.of("times"), true, "ch"));
        assertThrows(MatrixException.class, () -> new MatrixRequest("car", List.of(new GHPoint(Double.NaN, 7)), points, List.of("times"), true, "auto"));
        assertThrows(MatrixException.class, () -> new MatrixRequest("car", List.of(new GHPoint(43, Double.POSITIVE_INFINITY)), points, List.of("times"), true, "auto"));
        assertThrows(MatrixException.class, () -> new MatrixRequest("car", List.of(new GHPoint(91, 7)), points, List.of("times"), true, "auto"));
    }
}
