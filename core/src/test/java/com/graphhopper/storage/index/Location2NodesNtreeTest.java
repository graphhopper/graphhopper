/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
import com.graphhopper.util.EdgeIterator;
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
        return internalCreateIndex(g, 500000);
    }

    public Location2NodesNtree internalCreateIndex(Graph g, int minMeter) {
        Directory dir = new RAMDirectory(location);
        Location2NodesNtree idx = new Location2NodesNtree(g, dir);
        idx.resolution(minMeter).prepareIndex();
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
        index.minResolutionInMeter(50000).prepareAlgo();
        Location2NodesNtree.InMemConstructionIndex inMemIndex = index.prepareInMemIndex();
        assertEquals(Helper.createTList(4, 4), index.getEntries());

        assertEquals(3, inMemIndex.getEntriesOf(0).size());
        assertEquals(8, inMemIndex.getEntriesOf(1).size());
        assertEquals(0, inMemIndex.getEntriesOf(2).size());
        // [LEAF 0 {} {0, 2}, LEAF 2 {} {0, 1}, LEAF 1 {} {2}, LEAF 3 {} {1}, LEAF 8 {} {0}, LEAF 10 {} {0}, LEAF 9 {} {0}, LEAF 4 {} {2}, LEAF 6 {} {0, 1, 2, 3}, LEAF 5 {} {0, 2, 3}, LEAF 7 {} {1, 2, 3}, LEAF 13 {} {1}]        
        // System.out.println(inMemIndex.getLayer(2));

        index.dataAccess.create(10);
        inMemIndex.store(inMemIndex.root, Location2NodesNtree.START_POINTER);
        // [LEAF 0 {2} {},    LEAF 2 {1} {},    LEAF 1 {2} {}, LEAF 3 {1} {}, LEAF 8 {0} {}, LEAF 10 {0} {}, LEAF 9 {0} {}, LEAF 4 {2} {}, LEAF 6 {0, 3} {},       LEAF 5 {0, 2, 3} {}, LEAF 7 {1, 2, 3} {}, LEAF 13 {1} {}]
        // System.out.println(inMemIndex.getLayer(2));

        index.searchRegion(false);
        TIntHashSet set = new TIntHashSet();
        set.add(0);
        assertEquals(set, index.findNetworkEntries(0.5, -0.5));
        assertEquals(set, index.findNetworkEntries(-0.5, -0.9));
        assertEquals(2, index.findID(-0.5, -0.9));

        // The optimization if(dist > normedHalf) => feed nodeA or nodeB
        // although this reduces chance of nodes outside of the tile
        // in practice it even increases file size!?
        // Is this due to the LevelGraph disconnect problem?
//        set.clear();
//        set.add(4);
//        assertEquals(set, index.findNetworkEntries(-0.7, 1.5));
//        
//        set.clear();
//        set.add(4);
//        assertEquals(set, index.findNetworkEntries(-0.5, 0.5));
    }

    @Test
    public void testInMemIndex2() {
        Graph graph = createTestGraph2();
        Location2NodesNtree index = new Location2NodesNtree(graph, new RAMDirectory());
        index.minResolutionInMeter(500).prepareAlgo();
        Location2NodesNtree.InMemConstructionIndex inMemIndex = index.prepareInMemIndex();
        assertEquals(Helper.createTList(4, 4), index.getEntries());
        assertEquals(1, inMemIndex.getEntriesOf(0).size());
        assertEquals(4, inMemIndex.getEntriesOf(1).size());
        assertEquals(0, inMemIndex.getEntriesOf(2).size());

        index.dataAccess.create(10);
        inMemIndex.store(inMemIndex.root, Location2NodesNtree.START_POINTER);
        index.searchRegion(false);

        // 0
        assertEquals(2L, index.keyAlgo.encode(49.94653, 11.57114));
        // 1
        assertEquals(3L, index.keyAlgo.encode(49.94653, 11.57214));
        // 28
        assertEquals(3L, index.keyAlgo.encode(49.95053, 11.57714));
        // 29
        assertEquals(6L, index.keyAlgo.encode(49.95053, 11.57814));
        // 8
        assertEquals(1L, index.keyAlgo.encode(49.94553, 11.57214));
        // 34
        assertEquals(9L, index.keyAlgo.encode(49.95153, 11.57714));

        // Query near point 25 (49.95053, 11.57314).
        // If we would have a perfect compaction (takes a lot longer) we would
        // get only 0 or any node in the lefter greater subgraph.
        // The other subnetwork is already perfect {26}.
        // For compaction see: https://github.com/graphhopper/graphhopper/blob/5594f7f9d98d932f365557dc37b4b2d3b7abf698/core/src/main/java/com/graphhopper/storage/index/Location2NodesNtree.java#L277
        TIntHashSet set = new TIntHashSet();
        set.add(16);
        set.add(26);
        set.add(27);
        set.add(28);
        assertEquals(set, index.findNetworkEntries(49.950, 11.5732));
    }

    @Test
    public void testInMemIndex3() {
        Graph graph = createTestGraph();
        Location2NodesNtree index = new Location2NodesNtree(graph, new RAMDirectory());
        index.minResolutionInMeter(10000).prepareAlgo();
        Location2NodesNtree.InMemConstructionIndex inMemIndex = index.prepareInMemIndex();
        assertEquals(Helper.createTList(64, 4), index.getEntries());

        assertEquals(33, inMemIndex.getEntriesOf(0).size());
        assertEquals(59, inMemIndex.getEntriesOf(1).size());
        assertEquals(0, inMemIndex.getEntriesOf(2).size());

        index.dataAccess.create(1024);
        inMemIndex.store(inMemIndex.root, Location2NodesNtree.START_POINTER);
        assertEquals(1 << 20, index.capacity());

        LocationIDResult res = index.findClosest(-.5, -.5, EdgeFilter.ALL_EDGES);
        assertEquals(1, res.closestNode());
    }

    @Test
    public void testReverseSpatialKey() {
        Location2NodesNtree index = new Location2NodesNtree(createTestGraph(), new RAMDirectory());
        index.minResolutionInMeter(200).prepareAlgo();
        assertEquals(Helper.createTList(64, 64, 64, 4), index.getEntries());

        // 10111110111110101010
        assertEquals("01010101111101111101", BitUtil.toBitString(index.createReverseKey(1.7, 0.099), 20));
    }

    @Test
    public void testMoreReal() {
        Graph graph = createGraph();
        graph.setNode(1, 51.2492152, 9.4317166);
        graph.setNode(0, 52, 9);
        graph.setNode(2, 51.2, 9.4);
        graph.setNode(3, 49, 10);

        graph.edge(1, 0, 1000, true);
        graph.edge(0, 2, 1000, true);
        graph.edge(0, 3, 1000, true).wayGeometry(Helper.createPointList(51.21, 9.43));
        Location2IDIndex index = internalCreateIndex(graph, 1000);
        assertEquals(2, index.findID(51.2, 9.4));
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
        Location2IDIndex index = createIndex(g, 1000);
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
    public void testEdgeFilter() {
        Graph g = createTestGraph();
        Location2NodesNtree index = (Location2NodesNtree) createIndex(g, 1000);

        assertEquals(1, index.findClosest(-.6, -.6, EdgeFilter.ALL_EDGES).closestNode());
        assertEquals(2, index.findClosest(-.6, -.6, new EdgeFilter() {
            @Override public boolean accept(EdgeIterator iter) {
                return iter.baseNode() == 2 || iter.adjNode() == 2;
            }
        }).closestNode());
    }

    // see testgraph2.jpg
    Graph createTestGraph2() {
        Graph graph = createGraph(new RAMDirectory());

        graph.setNode(8, 49.94553, 11.57214);
        graph.setNode(9, 49.94553, 11.57314);
        graph.setNode(10, 49.94553, 11.57414);
        graph.setNode(11, 49.94553, 11.57514);
        graph.setNode(12, 49.94553, 11.57614);
        graph.setNode(13, 49.94553, 11.57714);

        graph.setNode(0, 49.94653, 11.57114);
        graph.setNode(1, 49.94653, 11.57214);
        graph.setNode(2, 49.94653, 11.57314);
        graph.setNode(3, 49.94653, 11.57414);
        graph.setNode(4, 49.94653, 11.57514);
        graph.setNode(5, 49.94653, 11.57614);
        graph.setNode(6, 49.94653, 11.57714);
        graph.setNode(7, 49.94653, 11.57814);

        graph.setNode(14, 49.94753, 11.57214);
        graph.setNode(15, 49.94753, 11.57314);
        graph.setNode(16, 49.94753, 11.57614);
        graph.setNode(17, 49.94753, 11.57814);

        graph.setNode(18, 49.94853, 11.57114);
        graph.setNode(19, 49.94853, 11.57214);
        graph.setNode(20, 49.94853, 11.57814);

        graph.setNode(21, 49.94953, 11.57214);
        graph.setNode(22, 49.94953, 11.57614);

        graph.setNode(23, 49.95053, 11.57114);
        graph.setNode(24, 49.95053, 11.57214);
        graph.setNode(25, 49.95053, 11.57314);
        graph.setNode(26, 49.95053, 11.57514);
        graph.setNode(27, 49.95053, 11.57614);
        graph.setNode(28, 49.95053, 11.57714);
        graph.setNode(29, 49.95053, 11.57814);

        graph.setNode(30, 49.95153, 11.57214);
        graph.setNode(31, 49.95153, 11.57314);
        graph.setNode(32, 49.95153, 11.57514);
        graph.setNode(33, 49.95153, 11.57614);
        graph.setNode(34, 49.95153, 11.57714);

        graph.setNode(34, 49.95153, 11.57714);

        // to create correct bounds
        // bottom left
        graph.setNode(100, 49.94053, 11.56614);
        // bottom right
        graph.setNode(101, 49.96053, 11.58814);

        graph.edge(0, 1, 10, true);
        graph.edge(1, 2, 10, true);
        graph.edge(2, 3, 10, true);
        graph.edge(3, 4, 10, true);
        graph.edge(4, 5, 10, true);
        graph.edge(6, 7, 10, true);

        graph.edge(8, 2, 10, true);
        graph.edge(9, 2, 10, true);
        graph.edge(10, 3, 10, true);
        graph.edge(11, 4, 10, true);
        graph.edge(12, 5, 10, true);
        graph.edge(13, 6, 10, true);

        graph.edge(1, 14, 10, true);
        graph.edge(2, 15, 10, true);
        graph.edge(5, 16, 10, true);
        graph.edge(14, 15, 10, true);
        graph.edge(16, 17, 10, true);
        graph.edge(16, 20, 10, true);
        graph.edge(16, 25, 10, true);

        graph.edge(18, 14, 10, true);
        graph.edge(18, 19, 10, true);
        graph.edge(18, 21, 10, true);
        graph.edge(19, 21, 10, true);
        graph.edge(21, 24, 10, true);
        graph.edge(23, 24, 10, true);
        graph.edge(24, 25, 10, true);
        graph.edge(26, 27, 10, true);
        graph.edge(27, 28, 10, true);
        graph.edge(28, 29, 10, true);

        graph.edge(24, 30, 10, true);
        graph.edge(24, 31, 10, true);
        graph.edge(26, 32, 10, true);
        graph.edge(27, 33, 10, true);
        graph.edge(28, 34, 10, true);
        return graph;
    }
}
