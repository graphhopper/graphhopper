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

package com.graphhopper.core.util;

import com.graphhopper.core.util.DistanceCalcEuclidean;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DistanceCalcEuclideanTest {

    @Test
    public void testCrossingPointToEdge() {
        DistanceCalcEuclidean distanceCalc = new DistanceCalcEuclidean();
        GHPoint point = distanceCalc.calcCrossingPointToEdge(0, 10, 0, 0, 10, 10);
        assertEquals(5, point.getLat(), 0);
        assertEquals(5, point.getLon(), 0);
    }

    @Test
    public void testCalcNormalizedEdgeDistance() {
        DistanceCalcEuclidean distanceCalc = new DistanceCalcEuclidean();
        double distance = distanceCalc.calcNormalizedEdgeDistance(0, 10, 0, 0, 10, 10);
        assertEquals(50, distance, 0);
    }

    @Test
    public void testCalcNormalizedEdgeDistance3dStartEndSame() {
        DistanceCalcEuclidean distanceCalc = new DistanceCalcEuclidean();
        double distance = distanceCalc.calcNormalizedEdgeDistance3D(0, 3, 4, 0, 0, 0, 0, 0, 0);
        assertEquals(25, distance, 0);
    }

    @Test
    public void testValidEdgeDistance() {
        DistanceCalcEuclidean distanceCalc = new DistanceCalcEuclidean();
        boolean validEdgeDistance = distanceCalc.validEdgeDistance(5, 15, 0, 0, 10, 10);
        assertEquals(false, validEdgeDistance);
        validEdgeDistance = distanceCalc.validEdgeDistance(15, 5, 0, 0, 10, 10);
        assertEquals(false, validEdgeDistance);
    }

    @Test
    public void testDistance3dEuclidean() {
        DistanceCalcEuclidean distCalc = new DistanceCalcEuclidean();
        assertEquals(1, distCalc.calcDist3D(
                0, 0, 0,
                0, 0, 1
        ), 1e-6);
        assertEquals(10, distCalc.calcDist3D(
                0, 0, 0,
                0, 0, 10
        ), 1e-6);
    }
}
