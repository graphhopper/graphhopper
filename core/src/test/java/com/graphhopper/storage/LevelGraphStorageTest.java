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
package com.graphhopper.storage;

import com.graphhopper.routing.util.LevelEdgeFilter;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterState;
import com.graphhopper.util.GHUtility;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Peter Karich
 */
public class LevelGraphStorageTest extends GraphHopperStorageTest
{
    @Override
    protected LevelGraphStorage createGraph()
    {
        return (LevelGraphStorage) super.createGraph();
    }

    @Override
    protected LevelGraphStorage createGraphStorage( Directory dir )
    {
        return (LevelGraphStorage) super.createGraphStorage(dir);
    }

    @Override
    public GraphStorage newGraph( Directory dir )
    {
        return new LevelGraphStorage(dir, encodingManager);
    }

    @Test
    public void testCannotBeLoadedViaDifferentClass()
    {
        GraphStorage g = createGraphStorage(new RAMDirectory(defaultGraphLoc, true));
        g.flush();
        g.close();

        g = new GraphBuilder(encodingManager).setLocation(defaultGraphLoc).setMmap(false).setStore(true).create();
        try
        {
            g.loadExisting();
            assertTrue(false);
        } catch (Exception ex)
        {
        }

        g = newGraph(new RAMDirectory(defaultGraphLoc, true));
        assertTrue(g.loadExisting());
    }

    @Test
    public void testPriosWhileDeleting()
    {
        LevelGraphStorage g = createGraph();
        for (int i = 0; i < 20; i++)
        {
            g.setLevel(i, i);
        }
        g.markNodeRemoved(10);
        g.optimize();
        assertEquals(9, g.getLevel(9));
        assertNotSame(10, g.getLevel(10));
        assertEquals(19, g.getNodes());
    }

    @Test
    public void testPrios()
    {
        LevelGraph g = createGraph();
        assertEquals(0, g.getLevel(10));

        g.setLevel(10, 100);
        assertEquals(100, g.getLevel(10));

        g.setLevel(30, 100);
        assertEquals(100, g.getLevel(30));
    }

    @Test
    public void testEdgeFilter()
    {
        LevelGraph g = createGraph();
        g.edge(0, 1, 10, true);
        g.edge(0, 2, 20, true);
        g.edge(2, 3, 30, true);
        EdgeSkipIterState tmpIter = g.shortcut(3, 4);
        tmpIter.setDistance(40).setFlags(carEncoder.setAccess(0, true, true));
        assertEquals(EdgeIterator.NO_EDGE, tmpIter.getSkippedEdge1());
        assertEquals(EdgeIterator.NO_EDGE, tmpIter.getSkippedEdge2());

        g.shortcut(0, 4).setDistance(40).setFlags(carEncoder.setAccess(0, true, true));
        g.setLevel(0, 1);
        g.setLevel(4, 1);

        EdgeIterator iter = g.createEdgeExplorer(new LevelEdgeFilter(g)).setBaseNode(0);
        assertEquals(1, GHUtility.count(iter));
        iter = g.createEdgeExplorer().setBaseNode(2);
        assertEquals(2, GHUtility.count(iter));
    }

    @Test
    public void testDisconnectEdge()
    {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        // only remove edges
        long flags = carEncoder.setProperties(60, true, true);
        long flags2 = carEncoder.setProperties(60, true, false);
        g.edge(4, 1, 30, true);
        EdgeSkipIterState tmp = g.shortcut(1, 2);
        tmp.setDistance(10).setFlags(flags);
        tmp.setSkippedEdges(10, 11);
        tmp = g.shortcut(1, 0);
        tmp.setDistance(20).setFlags(flags2);
        tmp.setSkippedEdges(12, 13);
        tmp = g.shortcut(3, 1);
        tmp.setDistance(30).setFlags(flags2);
        tmp.setSkippedEdges(14, 15);
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(1);
        iter.next();
        assertEquals(3, iter.getAdjNode());
        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(3)));
        g.disconnect(g.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(3)));

        // even directed ways change!
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertEquals(1, GHUtility.count(carInExplorer.setBaseNode(0)));
        g.disconnect(g.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(carInExplorer.setBaseNode(0)));

        iter.next();
        assertEquals(2, iter.getAdjNode());
        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(2)));
        g.disconnect(g.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(2)));
    }

    @Test
    public void testGetWeight()
    {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        assertFalse(g.edge(0, 1).isShortcut());
        assertFalse(g.edge(1, 2).isShortcut());

        // only remove edges
        long flags = carEncoder.setProperties(10, true, true);
        EdgeSkipIterState sc1 = g.shortcut(0, 1);
        assertTrue(sc1.isShortcut());
        sc1.setWeight(2.001);
        assertEquals(2.001, sc1.getWeight(), 1e-3);
        sc1.setWeight(100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        sc1.setWeight(Double.MAX_VALUE);
        assertTrue(Double.isInfinite(sc1.getWeight()));

        sc1.setFlags(flags);
        sc1.setWeight(100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        assertTrue(carEncoder.isBackward(sc1.getFlags()));
        assertTrue(carEncoder.isForward(sc1.getFlags()));

        flags = carEncoder.setProperties(10, false, true);
        sc1.setFlags(flags);
        sc1.setWeight(100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        assertTrue(carEncoder.isBackward(sc1.getFlags()));
        assertFalse(carEncoder.isForward(sc1.getFlags()));
    }
}
