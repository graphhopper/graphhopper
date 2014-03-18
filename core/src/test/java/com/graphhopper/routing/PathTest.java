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
package com.graphhopper.routing;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.Helper;
import static com.graphhopper.storage.AbstractGraphStorageTester.*;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PathTest
{
    @Test
    public void testFound()
    {
        Path p = new Path(null, null);
        assertFalse(p.isFound());
        assertEquals(0, p.getDistance(), 1e-7);
        assertEquals(0, p.calcNodes().size());
    }

    @Test
    public void testTime()
    {
        FlagEncoder encoder = new EncodingManager("CAR").getEncoder("CAR");
        Path p = new Path(null, encoder);
        assertEquals(60 * 60 * 1000, p.calcMillis(100000, encoder.setProperties(100, true, true), false));
    }

    @Test
    public void testWayList()
    {
        EncodingManager carManager = new EncodingManager("CAR");
        FlagEncoder carEnc = carManager.getEncoder("CAR");
        Graph g = new GraphBuilder(carManager).create();

        g.setNode(0, 0.0, 0.1);
        g.setNode(1, 1.0, 0.1);
        g.setNode(2, 2.0, 0.1);

        EdgeIteratorState edge1 = g.edge(0, 1).setDistance(1000).setFlags(carEnc.setProperties(10, true, true));
        edge1.setWayGeometry(Helper.createPointList(8, 1, 9, 1));
        EdgeIteratorState edge2 = g.edge(2, 1).setDistance(2000).setFlags(carEnc.setProperties(50, true, true));
        edge2.setWayGeometry(Helper.createPointList(11, 1, 10, 1));

        Path path = new Path(g, carEnc);
        EdgeEntry e1 = new EdgeEntry(edge2.getEdge(), 2, 1);
        e1.parent = new EdgeEntry(edge1.getEdge(), 1, 1);
        e1.parent.parent = new EdgeEntry(-1, 0, 1);
        path.setEdgeEntry(e1);
        path.extract();
        // 0-1-2
        assertPList(Helper.createPointList(0, 0.1, 8, 1, 9, 1, 1, 0.1, 10, 1, 11, 1, 2, 0.1), path.calcPoints());
        InstructionList instr = path.calcInstructions();
        assertEquals("[" + 504 * 1000 + ", 0]", instr.createMillis().toString());
        assertEquals("[3000.0, 0.0]", instr.createDistances().toString());

        // force minor change for instructions
        edge2.setName("2");
        path = new Path(g, carEnc);
        e1 = new EdgeEntry(edge2.getEdge(), 2, 1);
        e1.parent = new EdgeEntry(edge1.getEdge(), 1, 1);
        e1.parent.parent = new EdgeEntry(-1, 0, 1);
        path.setEdgeEntry(e1);
        path.extract();
        instr = path.calcInstructions();
        assertEquals("[" + 6 * 60 * 1000 + ", " + 144 * 1000 + ", 0]", instr.createMillis().toString());
        assertEquals("[1000.0, 2000.0, 0.0]", instr.createDistances().toString());

        // now reverse order
        path = new Path(g, carEnc);
        e1 = new EdgeEntry(edge1.getEdge(), 0, 1);
        e1.parent = new EdgeEntry(edge2.getEdge(), 1, 1);
        e1.parent.parent = new EdgeEntry(-1, 2, 1);
        path.setEdgeEntry(e1);
        path.extract();
        // 2-1-0
        assertPList(Helper.createPointList(2, 0.1, 11, 1, 10, 1, 1, 0.1, 9, 1, 8, 1, 0, 0.1), path.calcPoints());
        instr = path.calcInstructions();
        assertEquals("[" + 144 * 1000 + ", " + 6 * 60 * 1000 + ", 0]", instr.createMillis().toString());
        assertEquals("[2000.0, 1000.0, 0.0]", instr.createDistances().toString());
    }
    
    @Test
    public void testFindInstruction()
    {
        EncodingManager carManager = new EncodingManager("CAR");
        FlagEncoder carEnc = carManager.getEncoder("CAR");
        Graph g = new GraphBuilder(carManager).create();

        g.setNode(0, 0.0, 0.0);
        g.setNode(1, 5.0, 0.0);
        g.setNode(2, 5.0, 0.5);
        g.setNode(3, 10.0, 0.5);
        g.setNode(4, 7.5, 0.25);

        EdgeIteratorState edge1 = g.edge(0, 1).setDistance(1000).setFlags(carEnc.setProperties(50, true, true));
        edge1.setWayGeometry(Helper.createPointList());
        edge1.setName("Street 1");
        EdgeIteratorState edge2 = g.edge(1, 2).setDistance(1000).setFlags(carEnc.setProperties(50, true, true));
        edge2.setWayGeometry(Helper.createPointList());
        edge2.setName("Street 2");
        EdgeIteratorState edge3 = g.edge(2, 3).setDistance(1000).setFlags(carEnc.setProperties(50, true, true));
        edge3.setWayGeometry(Helper.createPointList());
        edge3.setName("Street 3");
        EdgeIteratorState edge4 = g.edge(3, 4).setDistance(500).setFlags(carEnc.setProperties(50, true, true));
        edge4.setWayGeometry(Helper.createPointList());
        edge4.setName("Street 4");

        Path path = new Path(g, carEnc);
        EdgeEntry e1 = new EdgeEntry(edge4.getEdge(), 4, 1);
        e1.parent = new EdgeEntry(edge3.getEdge(), 3, 1);
        e1.parent.parent = new EdgeEntry(edge2.getEdge(), 2, 1);
        e1.parent.parent.parent = new EdgeEntry(edge1.getEdge(), 1, 1);
        e1.parent.parent.parent.parent = new EdgeEntry(-1, 0, 1);
        path.setEdgeEntry(e1);
        path.extract();
            
        path.calcInstructions();
        Instruction nextInstr1 = path.findInstruction(0.0, 0.1);
        Instruction nextInstr2 = path.findInstruction(5.0, 0.4);
        Instruction nextInstr3 = path.findInstruction(9.0, 0.53);
        Instruction nextInstr4 = path.findInstruction(7.8, 0.25);
        
        assertEquals(Instruction.TURN_RIGHT, nextInstr1.getIndication());
        assertEquals(Instruction.TURN_LEFT, nextInstr2.getIndication());
        assertEquals(Instruction.TURN_SHARP_LEFT, nextInstr3.getIndication());
        assertEquals(Instruction.FINISH, nextInstr4.getIndication());
    }
    
}
