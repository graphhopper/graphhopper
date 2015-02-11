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

import java.io.IOException;
import java.util.Random;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Karl Hübner
 */
public class GraphHopperStorageWithTurnCostsTest extends GraphHopperStorageTest
{
    private TurnCostExtension turnCostStorage;

    @Override
    protected GraphStorage newGraph( Directory dir, boolean is3D )
    {
        turnCostStorage = new TurnCostExtension();
        return new GraphHopperStorage(dir, encodingManager, is3D, turnCostStorage);
    }

    @Override
    protected GraphStorage newRAMGraph()
    {
        return newGraph(new RAMDirectory(), false);
    }

    @Test
    public void testSave_and_fileFormat_withTurnCostEntries() throws IOException
    {
        graph = newGraph(new RAMDirectory(defaultGraphLoc, true), false).create(defaultSize);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 10, 10);
        na.setNode(1, 11, 20);
        na.setNode(2, 12, 12);

        EdgeIteratorState iter2 = graph.edge(0, 1, 100, true);
        iter2.setWayGeometry(Helper.createPointList(1.5, 1, 2, 3));
        EdgeIteratorState iter1 = graph.edge(0, 2, 200, true);
        iter1.setWayGeometry(Helper.createPointList(3.5, 4.5, 5, 6));
        graph.edge(9, 10, 200, true);
        graph.edge(9, 11, 200, true);
        graph.edge(1, 2, 120, false);

        turnCostStorage.addTurnInfo(iter1.getEdge(), 0, iter2.getEdge(), 1337);
        turnCostStorage.addTurnInfo(iter2.getEdge(), 0, iter1.getEdge(), 666);
        turnCostStorage.addTurnInfo(iter1.getEdge(), 1, iter2.getEdge(), 815);

        iter1.setName("named street1");
        iter2.setName("named street2");

        checkGraph(graph);
        graph.flush();
        graph.close();

        graph = newGraph(new MMapDirectory(defaultGraphLoc), false);
        assertTrue(graph.loadExisting());

        assertEquals(12, graph.getNodes());
        checkGraph(graph);

        assertEquals("named street1", graph.getEdgeProps(iter1.getEdge(), iter1.getAdjNode()).getName());
        assertEquals("named street2", graph.getEdgeProps(iter2.getEdge(), iter2.getAdjNode()).getName());

        assertEquals(1337, turnCostStorage.getTurnCostFlags(iter1.getEdge(), 0, iter2.getEdge()));
        assertEquals(666, turnCostStorage.getTurnCostFlags(iter2.getEdge(), 0, iter1.getEdge()));
        assertEquals(815, turnCostStorage.getTurnCostFlags(iter1.getEdge(), 1, iter2.getEdge()));
        assertEquals(0, turnCostStorage.getTurnCostFlags(iter1.getEdge(), 3, iter2.getEdge()));

        graph.edge(3, 4, 123, true).setWayGeometry(Helper.createPointList(4.4, 5.5, 6.6, 7.7));
        checkGraph(graph);
    }

    @Test
    public void testEnsureCapacity() throws IOException {
        graph = newGraph(new MMapDirectory(defaultGraphLoc), false);
        graph.setSegmentSize(128);
        graph.create(100); // 100 is the minimum size

        // assert that turnCostStorage can hold 104 turn cost entries at the beginning
        assertEquals(104, turnCostStorage.getCapacity() / 16);

        Random r = new Random();

        NodeAccess na = graph.getNodeAccess();
        for (int i = 0; i < 100; i++) {
            double randomLat = 90 * r.nextDouble();
            double randomLon = 180 * r.nextDouble();

            na.setNode(i, randomLat, randomLon);
        }

        // Make node 50 the 'center' node
        for (int nodeId = 51; nodeId < 100; nodeId++) {
            graph.edge(50, nodeId, r.nextDouble(), true);
        }
        for (int nodeId = 0; nodeId < 50; nodeId++) {
            graph.edge(nodeId, 50, r.nextDouble(), true);
        }

        // add 100 turn cost entries around node 50
        for (int edgeId = 0; edgeId < 50; edgeId++) {
            turnCostStorage.addTurnInfo(edgeId, 50, edgeId + 50, 1337);
            turnCostStorage.addTurnInfo(edgeId + 50, 50, edgeId, 1337);
        }

        turnCostStorage.addTurnInfo(0, 50, 1, 1337);
        assertEquals(104, turnCostStorage.getCapacity() / 16); // we are still good here

        turnCostStorage.addTurnInfo(0, 50, 2, 1337);
        // A new segment should be added, which will support 128 / 16 = 8 more entries.
        assertEquals(112, turnCostStorage.getCapacity() / 16);
    }
}
