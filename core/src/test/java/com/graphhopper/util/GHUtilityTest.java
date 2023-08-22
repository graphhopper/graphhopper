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

import com.graphhopper.coll.GHIntLongHashMap;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class GHUtilityTest {
    private final BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
    private final EncodingManager encodingManager = EncodingManager.start().add(accessEnc).add(speedEnc).build();

    BaseGraph createGraph() {
        return new BaseGraph.Builder(encodingManager).create();
    }

    // 7      8\
    // | \    | 2
    // |  5   | |
    // 3    4 | |
    //   6     \1
    //   ______/
    // 0/
    Graph initUnsorted(Graph g, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 1);
        na.setNode(1, 2.5, 4.5);
        na.setNode(2, 4.5, 4.5);
        na.setNode(3, 3, 0.5);
        na.setNode(4, 2.8, 2.8);
        na.setNode(5, 4.2, 1.6);
        na.setNode(6, 2.3, 2.2);
        na.setNode(7, 5, 1.5);
        na.setNode(8, 4.6, 4);
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(8, 2).setDistance(0.5));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(7, 3).setDistance(2.1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(1, 0).setDistance(3.9));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(7, 5).setDistance(0.7));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(1, 2).setDistance(1.9));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(8, 1).setDistance(2.05));
        return g;
    }

    double getLengthOfAllEdges(Graph graph) {
        double distance = 0;
        DistanceCalc calc = new DistanceCalcEuclidean();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            // This is meant to verify that all of the same edges (including tower nodes)
            // are included in the copied graph. Can not use iter.getDistance() since it
            // does not verify new geometry. See #1732
            distance += calc.calcDistance(iter.fetchWayGeometry(FetchMode.ALL));
        }
        return distance;
    }

    @Test
    public void testSort() {
        Graph g = initUnsorted(createGraph(), accessEnc, speedEnc);
        Graph newG = GHUtility.sortDFS(g, createGraph());
        assertEquals(g.getNodes(), newG.getNodes());
        assertEquals(g.getEdges(), newG.getEdges());
        NodeAccess na = newG.getNodeAccess();
        assertEquals(0, na.getLat(0), 1e-4); // 0
        assertEquals(2.5, na.getLat(1), 1e-4); // 1
        assertEquals(4.5, na.getLat(2), 1e-4); // 2
        assertEquals(4.6, na.getLat(3), 1e-4); // 8
        assertEquals(3.0, na.getLat(4), 1e-4); // 3
        assertEquals(5.0, na.getLat(5), 1e-4); // 7
        assertEquals(4.2, na.getLat(6), 1e-4); // 5
        assertEquals(getLengthOfAllEdges(g), getLengthOfAllEdges(newG), 1e-4);

        // 0 => 1
        assertEquals(0, newG.getEdgeIteratorState(0, Integer.MIN_VALUE).getAdjNode());
        assertEquals(1, newG.getEdgeIteratorState(0, Integer.MIN_VALUE).getBaseNode());

        // 1 => 3 (was 8)
        assertEquals(1, newG.getEdgeIteratorState(1, Integer.MIN_VALUE).getAdjNode());
        assertEquals(3, newG.getEdgeIteratorState(1, Integer.MIN_VALUE).getBaseNode());

        // 2 => 1
        assertEquals(2, newG.getEdgeIteratorState(2, Integer.MIN_VALUE).getAdjNode());
        assertEquals(1, newG.getEdgeIteratorState(2, Integer.MIN_VALUE).getBaseNode());
    }

    @Test
    public void testSortDirected() {
        Graph g = createGraph();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 1);
        na.setNode(1, 2.5, 2);
        na.setNode(2, 3.5, 3);
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(0, 1).setDistance(1.1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(2, 1).setDistance(1.1));
        GHUtility.sortDFS(g, createGraph());
    }

    @Test
    public void testEdgeStuff() {
        assertEquals(2, GHUtility.createEdgeKey(1, false));
        assertEquals(3, GHUtility.createEdgeKey(1, true));
    }

    @Test
    public void testZeroValue() {
        GHIntLongHashMap map1 = new GHIntLongHashMap();
        assertFalse(map1.containsKey(0));
        // assertFalse(map1.containsValue(0));
        map1.put(0, 3);
        map1.put(1, 0);
        map1.put(2, 1);

        // assertTrue(map1.containsValue(0));
        assertEquals(3, map1.get(0));
        assertEquals(0, map1.get(1));
        assertEquals(1, map1.get(2));

        // instead of assertEquals(-1, map1.get(3)); with hppc we have to check before:
        assertTrue(map1.containsKey(0));

        // trove4j behaviour was to return -1 if non existing:
//        TIntLongHashMap map2 = new TIntLongHashMap(100, 0.7f, -1, -1);
//        assertFalse(map2.containsKey(0));
//        assertFalse(map2.containsValue(0));
//        map2.add(0, 3);
//        map2.add(1, 0);
//        map2.add(2, 1);
//        assertTrue(map2.containsKey(0));
//        assertTrue(map2.containsValue(0));
//        assertEquals(3, map2.get(0));
//        assertEquals(0, map2.get(1));
//        assertEquals(1, map2.get(2));
//        assertEquals(-1, map2.get(3));
    }
}
