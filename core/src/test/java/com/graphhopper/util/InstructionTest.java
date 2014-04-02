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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Johannes Pelzer
 */
public class InstructionTest
{   
    @Test
    public void testGetAzimuthAndGetDirection() {
        PointList pl = new PointList();
        pl.add(49.942, 11.584);
        pl.add(49.942, 11.582);
        Instruction i1 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", 0, 0, pl).setDistance(240).setTime(15000);
        
        assertEquals("270", i1.getAzimuth(null));
        assertEquals("W", i1.getDirection(null));

        
        PointList p2 = new PointList();
        p2.add(49.942, 11.580);
        p2.add(49.944, 11.582);
        Instruction i2 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", 0, 0, p2).setDistance(240).setTime(15000);
        
        assertEquals("45", i2.getAzimuth(null));
        assertEquals("NE", i2.getDirection(null));
        
        
        PointList p3 = new PointList();
        p3.add(49.942, 11.580);
        p3.add(49.944, 11.580);
        Instruction i3 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", 0, 0, p3).setDistance(240).setTime(15000);
        
        
        assertEquals("0", i3.getAzimuth(null));
        assertEquals("N", i3.getDirection(null));
        
        PointList p4 = new PointList();
        p4.add(49.940, 11.580);
        p4.add(49.920, 11.586);
        Instruction i4 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", 0, 0, p4).setDistance(240).setTime(15000);
        
        
        
        assertEquals("S", i4.getDirection(null));
 
        PointList p5 = new PointList();
        p5.add(49.940, 11.580);
        Instruction i5 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", 0, 0, p5).setDistance(240).setTime(15000);
        
        assertEquals(null, i5.getAzimuth(null));
        assertEquals(null, i5.getDirection(null));
    }
    
    
    
}
