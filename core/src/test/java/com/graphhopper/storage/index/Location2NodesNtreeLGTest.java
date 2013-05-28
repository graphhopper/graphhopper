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
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.Helper;
import gnu.trove.list.TIntList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class Location2NodesNtreeLGTest extends Location2NodesNtreeTest {

    @Override
    public Location2NodesNtreeLG createIndex(Graph g, int resolution) {
        Directory dir = new RAMDirectory(location);
        Location2NodesNtreeLG idx = new Location2NodesNtreeLG((LevelGraph) g, dir);
        idx.resolution(1000000).prepareIndex();
        return idx;
    }

    @Override
    LevelGraph createGraph(Directory dir) {
        return new LevelGraphStorage(dir).create(100);
    }

    @Test
    public void testLevelGraph() {
        LevelGraph g = createGraph(new RAMDirectory());
        // 0
        // 1
        // 2
        //  3
        //   4

        g.setNode(0, 1, 0);
        g.setNode(1, 0.5, 0);
        g.setNode(2, 0, 0);
        g.setNode(3, -1, 1);
        g.setNode(4, -2, 2);

        EdgeIterator iter1 = g.edge(0, 1, 10, true);
        EdgeIterator iter2 = g.edge(1, 2, 10, true);
        EdgeIterator iter3 = g.edge(2, 3, 14, true);
        EdgeIterator iter4 = g.edge(3, 4, 14, true);

        // create shortcuts
        EdgeSkipIterator iter5 = g.edge(0, 2, 20, true);
        iter5.skippedEdges(iter1.edge(), iter2.edge());
        EdgeSkipIterator iter6 = g.edge(2, 4, 28, true);
        iter6.skippedEdges(iter3.edge(), iter4.edge());
        g.edge(0, 4, 40, true).skippedEdges(iter5.edge(), iter6.edge());

        Location2IDIndex index = createIndex(g, -1);
        assertEquals(2, index.findID(0, 0.5));
    }

    @Test
    public void testSortHighLevelFirst() {
        LevelGraph lg = createGraph(new RAMDirectory());
        lg.setLevel(1, 10);
        lg.setLevel(2, 30);
        lg.setLevel(3, 20);
        TIntList tlist = Helper.createTList(1, 2, 3);
        new Location2NodesNtreeLG(lg, new RAMDirectory()).sortNodes(tlist);
        assertEquals(Helper.createTList(2, 3, 1), tlist);
    }

    @Test
    public void testLevelGraphBug() {
        // 0
        // |
        // | X  2--3
        // |
        // 1

        LevelGraphStorage lg = (LevelGraphStorage) createGraph(new RAMDirectory());
        lg.setNode(0, 1, 0);
        lg.setNode(1, 0, 0);
        lg.setNode(2, 0.5, 0.5);
        lg.setNode(3, 0.5, 1);
        EdgeIterator iter1 = lg.edge(1, 0, 100, true);
        EdgeIterator iter2 = lg.edge(2, 3, 100, true);

        lg.setLevel(0, 11);
        lg.setLevel(1, 10);
        // disconnect higher 0 from lower 1
        lg.disconnect(iter1, EdgeIterator.NO_EDGE, false);

        lg.setLevel(2, 12);
        lg.setLevel(3, 13);
        // disconnect higher 3 from lower 2
        lg.disconnect(iter1, EdgeIterator.NO_EDGE, false);

        Location2NodesNtreeLG index = new Location2NodesNtreeLG(lg, new RAMDirectory());
        index.resolution(100000);
        index.prepareIndex();
        // very close to 2, but should match the edge 0--1
        TIntHashSet set = index.findNetworkEntries(0.51, 0.2);
        TIntSet expectedSet = new TIntHashSet();
        expectedSet.add(1);
        expectedSet.add(2);
        assertEquals(expectedSet, set);
        assertEquals(0, index.findID(0.51, 0.2));
    }
}
