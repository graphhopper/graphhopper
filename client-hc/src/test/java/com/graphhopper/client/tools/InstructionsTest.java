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

package com.graphhopper.client.tools;

import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstructionsTest {
    @Test
    public void testFind() {
        PointList pl0 = new PointList();
        pl0.add(15.0, 10.0);
        PointList pl1 = new PointList();
        pl1.add(15.1, 10.0);
        PointList pl2 = new PointList();
        pl2.add(15.1, 9.9);
        pl2.add(15.2, 9.9);
        pl2.add(15.2, 10.0);
        PointList pl3 = new PointList();
        pl3.add(15.2, 10.1);
        InstructionList wayList = new InstructionList(null);
        wayList.add(0, new Instruction(Instruction.CONTINUE_ON_STREET, "1-2", pl0).setDistance(10000).setTime(600000));
        wayList.add(1, new Instruction(Instruction.TURN_LEFT, "2-3", pl1).setDistance(10000).setTime(60000));
        wayList.add(2, new Instruction(Instruction.TURN_RIGHT, "3-4", pl2).setDistance(20000).setTime(1200000));
        wayList.add(3, new Instruction(Instruction.FINISH, "", pl3));

        assertEquals(4, wayList.size());
        assertEquals("1-2", wayList.get(0).getName());
        assertEquals(Instruction.CONTINUE_ON_STREET, wayList.get(0).getSign());
        assertEquals("2-3", wayList.get(1).getName());
        assertEquals(Instruction.TURN_LEFT, wayList.get(1).getSign());
        assertEquals("3-4", wayList.get(2).getName());
        assertEquals(Instruction.TURN_RIGHT, wayList.get(2).getSign());
        assertEquals("", wayList.get(3).getName());
        assertEquals(Instruction.FINISH, wayList.get(3).getSign());
    }
}