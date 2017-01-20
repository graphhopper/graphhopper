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
package com.graphhopper.storage.index;

import com.carrotsearch.hppc.IntSet;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class LocationIndexTreeCHTest extends LocationIndexTreeTest {
    @Override
    public LocationIndexTree createIndex(Graph g, int resolution) {
        if (resolution < 0)
            resolution = 500000;
        return (LocationIndexTree) createIndexNoPrepare(g, resolution).prepareIndex();
    }

    @Override
    public LocationIndexTree createIndexNoPrepare(Graph g, int resolution) {
        Directory dir = new RAMDirectory(location);
        LocationIndexTree tmpIdx = new LocationIndexTree(g, dir);
        tmpIdx.setResolution(resolution);
        return tmpIdx;
    }

    @Override
    GraphHopperStorage createGHStorage(Directory dir, EncodingManager encodingManager, boolean is3D) {
        return new GraphHopperStorage(Arrays.asList(new FastestWeighting(encodingManager.getEncoder("car"))), dir, encodingManager, is3D, new GraphExtension.NoOpExtension()).
                create(100);
    }

    @Test
    public void testCHGraph() {
        GraphHopperStorage ghStorage = createGHStorage(new RAMDirectory(), encodingManager, false);
        CHGraph lg = ghStorage.getGraph(CHGraph.class);
        // 0
        // 1
        // 2
        //  3
        //   4
        NodeAccess na = ghStorage.getNodeAccess();
        na.setNode(0, 1, 0);
        na.setNode(1, 0.5, 0);
        na.setNode(2, 0, 0);
        na.setNode(3, -1, 1);
        na.setNode(4, -2, 2);

        EdgeIteratorState iter1 = ghStorage.edge(0, 1, 10, true);
        EdgeIteratorState iter2 = ghStorage.edge(1, 2, 10, true);
        EdgeIteratorState iter3 = ghStorage.edge(2, 3, 14, true);
        EdgeIteratorState iter4 = ghStorage.edge(3, 4, 14, true);

        // create shortcuts
        ghStorage.freeze();
        FlagEncoder car = encodingManager.getEncoder("car");
        long flags = car.setProperties(60, true, true);
        CHEdgeIteratorState iter5 = lg.shortcut(0, 2);
        iter5.setDistance(20).setFlags(flags);
        iter5.setSkippedEdges(iter1.getEdge(), iter2.getEdge());
        CHEdgeIteratorState iter6 = lg.shortcut(2, 4);
        iter6.setDistance(28).setFlags(flags);
        iter6.setSkippedEdges(iter3.getEdge(), iter4.getEdge());
        CHEdgeIteratorState tmp = lg.shortcut(0, 4);
        tmp.setDistance(40).setFlags(flags);
        tmp.setSkippedEdges(iter5.getEdge(), iter6.getEdge());

        LocationIndex index = createIndex(ghStorage, -1);
        assertEquals(2, findID(index, 0, 0.5));
    }

    @Test
    public void testSortHighLevelFirst() {
        GraphHopperStorage g = createGHStorage(new RAMDirectory(), encodingManager, false);
        final CHGraph lg = g.getGraph(CHGraph.class);
        lg.getNodeAccess().ensureNode(4);
        lg.setLevel(1, 10);
        lg.setLevel(2, 30);
        lg.setLevel(3, 20);

        // nodes with high level should come first to be covered by lower level nodes
        List<Integer> list = Arrays.asList(1, 2, 3);
        Collections.sort(list, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return lg.getLevel(o2) - lg.getLevel(o1);
            }
        });
        assertEquals("[2, 3, 1]", list.toString());
    }

    @Test
    public void testCHGraphBug() {
        // 0
        // |
        // | X  2--3
        // |
        // 1

        GraphHopperStorage g = createGHStorage(new RAMDirectory(), encodingManager, false);
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1, 0);
        na.setNode(1, 0, 0);
        na.setNode(2, 0.5, 0.5);
        na.setNode(3, 0.5, 1);
        EdgeIteratorState iter1 = g.edge(1, 0, 100, true);
        g.edge(2, 3, 100, true);

        CHGraphImpl lg = (CHGraphImpl) g.getGraph(CHGraph.class);
        g.freeze();
        lg.setLevel(0, 11);
        lg.setLevel(1, 10);
        // disconnect higher 0 from lower 1
        lg.disconnect(lg.createEdgeExplorer(), iter1);

        lg.setLevel(2, 12);
        lg.setLevel(3, 13);
        // disconnect higher 3 from lower 2
        lg.disconnect(lg.createEdgeExplorer(), iter1);

        LocationIndexTree index = createIndex(g, 100000);

        // very close to 2, but should match the edge 0--1
        GHIntHashSet set = new GHIntHashSet();
        index.findNetworkEntries(0.51, 0.2, set, 0);
        index.findNetworkEntries(0.51, 0.2, set, 1);
        IntSet expectedSet = new GHIntHashSet();
        expectedSet.add(0);
        expectedSet.add(2);
        assertEquals(expectedSet, set);

        assertEquals(0, findID(index, 0.51, 0.2));
        assertEquals(1, findID(index, 0.1, 0.1));
        assertEquals(2, findID(index, 0.51, 0.51));
        assertEquals(3, findID(index, 0.51, 1.1));
    }
}
