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

import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.search.KVStorage;
import com.graphhopper.search.KVStorage.KValue;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.graphhopper.util.EdgeIteratorState.REVERSE_STATE;
import static com.graphhopper.util.FetchMode.*;
import static com.graphhopper.util.Parameters.Details.STREET_NAME;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class BaseGraphTest extends AbstractGraphStorageTester {
    @Override
    public BaseGraph createGHStorage(String location, boolean enabled3D) {
        // reduce segment size in order to test the case where multiple segments come into the game
        BaseGraph gs = newGHStorage(new RAMDirectory(location), enabled3D, defaultSize / 2);
        gs.create(defaultSize);
        return gs;
    }

    protected BaseGraph newGHStorage(Directory dir, boolean enabled3D) {
        return newGHStorage(dir, enabled3D, -1);
    }

    protected BaseGraph newGHStorage(Directory dir, boolean enabled3D, int segmentSize) {
        return new BaseGraph.Builder(encodingManager).setDir(dir).set3D(enabled3D).setSegmentSize(segmentSize).build();
    }

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
        EdgeIteratorState iter3 = graph.edge(3, 4);
        iter1.setWayGeometry(Helper.createPointList3D(3.5, 4.5, 0, 5, 6, 0));
        graph.edge(9, 10).setDistance(200).set(carAccessEnc, true, true);
        graph.edge(9, 11).setDistance(200).set(carAccessEnc, true, true);
        graph.edge(1, 2).setDistance(120).set(carAccessEnc, true, false);

        iter1.setKeyValues(KVStorage.createKV(STREET_NAME, "named street1"));
        iter2.setKeyValues(KVStorage.createKV(STREET_NAME, "named street2"));

        Map<String, KValue> map = new LinkedHashMap<>();
        map.put("keyA", new KValue("FORWARD", null));
        map.put("keyB", new KValue(null, "BACKWARD"));
        map.put("keyC", new KValue("BOTH"));
        map.put("keyD", new KValue("BOTH2", "BOTH2"));
        iter3.setKeyValues(map);

        checkGraph(graph);
        graph.flush();
        graph.close();

        graph = newGHStorage(new MMapDirectory(defaultGraphLoc), true);
        graph.loadExisting();

        assertEquals(12, graph.getNodes());
        checkGraph(graph);

        assertEquals("named street1", graph.getEdgeIteratorState(iter1.getEdge(), iter1.getAdjNode()).getName());
        assertEquals("named street2", graph.getEdgeIteratorState(iter2.getEdge(), iter2.getAdjNode()).getName());
        iter3 = graph.getEdgeIteratorState(iter3.getEdge(), iter3.getAdjNode());
        assertEquals(map, iter3.getKeyValues());
        assertEquals(map, iter3.detach(true).getKeyValues());

        assertEquals("FORWARD", iter3.getValue("keyA"));
        assertNull(iter3.getValue("keyB"));
        assertEquals("BOTH", iter3.getValue("keyC"));
        assertNull(iter3.detach(true).getValue("keyA"));
        assertEquals("BACKWARD", iter3.detach(true).getValue("keyB"));
        assertEquals("BOTH", iter3.detach(true).getValue("keyC"));
        assertEquals("BOTH2", iter3.getValue("keyD"));

        GHUtility.setSpeed(60, true, true, carAccessEnc, carSpeedEnc, graph.edge(3, 4).setDistance(123)).
                setWayGeometry(Helper.createPointList3D(4.4, 5.5, 0, 6.6, 7.7, 0));
        checkGraph(graph);
    }

    @Test
    public void testSave_and_Freeze() {
        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), true).create(defaultSize);
        graph.edge(1, 0);
        graph.freeze();

        graph.flush();
        graph.close();

        graph = newGHStorage(new MMapDirectory(defaultGraphLoc), true);
        graph.loadExisting();
        assertEquals(2, graph.getNodes());
        assertTrue(graph.isFrozen());
    }

    protected void checkGraph(Graph g) {
        NodeAccess na = g.getNodeAccess();
        assertTrue(na.is3D());
        assertTrue(g.getBounds().isValid());

        assertEquals(new BBox(10, 20, 10, 12, 0, 1), g.getBounds());
        assertEquals(10, na.getLat(0), 1e-2);
        assertEquals(10, na.getLon(0), 1e-2);
        EdgeExplorer explorer = g.createEdgeExplorer(carOutFilter);
        assertEquals(2, GHUtility.count(explorer.setBaseNode(0)));
        assertEquals(GHUtility.asSet(2, 1), GHUtility.getNeighbors(explorer.setBaseNode(0)));

        EdgeIterator iter = explorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(Helper.createPointList3D(3.5, 4.5, 0, 5, 6, 0), iter.fetchWayGeometry(PILLAR_ONLY));

        assertTrue(iter.next());
        assertEquals(Helper.createPointList3D(1.5, 1, 0, 2, 3, 0), iter.fetchWayGeometry(PILLAR_ONLY));
        assertEquals(Helper.createPointList3D(10, 10, 0, 1.5, 1, 0, 2, 3, 0), iter.fetchWayGeometry(BASE_AND_PILLAR));
        assertEquals(Helper.createPointList3D(1.5, 1, 0, 2, 3, 0, 11, 20, 1), iter.fetchWayGeometry(PILLAR_AND_ADJ));
        assertEquals(Helper.createPointList3D(10, 10, 0, 11, 20, 1), iter.fetchWayGeometry(TOWER_ONLY));
        assertEquals(Helper.createPointList3D(11, 20, 1, 10, 10, 0), iter.detach(true).fetchWayGeometry(TOWER_ONLY));

        assertEquals(11, na.getLat(1), 1e-2);
        assertEquals(20, na.getLon(1), 1e-2);
        assertEquals(2, GHUtility.count(explorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(2, 0), GHUtility.getNeighbors(explorer.setBaseNode(1)));

        assertEquals(12, na.getLat(2), 1e-2);
        assertEquals(12, na.getLon(2), 1e-2);
        assertEquals(1, GHUtility.count(explorer.setBaseNode(2)));

        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(explorer.setBaseNode(2)));

        EdgeIteratorState eib = GHUtility.getEdge(g, 1, 2);
        assertEquals(Helper.createPointList3D(), eib.fetchWayGeometry(PILLAR_ONLY));
        assertEquals(Helper.createPointList3D(11, 20, 1), eib.fetchWayGeometry(BASE_AND_PILLAR));
        assertEquals(Helper.createPointList3D(12, 12, 0.4), eib.fetchWayGeometry(PILLAR_AND_ADJ));
        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(explorer.setBaseNode(2)));
    }

    @Test
    public void testDoThrowExceptionIfDimDoesNotMatch() {
        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), false);
        graph.create(1000);
        graph.flush();
        graph.close();

        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), true);
        assertThrows(Exception.class, () -> graph.loadExisting());
    }

    @Test
    public void testIdentical() {
        BaseGraph store = new BaseGraph.Builder(encodingManager).set3D(true).build();
        assertEquals(store.getNodes(), store.getBaseGraph().getNodes());
        assertEquals(store.getEdges(), store.getBaseGraph().getEdges());
    }

    @Test
    public void testMultipleDecoupledEdges() {
        // a typical usage where we create independent EdgeIteratorState's BUT due to the IntsRef reference they are no more independent
        BaseGraph graph = createGHStorage();
        graph.edge(0, 1).setDistance(10).set(carAccessEnc, true, true);
        graph.edge(1, 2).setDistance(10).set(carAccessEnc, true, true);

        EdgeIteratorState edge0 = graph.getEdgeIteratorState(0, Integer.MIN_VALUE);
        EdgeIteratorState edge1 = graph.getEdgeIteratorState(1, Integer.MIN_VALUE);
        edge0.set(carAccessEnc, true, false);
        edge1.set(carAccessEnc, false, true);

        assertFalse(edge1.get(carAccessEnc));
        assertTrue(edge1.getReverse(carAccessEnc));

        // obviously this should pass but as the reference is shared and freshFlags=false the edge1 flags are returned!
        // So we do not set the reference for _setFlags but just the value
        // A better solution would be if we do not allow to create IntsRef outside of the EdgeIterator API
        assertTrue(edge0.get(carAccessEnc));
        assertFalse(edge0.getReverse(carAccessEnc));
    }

    @Test
    public void testInternalReverse() {
        BaseGraph storage = createGHStorage();
        EdgeIteratorState edge = storage.edge(1, 2);
        assertFalse(edge.get(REVERSE_STATE));
        assertTrue(edge.getReverse(REVERSE_STATE));
        edge = storage.getEdgeIteratorState(edge.getEdge(), Integer.MIN_VALUE);
        assertFalse(edge.get(REVERSE_STATE));

        edge = storage.getEdgeIteratorState(edge.getEdge(), 1);
        assertTrue(edge.get(REVERSE_STATE));
        assertFalse(edge.getReverse(REVERSE_STATE));
    }

    @Test
    public void testDecoupledEdgeIteratorStates() {
        BaseGraph storage = createGHStorage();
        Graph graph = storage.getBaseGraph();
        IntsRef ref = encodingManager.createEdgeFlags();
        ref.ints[0] = 12;
        GHUtility.setSpeed(60, true, true, carAccessEnc, carSpeedEnc, graph.edge(1, 2).setDistance(10)).setFlags(ref);
        ref.ints[0] = 13;
        GHUtility.setSpeed(60, true, true, carAccessEnc, carSpeedEnc, graph.edge(1, 3).setDistance(10)).setFlags(ref);

        EdgeIterator iter = graph.createEdgeExplorer().setBaseNode(1);
        assertTrue(iter.next());
        EdgeIteratorState edge1 = iter.detach(false);

        assertTrue(iter.next());
        ref.ints[0] = 44;
        iter.setFlags(ref);

        assertEquals(44, iter.getFlags().ints[0]);
        assertEquals(13, edge1.getFlags().ints[0]);
    }

    @Test
    public void testEdgeKey() {
        BaseGraph g = new BaseGraph.Builder(encodingManager).create();
        GHUtility.setSpeed(60, true, true, carAccessEnc, carSpeedEnc, g.edge(0, 1).setDistance(10));
        // storage direction
        assertEdge(g.getEdgeIteratorState(0, Integer.MIN_VALUE), 0, 1, false, 0, 0);
        // reverse direction
        assertEdge(g.getEdgeIteratorState(0, 0), 1, 0, true, 0, 1);
        // now use the edge key to retrieve the edge
        assertEdge(g.getEdgeIteratorStateForKey(0), 0, 1, false, 0, 0);
        // opposite direction
        assertEdge(g.getEdgeIteratorStateForKey(1), 1, 0, true, 0, 1);
    }

    private void assertEdge(EdgeIteratorState edge, int base, int adj, boolean reverse, int edgeId, int key) {
        assertEquals(base, edge.getBaseNode());
        assertEquals(adj, edge.getAdjNode());
        assertEquals(reverse, edge.get(REVERSE_STATE));
        assertEquals(edgeId, edge.getEdge());
        assertEquals(key, edge.getEdgeKey());
    }

    @Test
    public void outOfBounds() {
        BaseGraph graph = createGHStorage();
        assertThrows(IllegalArgumentException.class, () -> graph.getEdgeIteratorState(0, Integer.MIN_VALUE));
    }

    @Test
    public void setGetFlagsRaw() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        IntsRef flags = new IntsRef(graph.getIntsForFlags());
        flags.ints[0] = 10;
        edge.setFlags(flags);
        assertEquals(10, edge.getFlags().ints[0]);
        flags.ints[0] = 9;
        edge.setFlags(flags);
        assertEquals(9, edge.getFlags().ints[0]);
    }

    @Test
    public void setGetFlags() {
        BaseGraph graph = createGHStorage();
        EnumEncodedValue<RoadClass> rcEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EdgeIteratorState edge = graph.edge(0, 1).set(rcEnc, RoadClass.BRIDLEWAY);
        assertEquals(RoadClass.BRIDLEWAY, edge.get(rcEnc));
        edge.set(rcEnc, RoadClass.CORRIDOR);
        assertEquals(RoadClass.CORRIDOR, edge.get(rcEnc));
    }
}
