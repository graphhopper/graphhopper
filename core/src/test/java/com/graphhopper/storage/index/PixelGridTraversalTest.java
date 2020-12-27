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
package com.graphhopper.storage.index;

import com.graphhopper.util.shapes.BBox;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.algorithm.RectangleLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PixelGridTraversalTest {

    @ParameterizedTest
    @CsvSource({
            "5.5, 2.5, 0.5, 0.5",
            "3.5, 1.5, 0.5, 3.5",
            "2.5, 2.5, 3.5, 0.5",
            "0.5, 0.5, 2.5, 3.5",
            "2.5, 2.9, 3.1, 0.9"
    })
    public void testAgainstNaiveGridCellIntersection(double x1, double y1, double x2, double y2) {
        BBox bounds = new BBox(0.0, 10.0, 0.0, 10.0);
        int parts = 10;
        Coordinate a = new Coordinate(x1, y1);
        Coordinate b = new Coordinate(x2, y2);

        Set<Coordinate> actual = new HashSet<>();
        PixelGridTraversal pixelGridTraversal = new PixelGridTraversal(parts, bounds);
        pixelGridTraversal.traverse(a, b, actual::add);

        Set<Coordinate> expected = new HashSet<>();
        double deltaLat = (bounds.maxLat - bounds.minLat) / parts;
        double deltaLon = (bounds.maxLon - bounds.minLon) / parts;
        for (int y = 0; y < parts; y++) {
            for (int x = 0; x < parts; x++) {
                RectangleLineIntersector rectangleLineIntersector = new RectangleLineIntersector(new Envelope(bounds.minLon + x * deltaLon, bounds.minLon + (x+1) * deltaLon, bounds.minLat + y* deltaLat, bounds.minLat+ (y+1) * deltaLat));
                if (rectangleLineIntersector.intersects(a, b)) {
                    expected.add(new Coordinate(x, y));
                }
            }
        }
        assertEquals(expected, actual);
    }

}
