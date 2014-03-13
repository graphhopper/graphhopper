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
 *
 * @author Johannes Pelzer
 */
public class AngleCalcTest
{
    private AngleCalc2D ac = new AngleCalc2D();

    @Test
    public void testOrientation()
    {
        assertEquals(0.0, Math.toDegrees(ac.calcOrientation(0, 0, 10, 0)), 0.0001);
        assertEquals(45.0, Math.toDegrees(ac.calcOrientation(0, 0, 10, 10)), 0.0001);
        assertEquals(90.0, Math.toDegrees(ac.calcOrientation(0, 0, 0, 10)), 0.0001);
        assertEquals(-135.0, Math.toDegrees(ac.calcOrientation(0, 0, -10, -10)), 0.0001);
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
    public void testIsLeftTurn()
    {
        assertEquals(true, ac.isLeftTurn(1.5, 1.3));
        assertEquals(true, ac.isLeftTurn(-1.5, -1.6));
        assertEquals(true, ac.isLeftTurn(0.5, -0.6));

        assertEquals(false, ac.isLeftTurn(1.5, 1.7));
        assertEquals(false, ac.isLeftTurn(-1.5, 1.4));
        assertEquals(false, ac.isLeftTurn(-0.5, 1.3));

        AngleCalc2D ac = new AngleCalc2D();
        double o1 = ac.calcOrientation(1.2, 1.0, 1.2, 1.1);
        double o2 = ac.calcOrientation(1.2, 1.1, 1.1, 1.1);
        assertEquals(false, ac.isLeftTurn(o1, o2));
    }

    @Test
    public void testCalcTurnAngleDeg()
    {
        assertEquals(0.0, ac.calcTurnAngleDeg(0.0, 0.0, 10.0, 0.0, 20.0, 0.0), 0.001);
        assertEquals(90.0, ac.calcTurnAngleDeg(0.0, 0.0, 10.0, 0.0, 10.0, 20.0), 0.001);
        assertEquals(-90.0, ac.calcTurnAngleDeg(0.0, 0.0, 10.0, 0.0, 10.0, -20.0), 0.001);
        assertEquals(45.0, ac.calcTurnAngleDeg(0.0, 0.0, 10.0, 0.0, 20.0, 10.0), 0.001);
    }

    @Test
    public void testCalcAzimuth()
    {
        assertEquals(0.0, ac.calcAzimuthDeg(0.0, 0.0, 20.0, 0.0), 0.001);
        assertEquals(45.0, ac.calcAzimuthDeg(0.0, 0.0, 20.0, 20.0), 0.001);
        assertEquals(90.0, ac.calcAzimuthDeg(0.0, 0.0, 0.0, 10.0), 0.001);
        assertEquals(180.0, ac.calcAzimuthDeg(10.0, 0.0, 5.0, 0.0), 0.001);
        assertEquals(270.0, ac.calcAzimuthDeg(0.0, 10.0, 0.0, -10.0), 0.001);
        assertEquals(225.0, ac.calcAzimuthDeg(0.0, 0.0, -10.0, -10.0), 0.001);
    }

    @Test
    public void testAzimuthCompassPoint()
    {
        assertEquals("S", ac.azimuth2compassPoint(199));
    }
}
