/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
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

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.EdgeSkipIterState;
import com.graphhopper.util.Helper;
import gnu.trove.list.TIntList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class LocationIndexTreeSCTest extends LocationIndexTreeTest
{
    @Override
    public LocationIndexTreeSC createIndex( Graph g, int resolution )
    {
        Directory dir = new RAMDirectory(location);
        LocationIndexTreeSC idx = new LocationIndexTreeSC((LevelGraph) g, dir);
        idx.setResolution(1000000).prepareIndex();
        return idx;
    }

    @Override
    LevelGraph createGraph( Directory dir, EncodingManager encodingManager )
    {
        return new LevelGraphStorage(dir, encodingManager).create(100);
    }

    @Test
    public void testLevelGraph()
    {
        LevelGraph g = createGraph(new RAMDirectory(), encodingManager);
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

        EdgeIteratorState iter1 = g.edge(0, 1, 10, true);
        EdgeIteratorState iter2 = g.edge(1, 2, 10, true);
        EdgeIteratorState iter3 = g.edge(2, 3, 14, true);
        EdgeIteratorState iter4 = g.edge(3, 4, 14, true);

        // create shortcuts
        FlagEncoder car = encodingManager.getEncoder("CAR");
        long flags = car.setProperties(60, true, true);
        EdgeSkipIterState iter5 = g.shortcut(0, 2);
        iter5.setDistance(20).setFlags(flags);
        iter5.setSkippedEdges(iter1.getEdge(), iter2.getEdge());
        EdgeSkipIterState iter6 = g.shortcut(2, 4);
        iter6.setDistance(28).setFlags(flags);
        iter6.setSkippedEdges(iter3.getEdge(), iter4.getEdge());
        EdgeSkipIterState tmp = g.shortcut(0, 4);
        tmp.setDistance(40).setFlags(flags);
        tmp.setSkippedEdges(iter5.getEdge(), iter6.getEdge());

        LocationIndex index = createIndex(g, -1);
        assertEquals(2, index.findID(0, 0.5));
    }

    @Test
    public void testSortHighLevelFirst()
    {
        final LevelGraph lg = createGraph(new RAMDirectory(), encodingManager);
        lg.setLevel(1, 10);
        lg.setLevel(2, 30);
        lg.setLevel(3, 20);
        TIntList tlist = Helper.createTList(1, 2, 3);

        // nodes with high level should come first to be covered by lower level nodes
        ArrayList<Integer> list = Helper.tIntListToArrayList(tlist);
        Collections.sort(list, new Comparator<Integer>()
        {
            @Override
            public int compare( Integer o1, Integer o2 )
            {
                return lg.getLevel(o2) - lg.getLevel(o1);
            }
        });
        tlist.clear();
        tlist.addAll(list);
        assertEquals(Helper.createTList(2, 3, 1), tlist);
    }

    @Test
    public void testLevelGraphBug()
    {
        // 0
        // |
        // | X  2--3
        // |
        // 1

        LevelGraphStorage lg = (LevelGraphStorage) createGraph(new RAMDirectory(), encodingManager);
        lg.setNode(0, 1, 0);
        lg.setNode(1, 0, 0);
        lg.setNode(2, 0.5, 0.5);
        lg.setNode(3, 0.5, 1);
        EdgeIteratorState iter1 = lg.edge(1, 0, 100, true);
        EdgeIteratorState iter2 = lg.edge(2, 3, 100, true);

        lg.setLevel(0, 11);
        lg.setLevel(1, 10);
        // disconnect higher 0 from lower 1
        lg.disconnect(lg.createEdgeExplorer(), iter1);

        lg.setLevel(2, 12);
        lg.setLevel(3, 13);
        // disconnect higher 3 from lower 2
        lg.disconnect(lg.createEdgeExplorer(), iter1);

        LocationIndexTreeSC index = new LocationIndexTreeSC(lg, new RAMDirectory());
        index.setResolution(100000);
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
