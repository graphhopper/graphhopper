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
    private final AngleCalc ac = new AngleCalc();

    @Test
    public void testOrientation()
    {
        assertEquals(90.0, Math.toDegrees(ac.calcOrientation(0, 0, 1, 0)), 0.01);
        assertEquals(45.0, Math.toDegrees(ac.calcOrientation(0, 0, 1, 1)), 0.01);
        assertEquals(0.0, Math.toDegrees(ac.calcOrientation(0, 0, 0, 1)), 0.01);
        assertEquals(-45.0, Math.toDegrees(ac.calcOrientation(0, 0, -1, 1)), 0.01);
        assertEquals(-135.0, Math.toDegrees(ac.calcOrientation(0, 0, -1, -1)), 0.01);

        // is symetric?
        assertEquals(90 - 32.76, Math.toDegrees(ac.calcOrientation(49.942, 11.580, 49.944, 11.582)), 0.01);
        assertEquals(-90 - 32.76, Math.toDegrees(ac.calcOrientation(49.944, 11.582, 49.942, 11.580)), 0.01);
    }

    @Test
    public void testAlignOrientation()
    {
        assertEquals(90.0, Math.toDegrees(ac.alignOrientation(Math.toRadians(90), Math.toRadians(90))), 0.001);
        assertEquals(225.0, Math.toDegrees(ac.alignOrientation(Math.toRadians(90), Math.toRadians(-135))), 0.001);
        assertEquals(-45.0, Math.toDegrees(ac.alignOrientation(Math.toRadians(-135), Math.toRadians(-45))), 0.001);
        assertEquals(-270.0, Math.toDegrees(ac.alignOrientation(Math.toRadians(-135), Math.toRadians(90))), 0.001);
    }

    @Test
    public void testCombined()
    {
        double orientation = ac.calcOrientation(52.414918, 13.244221, 52.415333, 13.243595);
        assertEquals(132.7, Math.toDegrees(ac.alignOrientation(0, orientation)), 1);

        orientation = ac.calcOrientation(52.414918, 13.244221, 52.414573, 13.243627);
        assertEquals(-136.38, Math.toDegrees(ac.alignOrientation(0, orientation)), 1);
    }

    @Test
    public void testCalcAzimuth()
    {
        assertEquals(45.0, ac.calcAzimuth(0, 0, 1, 1), 0.001);
        assertEquals(90.0, ac.calcAzimuth(0, 0, 0, 1), 0.001);
        assertEquals(180.0, ac.calcAzimuth(0, 0, -1, 0), 0.001);
        assertEquals(270.0, ac.calcAzimuth(0, 0, 0, -1), 0.001);
        assertEquals(0.0, ac.calcAzimuth(49.942, 11.580, 49.944, 11.580), 0.001);
    }

    @Test
    public void testAzimuthCompassPoint()
    {
        assertEquals("S", ac.azimuth2compassPoint(199));
    }

    @Test
    public void testAtan2()
    {
        // assertEquals(0, AngleCalc.atan2(0, 0), 1e-4);
        // assertEquals(0, AngleCalc.atan2(-0.002, 0), 1e-4);
        assertEquals(45, AngleCalc.atan2(5, 5) * 180 / Math.PI, 1e-2);
        assertEquals(-45, AngleCalc.atan2(-5, 5) * 180 / Math.PI, 1e-2);
        assertEquals(11.14, AngleCalc.atan2(1, 5) * 180 / Math.PI, 1);
        assertEquals(180, AngleCalc.atan2(0, -5) * 180 / Math.PI, 1e-2);
        assertEquals(-90, AngleCalc.atan2(-5, 0) * 180 / Math.PI, 1e-2);

        assertEquals(90, Math.atan2(1, 0) * 180 / Math.PI, 1e-2);
        assertEquals(90, AngleCalc.atan2(1, 0) * 180 / Math.PI, 1e-2);
    }
}
