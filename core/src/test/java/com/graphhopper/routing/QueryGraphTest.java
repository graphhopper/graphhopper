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
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.QueryResult;
import static com.graphhopper.storage.index.QueryResult.Position.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import gnu.trove.map.TIntObjectMap;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Peter Karich
 */
public class QueryGraphTest
{
    private final EncodingManager encodingManager = new EncodingManager("CAR");
    private GraphStorage g;

    @Before
    public void setUp()
    {
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
    }

    @After
    public void tearDown()
    {
        g.close();
    }

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
        assertEquals(4, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(3, getPoints(queryGraph, 3, 1).getSize());

        queryGraph = new QueryGraph(g);
        res = createLocationResult(2, 1.7, iter, 1, PILLAR);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1.5, 1.5), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(4, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(3, getPoints(queryGraph, 3, 1).getSize());

        // snap to edge which has pillar nodes        
        queryGraph = new QueryGraph(g);
        res = createLocationResult(1.5, 2, iter, 0, EDGE);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1.300019, 1.899962), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(4, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(3, getPoints(queryGraph, 3, 1).getSize());

        // snap to edge which has no pillar nodes
        queryGraph = new QueryGraph(g);
        iter = expl.setBaseNode(2);
        iter.next();
        res = createLocationResult(0.5, 0.1, iter, 0, EDGE);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(0.5, 0), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(2, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(3, getPoints(queryGraph, 3, 2).getSize());
    }

    @Test
    public void testFillVirtualEdges()
    {
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
        initGraph(g);
        g.setNode(3, 0, 1);
        g.edge(1, 3);

        final int baseNode = 1;
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(baseNode);
        iter.next();
        QueryResult res1 = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryGraph queryGraph = new QueryGraph(g)
        {

            @Override
            void fillVirtualEdges( TIntObjectMap<QueryGraph.VirtualEdgeIterator> node2Edge, int towerNode, EdgeExplorer mainExpl )
            {
                super.fillVirtualEdges(node2Edge, towerNode, mainExpl);
                // ignore nodes should include baseNode == 1
                if (towerNode == 3)
                    assertEquals("[3->4]", node2Edge.get(towerNode).toString());
                else if (towerNode == 1)
                    assertEquals("[1->4, 1 1-0]", node2Edge.get(towerNode).toString());
                else
                    throw new IllegalStateException("not allowed " + towerNode);
            }
        };
        queryGraph.lookup(Arrays.asList(res1));
        EdgeIteratorState state = GHUtility.getEdge(queryGraph, 0, 1);
        assertEquals(4, state.fetchWayGeometry(3).size());
        
        // fetch virtual edge and check way geometry
        state = GHUtility.getEdge(queryGraph, 4, 3);
        assertEquals(2, state.fetchWayGeometry(3).size());
    }

    @Test
    public void testMultipleVirtualNodes()
    {
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
        initGraph(g);

        // snap to edge which has pillar nodes        
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(1);
        iter.next();
        QueryResult res1 = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryGraph queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res1));
        assertEquals(new GHPoint(1.5, 1.5), res1.getSnappedPoint());
        assertEquals(3, res1.getClosestNode());
        assertEquals(4, getPoints(queryGraph, 0, 3).getSize());
        PointList pl = getPoints(queryGraph, 3, 1);
        assertEquals(3, pl.getSize());
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

        assertEquals(4, getPoints(queryGraph, 3, 0).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 4).getSize());
        assertEquals(3, getPoints(queryGraph, 4, 1).getSize());
        assertNull(GHUtility.getEdge(queryGraph, 4, 0));
        assertNull(GHUtility.getEdge(queryGraph, 3, 1));
    }

    @Test
    public void testOneWay()
    {
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
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
        assertEquals(3, getPoints(queryGraph, 3, 1).getSize());
        assertNull(GHUtility.getEdge(queryGraph, 3, 0));
        assertNull(GHUtility.getEdge(queryGraph, 2, 1));
    }

    @Test
    public void testVirtEdges()
    {
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
        initGraph(g);

        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(0);
        iter.next();

        QueryGraph.VirtualEdgeIterator vi = new QueryGraph.VirtualEdgeIterator(2);
        vi.add(iter.detach(false));

        assertTrue(vi.next());
    }

    @Test
    public void testLoopStreet_Issue151()
    {
        // do query at x should result in ignoring only the bottom edge 1-3 not the upper one => getNeighbors are 0, 5, 3 and not only 0, 5
        //
        // 0--1--3--4
        //    |  |
        //    x---
        //
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
        g.edge(0, 1, 10, true);
        g.edge(1, 3, 10, true);
        g.edge(3, 4, 10, true);
        EdgeIteratorState edge = g.edge(1, 3, 20, true).setWayGeometry(Helper.createPointList(-0.001, 0.001, -0.001, 0.002));
        AbstractRoutingAlgorithmTester.updateDistancesFor(g, 0, 0, 0);
        AbstractRoutingAlgorithmTester.updateDistancesFor(g, 1, 0, 0.001);
        AbstractRoutingAlgorithmTester.updateDistancesFor(g, 3, 0, 0.002);
        AbstractRoutingAlgorithmTester.updateDistancesFor(g, 4, 0, 0.003);

        QueryResult qr = new QueryResult(-0.0005, 0.001);
        qr.setClosestEdge(edge);
        qr.setWayIndex(0);
        qr.calcSnappedPoint(new DistanceCalc2D());

        QueryGraph qg = new QueryGraph(g);
        qg.lookup(Arrays.asList(qr));
        EdgeExplorer ee = qg.createEdgeExplorer();

        assertEquals(GHUtility.asSet(0, 5, 3), GHUtility.getNeighbors(ee.setBaseNode(1)));
    }

    @Test
    public void testOneWayLoop_Issue162()
    {
        // do query at x, where edge is oneway
        //
        // |\
        // | x
        // 0<-\
        // |
        // 1
        FlagEncoder carEncoder = encodingManager.getSingle();
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
        g.setNode(0, 0, 0);
        g.setNode(1, 0, -0.001);
        g.edge(0, 1, 10, true);
        // in the case of identical nodes the wayGeometry defines the direction!
        EdgeIteratorState edge = g.edge(0, 0).
                setDistance(100).
                setFlags(carEncoder.setProperties(20, true, false)).
                setWayGeometry(Helper.createPointList(0.001, 0, 0, 0.001));

        QueryResult qr = new QueryResult(0.0011, 0.0009);
        qr.setClosestEdge(edge);
        qr.setWayIndex(1);
        qr.calcSnappedPoint(new DistanceCalc2D());

        QueryGraph qg = new QueryGraph(g);
        qg.lookup(Arrays.asList(qr));
        EdgeExplorer ee = qg.createEdgeExplorer();
        assertTrue(qr.getClosestNode() > 1);
        assertEquals(2, GHUtility.count(ee.setBaseNode(qr.getClosestNode())));
        EdgeIterator iter = ee.setBaseNode(qr.getClosestNode());
        iter.next();
        assertFalse(iter.toString(), carEncoder.isBackward(iter.getFlags()));
        assertTrue(iter.toString(), carEncoder.isForward(iter.getFlags()));

        iter.next();
        assertTrue(iter.toString(), carEncoder.isBackward(iter.getFlags()));
        assertFalse(iter.toString(), carEncoder.isForward(iter.getFlags()));        
    }

    @Test
    public void testEdgesShareOneNode()
    {
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager).create(100);
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
            EdgeIteratorState edge, int wayIndex, QueryResult.Position pos )
    {
        if (edge == null)
            throw new IllegalStateException("Specify edge != null");
        QueryResult tmp = new QueryResult(lat, lon);
        tmp.setClosestEdge(edge);
        tmp.setWayIndex(wayIndex);
        tmp.setSnappedPosition(pos);
        tmp.calcSnappedPoint(new DistanceCalcEarth());
        return tmp;
    }
}
