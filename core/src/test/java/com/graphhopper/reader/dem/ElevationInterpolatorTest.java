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
package com.graphhopper.reader.dem;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.graphhopper.util.Helper;

/**
 * @author Alexey Valikov
 */
public class ElevationInterpolatorTest {

    private static final double PRECISION = ElevationInterpolator.EPSILON2;

    private ElevationInterpolator elevationInterpolator = new ElevationInterpolator();

    @Test
    public void calculatesElevationOnTwoPoints() {
        assertEquals(15, elevationInterpolator.calculateElevationBasedOnTwoPoints(0, 0, -10, -10,
                10, 10, 10, 20), PRECISION);
        assertEquals(15, elevationInterpolator.calculateElevationBasedOnTwoPoints(-10, 10, -10, -10,
                10, 10, 10, 20), PRECISION);
        assertEquals(15, elevationInterpolator.calculateElevationBasedOnTwoPoints(-5, 5, -10, -10,
                10, 10, 10, 20), PRECISION);
        assertEquals(19, elevationInterpolator.calculateElevationBasedOnTwoPoints(8, 8, -10, -10,
                10, 10, 10, 20), PRECISION);
        assertEquals(10, elevationInterpolator.calculateElevationBasedOnTwoPoints(0, 0,
                -ElevationInterpolator.EPSILON / 3, 0, 10,
                ElevationInterpolator.EPSILON / 2, 0, 20), PRECISION);
        assertEquals(20, elevationInterpolator.calculateElevationBasedOnTwoPoints(0, 0,
                -ElevationInterpolator.EPSILON / 2, 0, 10,
                ElevationInterpolator.EPSILON / 3, 0, 20), PRECISION);
        assertEquals(10, elevationInterpolator.calculateElevationBasedOnTwoPoints(0, 0, 0, 0, 10, 0,
                0, 20), PRECISION);
    }

    @Test
    public void calculatesElevationOnThreePoints() {
        assertEquals(-0.88, elevationInterpolator.calculateElevationBasedOnThreePoints(0, 0, 1,
                2, 3, 4, 6, 9, 12, 11, 9), PRECISION);
        assertEquals(15, elevationInterpolator.calculateElevationBasedOnThreePoints(10, 0, 0, 0, 0,
                10, 10, 10, 10, -10, 20), PRECISION);
        assertEquals(5, elevationInterpolator.calculateElevationBasedOnThreePoints(5, 5, 0, 0, 0,
                10, 10, 10, 20, 20, 20), PRECISION);
    }

    @Test
    public void calculatesElevationOnNPoints() {
        assertEquals(0, elevationInterpolator.calculateElevationBasedOnPointList(5, 5,
                Helper.createPointList3D(0, 0, 0, 10, 0, 0, 10, 10, 0, 0, 10, 0)),
                PRECISION);
        assertEquals(10, elevationInterpolator.calculateElevationBasedOnPointList(5, 5,
                Helper.createPointList3D(0, 0, 0, 10, 0, 10, 10, 10, 20, 0, 10, 10)),
                PRECISION);
        assertEquals(5, elevationInterpolator.calculateElevationBasedOnPointList(5, 5,
                Helper.createPointList3D(0, 0, 0, 10, 0, 10, 10, 10, 0, 0, 10, 10)),
                PRECISION);
        assertEquals(2.65, elevationInterpolator.calculateElevationBasedOnPointList(2.5,
                2.5, Helper.createPointList3D(0, 0, 0, 10, 0, 10, 10, 10, 0, 0, 10, 10)),
                PRECISION);
        assertEquals(0, elevationInterpolator.calculateElevationBasedOnPointList(0.1,
                0.1, Helper.createPointList3D(0, 0, 0, 10, 0, 10, 10, 10, 0, 0, 10, 10)),
                PRECISION);
        assertEquals(0, elevationInterpolator.calculateElevationBasedOnPointList(
                ElevationInterpolator.EPSILON / 2, ElevationInterpolator.EPSILON / 2,
                Helper.createPointList3D(0, 0, 0, 10, 0, 10, 10, 10, 0, 0, 10, 10)),
                PRECISION);
        assertEquals(0, elevationInterpolator.calculateElevationBasedOnPointList(5, 0,
                Helper.createPointList3D(0, 0, 0, 10, 1, 10, 10, -1, -10, 20, 0, 0)),
                PRECISION);
    }

}
