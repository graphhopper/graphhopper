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
package com.graphhopper.storage;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.search.KVStorage;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static com.graphhopper.util.Parameters.Details.STREET_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Karl Hübner
 */
public class BaseGraphWithTurnCostsTest extends BaseGraphTest {

    private DecimalEncodedValue turnCostEnc;

    @Override
    protected EncodingManager createEncodingManager() {
        turnCostEnc = TurnCost.create("car", 1400);
        return EncodingManager.start()
                .add(carAccessEnc).add(carSpeedEnc).addTurnCostEncodedValue(turnCostEnc)
                .add(footAccessEnc).add(footSpeedEnc)
                .add(RoadClass.create())
                .build();
    }

    @Override
    protected BaseGraph newGHStorage(Directory dir, boolean is3D) {
        return newGHStorage(dir, is3D, -1);
    }

    @Override
    protected BaseGraph newGHStorage(Directory dir, boolean enabled3D, int segmentSize) {
        return new BaseGraph.Builder(encodingManager).setDir(dir).set3D(enabled3D).withTurnCosts(true).setSegmentSize(segmentSize).build();
    }

    @Override
    @Test
    public void testSave_and_fileFormat() {
        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), true).create(defaultSize);
        NodeAccess na = graph.getNodeAccess();
        assertTrue(na.is3D());
        na.setNode(0, 10, 10, 0);
        na.setNode(1, 11, 20, 1);
        na.setNode(2, 12, 12, 0.4);

        EdgeIteratorState iter2 = graph.edge(0, 1).setDistance(100).set(carAccessEnc, true, true);
        iter2.setWayGeometry(Helper.createPointList3D(1.5, 1, 0, 2, 3, 0));
        EdgeIteratorState iter1 = graph.edge(0, 2).setDistance(200).set(carAccessEnc, true, true);
        iter1.setWayGeometry(Helper.createPointList3D(3.5, 4.5, 0, 5, 6, 0));
        graph.edge(9, 10).setDistance(200).set(carAccessEnc, true, true);
        graph.edge(9, 11).setDistance(200).set(carAccessEnc, true, true);
        graph.edge(1, 2).setDistance(120).set(carAccessEnc, true, false);

        setTurnCost(iter1.getEdge(), 0, iter2.getEdge(), 1337);
        setTurnCost(iter2.getEdge(), 0, iter1.getEdge(), 666);
        setTurnCost(iter1.getEdge(), 1, iter2.getEdge(), 815);

        iter1.setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue( "named street1")));
        iter2.setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue( "named street2")));

        checkGraph(graph);
        graph.flush();
        graph.close();

        graph = newGHStorage(new MMapDirectory(defaultGraphLoc), true);
        graph.loadExisting();

        assertEquals(12, graph.getNodes());
        checkGraph(graph);

        assertEquals("named street1", graph.getEdgeIteratorState(iter1.getEdge(), iter1.getAdjNode()).getName());
        assertEquals("named street2", graph.getEdgeIteratorState(iter2.getEdge(), iter2.getAdjNode()).getName());

        assertEquals(1337, getTurnCost(iter2, 0, iter1), .1);
        assertEquals(666, getTurnCost(iter1, 0, iter2), .1);
        assertEquals(815, getTurnCost(iter2, 1, iter1), .1);
        assertEquals(0, getTurnCost(iter2, 3, iter1), .1);

        graph.edge(3, 4).setDistance(123).set(carAccessEnc, true, true).
                setWayGeometry(Helper.createPointList3D(4.4, 5.5, 0, 6.6, 7.7, 0));
        checkGraph(graph);
    }

    @Test
    public void testEnsureCapacity() {
        graph = newGHStorage(new MMapDirectory(defaultGraphLoc), false, 128);
        graph.create(100); // 100 is the minimum size

        TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
        // assert that turnCostStorage can hold 104 turn cost entries at the beginning
        assertEquals(128, turnCostStorage.getCapacity());

        Random r = new Random();

        NodeAccess na = graph.getNodeAccess();
        for (int i = 0; i < 100; i++) {
            double randomLat = 90 * r.nextDouble();
            double randomLon = 180 * r.nextDouble();

            na.setNode(i, randomLat, randomLon);
        }

        // Make node 50 the 'center' node
        for (int nodeId = 51; nodeId < 100; nodeId++) {
            graph.edge(50, nodeId).setDistance(r.nextDouble()).set(carAccessEnc, true, true);
        }
        for (int nodeId = 0; nodeId < 50; nodeId++) {
            graph.edge(nodeId, 50).setDistance(r.nextDouble()).set(carAccessEnc, true, true);
        }

        // add turn cost entries around node 50
        for (int edgeId = 0; edgeId < 52; edgeId++) {
            setTurnCost(edgeId, 50, edgeId + 50, 1337);
            setTurnCost(edgeId + 50, 50, edgeId, 1337);
        }

        // todonow: review this test!
        assertEquals(106, turnCostStorage.getCapacity() / 12); // we are still good here

        setTurnCost(0, 50, 2, 1337);
        // A new segment should be added, which will support 128 / 12 = 8 more entries.
        assertEquals(106, turnCostStorage.getCapacity() / 12);
    }

    private double getTurnCost(EdgeIteratorState fromEdge, int viaNode, EdgeIteratorState toEdge) {
        return graph.getTurnCostStorage().get(turnCostEnc, toEdge.getEdge(), viaNode, fromEdge.getEdge());
    }

    private void setTurnCost(int fromEdge, int viaNode, int toEdge, int cost) {
        graph.getTurnCostStorage().set(turnCostEnc, fromEdge, viaNode, toEdge, cost);
    }
}
