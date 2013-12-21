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
package com.graphhopper.routing;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.QueryResult;
import static com.graphhopper.storage.index.QueryResult.Position.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class QueryGraphTest
{
    void initGraph( Graph g )
    {
        //
        //  /*-*\
        // 0     1
        // |
        // 2
        g.setNode(0, 1, 0);
        g.setNode(1, 1, 2.5);
        g.setNode(2, 0, 0);
        g.edge(0, 2, 10, true);
        g.edge(0, 1, 10, true).setWayGeometry(Helper.createPointList(1.5, 1, 1.5, 1.5));
    }

    @Test
    public void testOneVirtualNode()
    {
        EncodingManager encodingManager = new EncodingManager("CAR");
        Graph g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
        initGraph(g);
        EdgeExplorer expl = g.createEdgeExplorer();

        // snap directly to tower node => pointList could get of size 1?!?      
        // a)
        EdgeIterator iter = expl.setBaseNode(2);
        iter.next();

        QueryGraph queryGraph = new QueryGraph(g);
        QueryResult res = createLocationResult(1, -1, iter, 0, TOWER);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(0, 0), res.getSnappedPoint());

        // b)
        res = createLocationResult(1, -1, iter, 1, TOWER);
        queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1, 0), res.getSnappedPoint());
        // c)
        iter = expl.setBaseNode(1);
        iter.next();
        res = createLocationResult(1.2, 2.7, iter, 0, TOWER);
        queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1, 2.5), res.getSnappedPoint());

        // node number stays
        assertEquals(3, queryGraph.getNodes());

        // snap directly to pillar node
        queryGraph = new QueryGraph(g);
        iter = expl.setBaseNode(1);
        iter.next();
        res = createLocationResult(2, 1.5, iter, 1, PILLAR);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1.5, 1.5), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());

        queryGraph = new QueryGraph(g);
        res = createLocationResult(2, 1.7, iter, 1, PILLAR);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1.5, 1.5), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());

        // snap to edge which has pillar nodes        
        queryGraph = new QueryGraph(g);
        res = createLocationResult(1.5, 2, iter, 0, EDGE);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1.300019, 1.899962), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(4, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());

        // snap to edge which has no pillar nodes
        queryGraph = new QueryGraph(g);
        iter = expl.setBaseNode(2);
        iter.next();
        res = createLocationResult(0.5, 0.1, iter, 0, EDGE);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(0.5, 0), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(2, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 2).getSize());
    }

    @Test
    public void testMultipleVirtualNodes()
    {
        EncodingManager encodingManager = new EncodingManager("CAR");
        Graph g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
        initGraph(g);

        // snap to edge which has pillar nodes        
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(1);
        iter.next();
        QueryResult res1 = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryGraph queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res1));
        assertEquals(new GHPoint(1.5, 1.5), res1.getSnappedPoint());
        assertEquals(3, res1.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).getSize());
        PointList pl = getPoints(queryGraph, 3, 1);
        assertEquals(2, pl.getSize());        
        assertEquals(new GHPoint(1.5, 1.5), pl.toGHPoint(0));
        assertEquals(new GHPoint(1, 2.5), pl.toGHPoint(1));

        EdgeIteratorState edge = GHUtility.getEdge(queryGraph, 3, 1);
        assertNotNull(queryGraph.getEdgeProps(edge.getEdge(), 3));
        assertNotNull(queryGraph.getEdgeProps(edge.getEdge(), 1));

        edge = GHUtility.getEdge(queryGraph, 3, 0);
        assertNotNull(queryGraph.getEdgeProps(edge.getEdge(), 3));
        assertNotNull(queryGraph.getEdgeProps(edge.getEdge(), 0));

        // snap again => new virtual node on same edge!
        iter = g.createEdgeExplorer().setBaseNode(1);
        iter.next();
        res1 = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryResult res2 = createLocationResult(1.5, 2, iter, 0, EDGE);
        queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res1, res2));
        assertEquals(4, res2.getClosestNode());
        assertEquals(new GHPoint(1.300019, 1.899962), res2.getSnappedPoint());
        assertEquals(3, res1.getClosestNode());
        assertEquals(new GHPoint(1.5, 1.5), res1.getSnappedPoint());

        assertEquals(3, getPoints(queryGraph, 3, 0).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 4).getSize());
        assertEquals(2, getPoints(queryGraph, 4, 1).getSize());
        assertNull(GHUtility.getEdge(queryGraph, 4, 0));
        assertNull(GHUtility.getEdge(queryGraph, 3, 1));
    }

    @Test
    public void testOneWay()
    {
        EncodingManager encodingManager = new EncodingManager("CAR");
        Graph g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
        g.setNode(0, 0, 0);
        g.setNode(1, 0, 1);
        g.edge(0, 1, 10, false);

        EdgeIteratorState edge = GHUtility.getEdge(g, 0, 1);
        QueryResult res1 = createLocationResult(0.1, 0.1, edge, 0, EDGE);
        QueryResult res2 = createLocationResult(0.1, 0.9, edge, 0, EDGE);
        QueryGraph queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res2, res1));
        assertEquals(2, res1.getClosestNode());
        assertEquals(new GHPoint(0, 0.1), res1.getSnappedPoint());
        assertEquals(3, res2.getClosestNode());
        assertEquals(new GHPoint(0, 0.9), res2.getSnappedPoint());

        assertEquals(2, getPoints(queryGraph, 0, 2).getSize());
        assertEquals(2, getPoints(queryGraph, 2, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());
        assertNull(GHUtility.getEdge(queryGraph, 3, 0));
        assertNull(GHUtility.getEdge(queryGraph, 2, 1));
    }

    @Test
    public void testVirtEdges()
    {
        EncodingManager encodingManager = new EncodingManager("CAR");
        Graph g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
        initGraph(g);

        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(0);
        iter.next();

        QueryGraph.VirtualEdgeIterator vi = new QueryGraph.VirtualEdgeIterator(2);
        vi.add(iter.detach());

        assertTrue(vi.next());
    }

    @Test
    public void testEdgesShareOneNode()
    {
        EncodingManager encodingManager = new EncodingManager("CAR");
        Graph g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
        initGraph(g);

        EdgeIteratorState iter = GHUtility.getEdge(g, 0, 2);
        QueryResult res1 = createLocationResult(0.5, 0, iter, 0, EDGE);
        iter = GHUtility.getEdge(g, 1, 0);
        QueryResult res2 = createLocationResult(1.5, 2, iter, 0, EDGE);
        QueryGraph queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res1, res2));
        assertEquals(new GHPoint(0.5, 0), res1.getSnappedPoint());
        assertEquals(new GHPoint(1.300019, 1.899962), res2.getSnappedPoint());
        assertNotNull(GHUtility.getEdge(queryGraph, 0, 4));
        assertNotNull(GHUtility.getEdge(queryGraph, 0, 3));
    }

    PointList getPoints( Graph g, int base, int adj )
    {
        EdgeIteratorState edge = GHUtility.getEdge(g, base, adj);
        if (edge == null)
            throw new IllegalStateException("edge " + base + "-" + adj + " not found");
        return edge.fetchWayGeometry(3);
    }

    public QueryResult createLocationResult( double lat, double lon,
            EdgeIteratorState edge, int index, QueryResult.Position pos )
    {
        if (edge == null)
            throw new IllegalStateException("Specify edge != null");
        QueryResult tmp = new QueryResult(lat, lon);
        tmp.setClosestEdge(edge);
        tmp.setWayIndex(index);
        tmp.setSnappedPosition(pos);
        tmp.calcSnappedPoint(new DistanceCalcEarth());
        return tmp;
    }
}
