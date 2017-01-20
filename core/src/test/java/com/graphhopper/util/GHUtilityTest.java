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

import com.carrotsearch.hppc.LongArrayList;
import com.graphhopper.coll.GHIntLongHashMap;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.*;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GHUtilityTest {
    private final FlagEncoder carEncoder = new CarFlagEncoder();
    private final EncodingManager encodingManager = new EncodingManager(carEncoder);

    Graph createGraph() {
        return new GraphBuilder(encodingManager).create();
    }

    // 7      8\
    // | \    | 2
    // |  5   | |
    // 3    4 | |
    //   6     \1
    //   ______/
    // 0/
    Graph initUnsorted(Graph g) {
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
        g.edge(8, 2, 0.5, true);
        g.edge(7, 3, 2.1, false);
        g.edge(1, 0, 3.9, true);
        g.edge(7, 5, 0.7, true);
        g.edge(1, 2, 1.9, true);
        g.edge(8, 1, 2.05, true);
        return g;
    }

    @Test
    public void testSort() {
        Graph g = initUnsorted(createGraph());
        Graph newG = GHUtility.sortDFS(g, createGraph());
        assertEquals(g.getNodes(), newG.getNodes());
        NodeAccess na = newG.getNodeAccess();
        assertEquals(0, na.getLatitude(0), 1e-4); // 0
        assertEquals(2.5, na.getLatitude(1), 1e-4); // 1
        assertEquals(4.5, na.getLatitude(2), 1e-4); // 2
        assertEquals(4.6, na.getLatitude(3), 1e-4); // 8                
        assertEquals(3.0, na.getLatitude(4), 1e-4); // 3
        assertEquals(5.0, na.getLatitude(5), 1e-4); // 7
        assertEquals(4.2, na.getLatitude(6), 1e-4); // 5
    }

    @Test
    public void testSort2() {
        Graph g = initUnsorted(createGraph());
        Graph newG = GHUtility.sortDFS(g, createGraph());
        assertEquals(g.getNodes(), newG.getNodes());
        NodeAccess na = newG.getNodeAccess();
        assertEquals(0, na.getLatitude(0), 1e-4); // 0
        assertEquals(2.5, na.getLatitude(1), 1e-4); // 1
        assertEquals(4.5, na.getLatitude(2), 1e-4); // 2
        assertEquals(4.6, na.getLatitude(3), 1e-4); // 8        
    }

    @Test
    public void testSortDirected() {
        Graph g = createGraph();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 1);
        na.setNode(1, 2.5, 2);
        na.setNode(2, 3.5, 3);
        g.edge(0, 1, 1.1, false);
        g.edge(2, 1, 1.1, false);
        GHUtility.sortDFS(g, createGraph());
    }

    @Test
    public void testCopyWithSelfRef() {
        Graph g = initUnsorted(createGraph());
        g.edge(0, 0, 11, true);

        CHGraph lg = new GraphBuilder(encodingManager).chGraphCreate(new FastestWeighting(carEncoder));
        GHUtility.copyTo(g, lg);

        assertEquals(g.getAllEdges().getMaxId(), lg.getAllEdges().getMaxId());
    }

    @Test
    public void testCopy() {
        Graph g = initUnsorted(createGraph());
        EdgeIteratorState edgeState = g.edge(6, 5, 11, true);
        edgeState.setWayGeometry(Helper.createPointList(12, 10, -1, 3));

        GraphHopperStorage newStore = new GraphBuilder(encodingManager).setCHGraph(new FastestWeighting(carEncoder)).create();
        CHGraph lg = newStore.getGraph(CHGraph.class);
        GHUtility.copyTo(g, lg);
        newStore.freeze();

        edgeState = GHUtility.getEdge(lg, 5, 6);
        assertEquals(Helper.createPointList(-1, 3, 12, 10), edgeState.fetchWayGeometry(0));

        assertEquals(0, lg.getLevel(0));
        assertEquals(0, lg.getLevel(1));
        NodeAccess na = lg.getNodeAccess();
        assertEquals(0, na.getLatitude(0), 1e-6);
        assertEquals(1, na.getLongitude(0), 1e-6);
        assertEquals(2.5, na.getLatitude(1), 1e-6);
        assertEquals(4.5, na.getLongitude(1), 1e-6);
        assertEquals(9, lg.getNodes());
        EdgeIterator iter = lg.createEdgeExplorer().setBaseNode(8);
        iter.next();
        assertEquals(2.05, iter.getDistance(), 1e-6);
        assertTrue(iter.isBackward(carEncoder));
        assertTrue(iter.isForward(carEncoder));
        iter.next();
        assertEquals(0.5, iter.getDistance(), 1e-6);
        assertTrue(iter.isBackward(carEncoder));
        assertTrue(iter.isForward(carEncoder));

        iter = lg.createEdgeExplorer().setBaseNode(7);
        iter.next();
        assertEquals(.7, iter.getDistance(), 1e-6);

        iter.next();
        assertEquals(2.1, iter.getDistance(), 1e-6);
        assertFalse(iter.isBackward(carEncoder));
        assertTrue(iter.isForward(carEncoder));
        assertFalse(iter.next());
    }

    @Test
    public void testEdgeStuff() {
        assertEquals(6, GHUtility.createEdgeKey(1, 2, 3, false));
        assertEquals(7, GHUtility.createEdgeKey(2, 1, 3, false));
        assertEquals(7, GHUtility.createEdgeKey(1, 2, 3, true));
        assertEquals(6, GHUtility.createEdgeKey(2, 1, 3, true));

        assertEquals(8, GHUtility.createEdgeKey(1, 2, 4, false));
        assertEquals(9, GHUtility.createEdgeKey(2, 1, 4, false));

        assertTrue(GHUtility.isSameEdgeKeys(GHUtility.createEdgeKey(1, 2, 4, false), GHUtility.createEdgeKey(1, 2, 4, false)));
        assertTrue(GHUtility.isSameEdgeKeys(GHUtility.createEdgeKey(2, 1, 4, false), GHUtility.createEdgeKey(1, 2, 4, false)));
        assertFalse(GHUtility.isSameEdgeKeys(GHUtility.createEdgeKey(1, 2, 4, false), GHUtility.createEdgeKey(1, 2, 5, false)));
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
//        map2.put(0, 3);
//        map2.put(1, 0);
//        map2.put(2, 1);
//        assertTrue(map2.containsKey(0));
//        assertTrue(map2.containsValue(0));
//        assertEquals(3, map2.get(0));
//        assertEquals(0, map2.get(1));
//        assertEquals(1, map2.get(2));
//        assertEquals(-1, map2.get(3));
    }
}
