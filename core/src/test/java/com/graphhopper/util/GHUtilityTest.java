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
import com.graphhopper.routing.util.AllEdgesIterator;
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
    private final EncodingManager encodingManager = EncodingManager.create(carEncoder);
    private final BooleanEncodedValue accessEnc = carEncoder.getAccessEnc();

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
    Graph initUnsorted(Graph g, FlagEncoder encoder) {
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
        GHUtility.setSpeed(60, true, true, encoder, g.edge(8, 2).setDistance(0.5));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(7, 3).setDistance(2.1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 0).setDistance(3.9));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(7, 5).setDistance(0.7));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 2).setDistance(1.9));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(8, 1).setDistance(2.05));
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
        Graph g = initUnsorted(createGraph(), carEncoder);
        Graph newG = GHUtility.sortDFS(g, createGraph());
        assertEquals(g.getNodes(), newG.getNodes());
        assertEquals(g.getEdges(), newG.getEdges());
        NodeAccess na = newG.getNodeAccess();
        assertEquals(0, na.getLatitude(0), 1e-4); // 0
        assertEquals(2.5, na.getLatitude(1), 1e-4); // 1
        assertEquals(4.5, na.getLatitude(2), 1e-4); // 2
        assertEquals(4.6, na.getLatitude(3), 1e-4); // 8                
        assertEquals(3.0, na.getLatitude(4), 1e-4); // 3
        assertEquals(5.0, na.getLatitude(5), 1e-4); // 7
        assertEquals(4.2, na.getLatitude(6), 1e-4); // 5
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
        GHUtility.setSpeed(60, true, false, carEncoder, g.edge(0, 1).setDistance(1.1));
        GHUtility.setSpeed(60, true, false, carEncoder, g.edge(2, 1).setDistance(1.1));
        GHUtility.sortDFS(g, createGraph());
    }

    @Test
    public void testCopyWithSelfRef() {
        Graph g = initUnsorted(createGraph(), carEncoder);
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(0, 0).setDistance(11));

        Graph g2 = new GraphBuilder(encodingManager).create();
        GHUtility.copyTo(g, g2);

        assertEquals(g.getEdges(), g2.getEdges());
    }

    @Test
    public void testCopy() {
        Graph g = initUnsorted(createGraph(), carEncoder);
        EdgeIteratorState edgeState = GHUtility.setSpeed(60, true, true, carEncoder, g.edge(6, 5).setDistance(11));
        edgeState.setWayGeometry(Helper.createPointList(12, 10, -1, 3));

        GraphHopperStorage newStore = new GraphBuilder(encodingManager).setCHConfigs(CHConfig.nodeBased("p2", new FastestWeighting(carEncoder))).create();
        Graph lg = new GraphBuilder(encodingManager).create();
        GHUtility.copyTo(g, lg);
        newStore.freeze();

        edgeState = GHUtility.getEdge(lg, 5, 6);
        assertEquals(Helper.createPointList(-1, 3, 12, 10), edgeState.fetchWayGeometry(FetchMode.PILLAR_ONLY));

        NodeAccess na = lg.getNodeAccess();
        assertEquals(0, na.getLatitude(0), 1e-6);
        assertEquals(1, na.getLongitude(0), 1e-6);
        assertEquals(2.5, na.getLatitude(1), 1e-6);
        assertEquals(4.5, na.getLongitude(1), 1e-6);
        assertEquals(9, lg.getNodes());
        EdgeIterator iter = lg.createEdgeExplorer().setBaseNode(8);
        iter.next();
        assertEquals(2.05, iter.getDistance(), 1e-6);
        assertTrue(iter.getReverse(accessEnc));
        assertTrue(iter.get(accessEnc));
        iter.next();
        assertEquals(0.5, iter.getDistance(), 1e-6);
        assertTrue(iter.getReverse(accessEnc));
        assertTrue(iter.get(accessEnc));

        iter = lg.createEdgeExplorer().setBaseNode(7);
        iter.next();
        assertEquals(.7, iter.getDistance(), 1e-6);

        iter.next();
        assertEquals(2.1, iter.getDistance(), 1e-6);
        assertFalse(iter.getReverse(accessEnc));
        assertTrue(iter.get(accessEnc));
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

        assertEquals(6, GHUtility.createEdgeKey(1, 1, 3, false));
        assertEquals(6, GHUtility.createEdgeKey(1, 1, 3, true));

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
