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
package com.graphhopper;

import com.graphhopper.util.GPXEntry;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TLongArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class GHResponseTest
{
    @Test
    public void testCreateGPX()
    {
        GHResponse instance = new GHResponse();
        InstructionList instructions = new InstructionList();
        TLongArrayList times = new TLongArrayList();
        times.add(10 * 1000);
        times.add(5 * 1000);
        PointList pl = new PointList();
        pl.add(51.272226, 13.623047);
        pl.add(51.272, 13.623);
        TDoubleArrayList distances = new TDoubleArrayList();
        distances.add(100);
        distances.add(10);
        instructions.add(new Instruction(Instruction.CONTINUE_ON_STREET, "temp", distances, times, pl));

        times = new TLongArrayList();
        times.add(4 * 1000);
        distances = new TDoubleArrayList();
        distances.add(100);
        pl = new PointList();
        pl.add(51.272226, 13.623047);
        instructions.add(new Instruction(Instruction.TURN_LEFT, "temp2", distances, times, pl));
        instance.setInstructions(instructions);
        List<GPXEntry> result = instance.createGPXList();
        assertEquals(3, result.size());
        assertEquals(19 * 1000, result.get(2).getMillis());        
    }
}
