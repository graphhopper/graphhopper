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
    public void testCalcAngle()
    {
        assertEquals(0.0, ac.calcAngleDeg(20.0, 10.0, 10.0, 10.0, 20.0, 10.0), 0.001);
        assertEquals(90.0, ac.calcAngleDeg(20.0, 10.0, 10.0, 10.0, 10.0, 30.0), 0.001);
        
        assertEquals(45.0, ac.calcAngleDeg(20.0, 10.0, 10.0, 10.0, 20.0, 20.0), 0.001);
        assertEquals(135.0, ac.calcAngleDeg(20.0, 10.0, 10.0, 10.0, 0.0, 20.0), 0.001);
        
        assertEquals(90.0, ac.calcAngleDeg(-10.0, 10.0, 10.0, 10.0, 10.0, 30.0), 0.001);
        assertEquals(90.0, ac.calcAngleDeg(20.0, 10.0, 10.0, 10.0, 10.0, -30.0), 0.001);
        assertEquals(90.0, ac.calcAngleDeg(-10.0, 10.0, 10.0, 10.0, 10.0, -30.0), 0.001);
        
        assertEquals(Double.NaN, ac.calcAngleDeg(20.0, 10.0, 20.0, 10.0, 10.0, 30.0), 0.001);
    }
    
    @Test
    public void testCalcTurnAngleDeg() {
        assertEquals(0.0, ac.calcTurnAngleDeg(0.0, 0.0, 10.0, 0.0, 20.0, 0.0), 0.001);
        assertEquals(90.0, ac.calcTurnAngleDeg(0.0, 0.0, 10.0, 0.0, 10.0, 20.0), 0.001);
        assertEquals(-90.0, ac.calcTurnAngleDeg(0.0, 0.0, 10.0, 0.0, 10.0, -20.0), 0.001);
        assertEquals(45.0, ac.calcTurnAngleDeg(0.0, 0.0, 10.0, 0.0, 20.0, 10.0), 0.001);
    }
    
    @Test
    public void testCalcAngleAgainstNorth() {
        assertEquals(0.0, ac.calcAngleAgainstNorthDeg(20.0, 10.0, 30.0, 10.0), 0.001);
        assertEquals(90.0, ac.calcAngleAgainstNorthDeg(20.0, 10.0, 20.0, 20.0), 0.001);
        assertEquals(270.0, ac.calcAngleAgainstNorthDeg(20.0, 10.0, 20.0, -10.0), 0.001);
    }
}
