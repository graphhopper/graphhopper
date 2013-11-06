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
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipExplorer;
import com.graphhopper.util.GHUtility;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class LevelGraphStorageTest extends GraphStorageTest
{
    @Override
    protected LevelGraph createGraph()
    {
        return (LevelGraph) super.createGraph();
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
        GraphStorage g = createGraphStorage(new RAMDirectory(defaultGraph, true));
        g.flush();
        g.close();

        g = new GraphBuilder(encodingManager).setLocation(defaultGraph).setMmap(false).setStore(true).create();
        try
        {
            g.loadExisting();
            assertTrue(false);
        } catch (Exception ex)
        {
        }

        g = newGraph(new RAMDirectory(defaultGraph, true));
        assertTrue(g.loadExisting());
    }

    @Test
    public void testPriosWhileDeleting()
    {
        LevelGraph g = createGraph();
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
        EdgeSkipExplorer tmpIter = g.edge(3, 4, 40, true);
        assertEquals(EdgeIterator.NO_EDGE, tmpIter.getSkippedEdge1());
        assertEquals(EdgeIterator.NO_EDGE, tmpIter.getSkippedEdge2());

        // shortcut
        g.edge(0, 4, 40, true);
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
        g.edge(1, 2, 10, true);
        g.edge(1, 0, 20, false);
        g.edge(3, 1, 30, false);
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(1);
        iter.next();
        assertEquals(2, iter.getAdjNode());        
        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(2)));
        g.disconnect(g.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(2)));

        // even directed ways change!
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertEquals(1, GHUtility.count(carInExplorer.setBaseNode(0)));
        g.disconnect(g.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(carInExplorer.setBaseNode(0)));

        iter.next();
        assertEquals(3, iter.getAdjNode());
        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(3)));
        g.disconnect(g.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(3)));
    }
}
