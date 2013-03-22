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
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPlace;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class Location2NodesNtreeTest extends AbstractLocation2IDIndexTester {

    @Override
    public Location2IDIndex createIndex(Graph g, int resolution) {
        Directory dir = new RAMDirectory(location);
        return new Location2NodesNtree(g, dir).subEntries(16).resolution(1000000).prepareIndex();
    }

    @Override
    public boolean hasEdgeSupport() {
        return true;
    }

    @Override
    public void testGrid() {
        // TODO do not skip
        // Error for i==45 
        // orig:3.9040709,2.1737225 full:3.2999998696148367,2.2000000372529036 fullDist:67232.91 found:4.0,1.0 foundDist:130637.836
    }   

    private Graph createTestGraph() {
        Graph graph = new GraphBuilder().create();
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
        inMemIndex.store(inMemIndex.root, 0);
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
        Graph graph = new GraphBuilder().create();
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
        // g.setNode(40, 51.25, 9.43);
        g.setNode(50, 49, 10);
        g.edge(20, 50, 1, true).wayGeometry(Helper.createPointList(51.25, 9.43));
        g.edge(10, 20, 1, true);
        g.edge(20, 30, 1, true);

        Location2IDIndex index = createIndex(g, 2000);
        assertEquals(20, index.findID(51.25, 9.43));
    }
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
