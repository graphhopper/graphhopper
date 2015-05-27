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
import com.graphhopper.storage.NodeAccess;
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
public class LocationIndexTreeForLevelGraphTest extends LocationIndexTreeTest
{
    @Override
    public LocationIndexTree createIndex( Graph g, int resolution )
    {
        if (resolution < 0)
            resolution = 500000;
        return (LocationIndexTree) createIndexNoPrepare(g, resolution).prepareIndex();
    }

    @Override
    public LocationIndexTree createIndexNoPrepare( Graph g, int resolution )
    {
        Directory dir = new RAMDirectory(location);
        LocationIndexTree tmpIdx = new LocationIndexTree(g.getBaseGraph(), dir);
        tmpIdx.setResolution(resolution);
        return tmpIdx;
    }

    @Override
    LevelGraph createGraph( Directory dir, EncodingManager encodingManager, boolean is3D )
    {
        return new LevelGraphStorage(dir, encodingManager, is3D).create(100);
    }

    @Test
    public void testLevelGraph()
    {
        LevelGraph g = createGraph(new RAMDirectory(), encodingManager, false);
        // 0
        // 1
        // 2
        //  3
        //   4
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1, 0);
        na.setNode(1, 0.5, 0);
        na.setNode(2, 0, 0);
        na.setNode(3, -1, 1);
        na.setNode(4, -2, 2);

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
        final LevelGraph lg = createGraph(new RAMDirectory(), encodingManager, false);
        lg.getNodeAccess().ensureNode(4);
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

        LevelGraphStorage lg = (LevelGraphStorage) createGraph(new RAMDirectory(), encodingManager, false);
        NodeAccess na = lg.getNodeAccess();
        na.setNode(0, 1, 0);
        na.setNode(1, 0, 0);
        na.setNode(2, 0.5, 0.5);
        na.setNode(3, 0.5, 1);
        EdgeIteratorState iter1 = lg.edge(1, 0, 100, true);
        lg.edge(2, 3, 100, true);

        lg.setLevel(0, 11);
        lg.setLevel(1, 10);
        // disconnect higher 0 from lower 1
        lg.disconnect(lg.createEdgeExplorer(), iter1);

        lg.setLevel(2, 12);
        lg.setLevel(3, 13);
        // disconnect higher 3 from lower 2
        lg.disconnect(lg.createEdgeExplorer(), iter1);

        LocationIndexTree index = createIndex(lg, 100000);

        // very close to 2, but should match the edge 0--1
        TIntHashSet set = index.findNetworkEntries(0.51, 0.2, index.maxRegionSearch);
        assertEquals(0, index.findID(0.51, 0.2));
        assertEquals(1, index.findID(0.1, 0.1));
        assertEquals(2, index.findID(0.51, 0.51));
        assertEquals(3, index.findID(0.51, 1.1));
        TIntSet expectedSet = new TIntHashSet();
        expectedSet.add(0);
        expectedSet.add(2);
        assertEquals(expectedSet, set);
    }
}
