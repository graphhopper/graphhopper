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

import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPlace;
import gnu.trove.list.TIntList;
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
        return new Location2NodesNtree(g, dir).subEntries(4).prepareIndex(1000000);
    }

    @Override
    public boolean hasEdgeSupport() {
        return true;
    }

//    @Test
//    public void testPrint() {
//        Graph g = createGraph();
//        initSimpleGraph(g);
//
//        System.out.println(BitUtil.fromBitString2Long("0000100001100111"));        
//        System.out.println(BitUtil.fromBitString2Long("1011100001100111"));
//        
//        Location2NodesNtree index = new Location2NodesNtree(g, new RAMDirectory()).subEntries(16).minResolutionInMeter(10000);
//        index.prepareAlgo();
//        Location2NodesNtree.InMemConstructionIndex inMemIndex = index.prepareIndex();
//        System.out.println("TREE");
//        System.out.println(inMemIndex.print());
//    }

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
        Location2NodesNtree.InMemConstructionIndex inMemIndex = index.prepareIndex();

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

        TIntList list = index.findIDs(new GHPlace(-.5, -.5), Location2NodesNtree.ALL_EDGES);
        assertEquals(Helper.createTList(1), list);
    }

    @Test
    public void reverseSpatialKey() {
        Location2NodesNtree index = new Location2NodesNtree(createTestGraph(), new RAMDirectory());
        index.subEntries(4).minResolutionInMeter(500).prepareAlgo();
        // 10111110111110101010
        assertEquals("01010101111101111101", BitUtil.toBitString(index.createReverseKey(1.7, 0.099), 20));
    }
}
