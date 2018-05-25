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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Johannes Pelzer
 */
public class InstructionTest {
    @Test
    public void testCalcAzimuthAndGetDirection() {
        InstructionAnnotation ea = InstructionAnnotation.EMPTY;
        PointList pl = new PointList();
        pl.add(49.942, 11.584);

        PointList nextPl = new PointList();
        nextPl.add(49.942, 11.582);
        Instruction currI = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", ea, pl);
        Instruction nextI = new Instruction(Instruction.CONTINUE_ON_STREET, "next", ea, nextPl);

        assertEquals(270, currI.calcAzimuth(nextI), .1);
        assertEquals("W", currI.calcDirection(nextI));

        PointList p2 = new PointList();
        p2.add(49.942, 11.580);
        p2.add(49.944, 11.582);
        Instruction i2 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", ea, p2);

        assertEquals(32.76, i2.calcAzimuth(null), .1);
        assertEquals("NE", i2.calcDirection(null));

        PointList p3 = new PointList();
        p3.add(49.942, 11.580);
        p3.add(49.944, 11.580);
        Instruction i3 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", ea, p3);

        assertEquals(0, i3.calcAzimuth(null), .1);
        assertEquals("N", i3.calcDirection(null));

        PointList p4 = new PointList();
        p4.add(49.940, 11.580);
        p4.add(49.920, 11.586);
        Instruction i4 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", ea, p4);

        assertEquals("S", i4.calcDirection(null));

        PointList p5 = new PointList();
        p5.add(49.940, 11.580);
        Instruction i5 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", ea, p5);

        assertTrue(Double.isNaN(i5.calcAzimuth(null)));
        assertEquals("", i5.calcDirection(null));
    }
}
