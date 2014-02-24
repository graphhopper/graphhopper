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
package com.graphhopper.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

/**
 *
 * @author Karl Hübner
 */
public class GraphHopperStorageWithTurnCostsTest extends GraphHopperStorageTest
{
    private TurnCostStorage turnCostStorage;

    @Override
    protected GraphStorage newGraph( Directory dir )
    {
        turnCostStorage = new TurnCostStorage();
        return new GraphHopperStorage(dir, encodingManager, turnCostStorage);
    }

    @Override
    protected GraphStorage newRAMGraph()
    {
        return newGraph(new RAMDirectory());
    }

    @Test
    public void testSave_and_fileFormat_withTurnCostEntries() throws IOException
    {
        graph = createGraphStorage(new RAMDirectory(defaultGraphLoc, true));
        graph.setNode(0, 10, 10);
        graph.setNode(1, 11, 20);
        graph.setNode(2, 12, 12);

        EdgeIteratorState iter2 = graph.edge(0, 1, 100, true);
        iter2.setWayGeometry(Helper.createPointList(1.5, 1, 2, 3));
        EdgeIteratorState iter1 = graph.edge(0, 2, 200, true);
        iter1.setWayGeometry(Helper.createPointList(3.5, 4.5, 5, 6));
        graph.edge(9, 10, 200, true);
        graph.edge(9, 11, 200, true);
        graph.edge(1, 2, 120, false);

        turnCostStorage.setTurnCosts(0, iter1.getEdge(), iter2.getEdge(), 1337);
        turnCostStorage.setTurnCosts(0, iter2.getEdge(), iter1.getEdge(), 666);
        turnCostStorage.setTurnCosts(1, iter1.getEdge(), iter2.getEdge(), 815);

        iter1.setName("named street1");
        iter2.setName("named street2");

        checkGraph(graph);
        graph.flush();
        graph.close();

        graph = newGraph(new MMapDirectory(defaultGraphLoc));
        assertTrue(graph.loadExisting());

        assertEquals(12, graph.getNodes());
        checkGraph(graph);

        assertEquals("named street1", graph.getEdgeProps(iter1.getEdge(), iter1.getAdjNode()).getName());
        assertEquals("named street2", graph.getEdgeProps(iter2.getEdge(), iter2.getAdjNode()).getName());

        assertEquals(1337, turnCostStorage.getTurnCosts(0, iter1.getEdge(), iter2.getEdge()));
        assertEquals(666, turnCostStorage.getTurnCosts(0, iter2.getEdge(), iter1.getEdge()));
        assertEquals(815, turnCostStorage.getTurnCosts(1, iter1.getEdge(), iter2.getEdge()));
        assertEquals(0, turnCostStorage.getTurnCosts(3, iter1.getEdge(), iter2.getEdge()));

        graph.edge(3, 4, 123, true).setWayGeometry(Helper.createPointList(4.4, 5.5, 6.6, 7.7));
        checkGraph(graph);
    }
}
