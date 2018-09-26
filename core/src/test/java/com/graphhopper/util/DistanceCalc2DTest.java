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

import org.junit.Assert;
import org.junit.Test;

import com.graphhopper.util.shapes.GHPoint;

public class DistanceCalc2DTest {

    @Test
    public void testCrossingPointToEdge() {
        DistanceCalc2D distanceCalc = new DistanceCalc2D();
        GHPoint point = distanceCalc.calcCrossingPointToEdge(0, 10, 0, 0, 10, 10);
        Assert.assertEquals(5, point.getLat(), 0);
        Assert.assertEquals(5, point.getLon(), 0);
    }

    @Test
    public void testCalcNormalizedEdgeDistance() {
        DistanceCalc2D distanceCalc = new DistanceCalc2D();
        double distance = distanceCalc.calcNormalizedEdgeDistance(0, 10, 0, 0, 10, 10);
        Assert.assertEquals(50, distance, 0);
    }

    @Test
    public void testValidEdgeDistance() {
        DistanceCalc2D distanceCalc = new DistanceCalc2D();
        boolean validEdgeDistance = distanceCalc.validEdgeDistance(5, 15, 0, 0, 10, 10);
        Assert.assertEquals(false, validEdgeDistance);
        validEdgeDistance = distanceCalc.validEdgeDistance(15, 5, 0, 0, 10, 10);
        Assert.assertEquals(false, validEdgeDistance);
    }
}
