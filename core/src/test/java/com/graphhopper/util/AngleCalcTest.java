/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Johannes Pelzer
 * @author Peter Karich
 */
public class AngleCalcTest
{
    private final AngleCalc2D ac = new AngleCalc2D();

    @Test
    public void testOrientation()
    {
        assertEquals(90.0, Math.toDegrees(ac.calcOrientation(0, 0, 10, 0)), 0.0001);
        assertEquals(45.0, Math.toDegrees(ac.calcOrientation(0, 0, 10, 10)), 0.0001);
        assertEquals(0.0, Math.toDegrees(ac.calcOrientation(0, 0, 0, 10)), 0.0001);
        assertEquals(-135.0, Math.toDegrees(ac.calcOrientation(0, 0, -10, -10)), 0.0001);
    }

    @Test
    public void testOrientationNorth()
    {
        assertEquals(0.0, Math.toDegrees(ac.calcOrientationNorth(0, 0, 10, 0)), 0.0001);
        assertEquals(45.0, Math.toDegrees(ac.calcOrientationNorth(0, 0, 10, 10)), 0.0001);
        assertEquals(90.0, Math.toDegrees(ac.calcOrientationNorth(0, 0, 0, 10)), 0.0001);
        assertEquals(-135.0, Math.toDegrees(ac.calcOrientationNorth(0, 0, -10, -10)), 0.0001);
    }

    @Test
    public void testAlignOrientation()
    {
        assertEquals(90.0, Math.toDegrees(ac.alignOrientation(Math.toRadians(90), Math.toRadians(90))), 0.0001);
        assertEquals(225.0, Math.toDegrees(ac.alignOrientation(Math.toRadians(90), Math.toRadians(-135))), 0.0001);
        assertEquals(-45.0, Math.toDegrees(ac.alignOrientation(Math.toRadians(-135), Math.toRadians(-45))), 0.0001);
        assertEquals(-270.0, Math.toDegrees(ac.alignOrientation(Math.toRadians(-135), Math.toRadians(90))), 0.0001);
    }

    @Test
    public void testCombined()
    {
        double orientation = ac.calcOrientation(52.414918, 13.244221, 52.415333, 13.243595);
        assertEquals(146.458, Math.toDegrees(ac.alignOrientation(0, orientation)), 0.001);
    }

    @Test
    public void testCalcAzimuth()
    {
        assertEquals(90.0, ac.calcAzimuth(0, 0, 0, 10), 0.0001);
        assertEquals(180.0, ac.calcAzimuth(0, 0, -10, 0), 0.0001);
        assertEquals(270.0, ac.calcAzimuth(0, 0, 0, -10), 0.0001);
    }

    @Test
    public void testAzimuthCompassPoint()
    {
        assertEquals("S", ac.azimuth2compassPoint(199));
    }
}
