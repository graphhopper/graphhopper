/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage.index;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPlace;
import gnu.trove.set.hash.TIntHashSet;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class Location2NodesNtreeTest extends AbstractLocation2IDIndexTester {

    @Override
    public Location2NodesNtree createIndex(Graph g, int resolution) {
        return internCreateIndex(g, 16, 1000000);
    }

    Location2NodesNtree internCreateIndex(Graph g, int leafEntries, int resolution) {
        Directory dir = new RAMDirectory(location);
        Location2NodesNtree idx = new Location2NodesNtree(g, dir);
        idx.subEntries(leafEntries).resolution(resolution).prepareIndex();
        return idx;
    }

    @Override
    public boolean hasEdgeSupport() {
        return true;
    }

    Graph createTestGraph() {
        Graph graph = createGraph(new RAMDirectory());
        graph.setNode(0, 0.5, -0.5);
        graph.setNode(1, -0.5, -0.5);
        graph.setNode(2, -1, -1);
        graph.setNode(3, -0.4, 0.9);
        graph.setNode(4, -0.6, 1.6);
        graph.edge(0, 1, 1, true);
        graph.edge(0, 2, 1, true);
        graph.edge(0, 4, 1, true);
        graph.edge(1, 3, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(2, 4, 1, true);
        graph.edge(3, 4, 1, true);
        return graph;
    }

    @Test
    public void testInMemIndex() {
        Graph graph = createTestGraph();
        Location2NodesNtree index = new Location2NodesNtree(graph, new RAMDirectory());
        index.subEntries(4).minResolutionInMeter(100000).prepareAlgo();
        Location2NodesNtree.InMemConstructionIndex inMemIndex = index.prepareInMemIndex();

        assertEquals(2, index.getMaxDepth());

        assertEquals(1, inMemIndex.getLayer(0).size());
        assertEquals(4, inMemIndex.getLayer(1).size());
        assertEquals(12, inMemIndex.getLayer(2).size());
        // [LEAF 0 {} {0, 2}, LEAF 2 {} {0, 1}, LEAF 1 {} {2}, LEAF 3 {} {1}, LEAF 8 {} {0}, LEAF 10 {} {0}, LEAF 9 {} {0}, LEAF 4 {} {2}, LEAF 6 {} {0, 1, 2, 3}, LEAF 5 {} {0, 2, 3}, LEAF 7 {} {1, 2, 3}, LEAF 13 {} {1}]        
        // System.out.println(inMemIndex.getLayer(2));

        index.dataAccess.createNew(10);
        inMemIndex.store(inMemIndex.root, index.START_POINTER);
        // [LEAF 0 {2} {},    LEAF 2 {1} {},    LEAF 1 {2} {}, LEAF 3 {1} {}, LEAF 8 {0} {}, LEAF 10 {0} {}, LEAF 9 {0} {}, LEAF 4 {2} {}, LEAF 6 {0, 3} {},       LEAF 5 {0, 2, 3} {}, LEAF 7 {1, 2, 3} {}, LEAF 13 {1} {}]
        // System.out.println(inMemIndex.getLayer(2));

        index.searchRegion(false);
        
        TIntHashSet set = new TIntHashSet();
        set.add(0);
        assertEquals(set, index.findNetworkEntries(-0.5, -0.9));
        assertEquals(2, index.findID(-0.5, -0.9));
        set.clear();
        set.add(3);
        assertEquals(set, index.findNetworkEntries(-0.5, 0.5));
        set.clear();
        set.add(4);
        assertEquals(set, index.findNetworkEntries(-0.7, 1.5));
    }

    @Test
    public void testInMemIndex2() {
        Graph graph = createTestGraph();
        Location2NodesNtree index = new Location2NodesNtree(graph, new RAMDirectory());
        index.subEntries(4).minResolutionInMeter(10000).prepareAlgo();
        Location2NodesNtree.InMemConstructionIndex inMemIndex = index.prepareInMemIndex();

        assertEquals(5, index.getMaxDepth());

        assertEquals(1, inMemIndex.getLayer(0).size());
        assertEquals(2 * 2, inMemIndex.getLayer(1).size());
        assertEquals(13, inMemIndex.getLayer(2).size());
        assertEquals(37, inMemIndex.getLayer(3).size());
        assertEquals(86, inMemIndex.getLayer(4).size());
        assertEquals(153, inMemIndex.getLayer(5).size());
        assertEquals(0, inMemIndex.getLayer(6).size());

        index.dataAccess.createNew(1024);
        inMemIndex.store(inMemIndex.root, index.START_POINTER);
        assertEquals(1.0, index.calcMemInMB(), 0.01);

        LocationIDResult res = index.findClosest(new GHPlace(-.5, -.5), EdgeFilter.ALL_EDGES);
        assertEquals(1, res.closestNode());
    }

    @Test
    public void testReverseSpatialKey() {
        Location2NodesNtree index = new Location2NodesNtree(createTestGraph(), new RAMDirectory());
        index.subEntries(4).minResolutionInMeter(500).prepareAlgo();
        // 10111110111110101010
        assertEquals("01010101111101111101", BitUtil.toBitString(index.createReverseKey(1.7, 0.099), 20));
    }

    //    -1    0   1 1.5
    // --------------------
    // 1|         --A
    //  |    -0--/   \
    // 0|   / | B-\   \
    //  |  /  1/   3--4
    //  |  |/------/  /
    //-1|  2---------/
    //  |
    private Graph createTestGraphWithWayGeometry() {
        Graph graph = createGraph();
        graph.setNode(0, 0.5, -0.5);
        graph.setNode(1, -0.5, -0.5);
        graph.setNode(2, -1, -1);
        graph.setNode(3, -0.4, 0.9);
        graph.setNode(4, -0.6, 1.6);
        graph.edge(0, 1, 1, true);
        graph.edge(0, 2, 1, true);
        // insert A and B, without this we would get 0 for 0,0
        graph.edge(0, 4, 1, true).wayGeometry(Helper.createPointList(1, 1));
        graph.edge(1, 3, 1, true).wayGeometry(Helper.createPointList(0, 0));
        graph.edge(2, 3, 1, true);
        graph.edge(2, 4, 1, true);
        graph.edge(3, 4, 1, true);
        return graph;
    }

    @Test
    public void testWayGeometry() {
        Graph g = createTestGraphWithWayGeometry();
        Location2IDIndex index = createIndex(g, -1);
        assertEquals(1, index.findID(0, 0));
        assertEquals(1, index.findID(0, 0.1));
        assertEquals(1, index.findID(0.1, 0.1));
        assertEquals(1, index.findID(-0.5, -0.5));
    }

    @Test
    public void testMore() {
        Graph g = createGraph();
        g.setNode(10, 51.2492152, 9.4317166);
        g.setNode(20, 52, 9);
        g.setNode(30, 51.2, 9.4);
        g.setNode(50, 49, 10);
        g.edge(20, 50, 1, true).wayGeometry(Helper.createPointList(51.25, 9.43));
        g.edge(10, 20, 1, true);
        g.edge(20, 30, 1, true);

        Location2IDIndex index = createIndex(g, 2000);
        assertEquals(20, index.findID(51.25, 9.43));
    }

    @Test
    public void testLeafCompact() {
        Graph g = createGraph();

        Location2NodesNtree index = internCreateIndex(g, 4, 1000);
        Location2NodesNtree.InMemLeafEntry leaf = new Location2NodesNtree.InMemLeafEntry(4, 0L);
        for (int i = 0; i < 3; i++) {
            assertTrue(leaf.addNode(i));
            // assign a valid position
            g.setNode(i, 0, 0);
        }
        assertTrue(leaf.getResults().isEmpty());
        leaf.doCompact(index);
        assertEquals(3, leaf.getResults().size());

        // reduce already existing subnetworks to only one subnetwork        
        leaf = new Location2NodesNtree.InMemLeafEntry(4, 0L);
        g.edge(0, 1, 10, true);
        g.edge(1, 2, 10, true);
        g.edge(2, 3, 10, true);
        for (int i = 0; i < 4; i++) {
            assertTrue(leaf.addNode(i));
            // assign a valid position
            g.setNode(i, 0, 0);
        }
        leaf.doCompact(index);
        assertEquals(1, leaf.getResults().size());
    }

    @Test
    public void testLeaf2Subnetworks() {
        // only 2 subnetworks as one edge connects two nodes        
        Graph g = createGraph();
        g.edge(0, 1, 10, true);

        Location2NodesNtree index = internCreateIndex(g, 4, 1000);
        Location2NodesNtree.InMemLeafEntry leaf = new Location2NodesNtree.InMemLeafEntry(4, 0L);
        for (int i = 0; i < 3; i++) {
            assertTrue(leaf.addNode(i));
            // assign a valid position
            g.setNode(i, 0, 0);
        }
        assertTrue(leaf.getResults().isEmpty());
        leaf.doCompact(index);
        assertEquals(2, leaf.getResults().size());
    }
//    @Test
//    public void testDoCompactionBug() {
//        Graph g = createGraph();
//        g.setNode(0, 5, 2.1);
//        g.setNode(1, 4, 4.5);
//        g.setNode(2, 5, 5.5);
//        g.setNode(3, 1.5, 4);
//        g.setNode(4, 4, 1);
//        g.setNode(5, 4.5, 4.1);
//        g.setNode(6, 4, 3);
//
//        g.edge(0, 1, 10, true);
//        g.edge(0, 2, 10, true);
//        g.edge(1, 2, 10, true);
//        g.edge(1, 3, 10, true);
//        g.edge(0, 6, 10, true);
//
//        g.edge(4, 5, 10, true);
//
//        Location2NodesNtree index = internCreateIndex(g, 16, 1000000);
//        for (int i = 0; i < g.nodes(); i++) {
//            double lat = g.getLatitude(i);
//            double lon = g.getLongitude(i);
//            assertEquals("nodeId:" + i + " " + (float) lat + "," + (float) lon,
//                    i, index.findID(lat, lon));
//        }
//    }
    // TODO
//    @Test
//    public void testEdgeFilter() {
//        Graph g = createTestGraph();
//        Location2NodesNtree index = (Location2NodesNtree) createIndex(g, 1000);
//
//        assertEquals(1, index.findIDs(new GHPlace(-.7, -.7), Location2NodesNtree.ALL_EDGES).node);
//        assertEquals(2, index.findIDs(new GHPlace(-.7, -.7), new EdgeFilter() {
//            @Override public boolean accept(EdgeIterator iter) {
//                return iter.baseNode() == 2 || iter.adjNode() == 2;
//            }
//        }).node);
//    }
}
