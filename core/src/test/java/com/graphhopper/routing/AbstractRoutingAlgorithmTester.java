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

import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import gnu.trove.list.TIntList;
import java.util.Random;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public abstract class AbstractRoutingAlgorithmTester
{
    // problem is: matrix graph is expensive to create to cache it in a static variable
    private static Graph matrixGraph;
    protected static final EncodingManager encodingManager = new EncodingManager("CAR,FOOT");
    protected FlagEncoder carEncoder;
    protected FlagEncoder footEncoder;
    protected AlgorithmOptions defaultOpts;

    @Before
    public void setUp()
    {
        carEncoder = (CarFlagEncoder) encodingManager.getEncoder("CAR");
        footEncoder = (FootFlagEncoder) encodingManager.getEncoder("FOOT");
        defaultOpts = AlgorithmOptions.start().flagEncoder(carEncoder).
                weighting(new ShortestWeighting()).build();
    }

    protected Graph createGraph( EncodingManager em, boolean is3D )
    {
        return new GraphBuilder(em).set3D(is3D).create();
    }

    protected Graph createGraph( boolean is3D )
    {
        return createGraph(encodingManager, is3D);
    }

    public RoutingAlgorithm createAlgo( Graph g )
    {
        return createAlgo(g, defaultOpts);
    }

    public RoutingAlgorithm createAlgo( Graph g, AlgorithmOptions opts )
    {
        return createFactory(g, opts).createAlgo(g, opts);
    }

    public abstract RoutingAlgorithmFactory createFactory( Graph g, AlgorithmOptions opts );

    @Test
    public void testCalcShortestPath()
    {
        Graph graph = createTestGraph();
        RoutingAlgorithm algo = createAlgo(graph);
        Path p = algo.calcPath(0, 7);
        assertEquals(p.toString(), Helper.createTList(0, 4, 5, 7), p.calcNodes());
        assertEquals(p.toString(), 62.1, p.getDistance(), .1);
    }

    @Test
    public void testWeightLimit()
    {
        Graph graph = createTestGraph();
        RoutingAlgorithm algo = createAlgo(graph);
        algo.setWeightLimit(10);
        Path p = algo.calcPath(0, 7);
        assertTrue(algo.getVisitedNodes() < 7);
        assertFalse(p.isFound());
        assertEquals(p.toString(), Helper.createTList(), p.calcNodes());
    }

    @Test
    public void testWeightLimit_issue380()
    {
        Graph graph = createGraph(false);
        initGraphWeightLimit(graph);
        RoutingAlgorithm algo = createAlgo(graph);
        algo.setWeightLimit(3);
        Path p = algo.calcPath(0, 4);
        assertTrue(p.isFound());
        assertEquals(3.0, p.getWeight(), 1e-6);

        algo = createAlgo(graph);
        algo.setWeightLimit(3);
        p = algo.calcPath(0, 3);
        assertTrue(p.isFound());
        assertEquals(3.0, p.getWeight(), 1e-6);
    }

    // see calc-fastest-graph.svg
    @Test
    public void testCalcFastestPath()
    {
        Graph graphShortest = createGraph(false);
        initDirectedAndDiffSpeed(graphShortest, carEncoder);
        Path p1 = createAlgo(graphShortest, defaultOpts).calcPath(0, 3);
        assertEquals(Helper.createTList(0, 1, 5, 2, 3), p1.calcNodes());
        assertEquals(p1.toString(), 402.293, p1.getDistance(), 1e-6);
        assertEquals(p1.toString(), 144823, p1.getTime());

        Graph graphFastest = createGraph(false);
        initDirectedAndDiffSpeed(graphFastest, carEncoder);
        Path p2 = createAlgo(graphFastest,
                AlgorithmOptions.start().flagEncoder(carEncoder).weighting(new FastestWeighting(carEncoder)).build()).
                calcPath(0, 3);
        assertEquals(Helper.createTList(0, 4, 6, 7, 5, 3), p2.calcNodes());
        assertEquals(p2.toString(), 1261.714, p2.getDistance(), 1e-6);
        assertEquals(p2.toString(), 111437, p2.getTime());
    }

    // 0-1-2-3
    // |/|/ /|
    // 4-5-- |
    // |/ \--7
    // 6----/
    protected void initDirectedAndDiffSpeed( Graph graph, FlagEncoder enc )
    {
        graph.edge(0, 1).setFlags(enc.setProperties(10, true, false));
        graph.edge(0, 4).setFlags(enc.setProperties(100, true, false));

        graph.edge(1, 4).setFlags(enc.setProperties(10, true, true));
        graph.edge(1, 5).setFlags(enc.setProperties(10, true, true));
        EdgeIteratorState edge12 = graph.edge(1, 2).setFlags(enc.setProperties(10, true, true));

        graph.edge(5, 2).setFlags(enc.setProperties(10, true, false));
        graph.edge(2, 3).setFlags(enc.setProperties(10, true, false));

        EdgeIteratorState edge53 = graph.edge(5, 3).setFlags(enc.setProperties(20, true, false));
        graph.edge(3, 7).setFlags(enc.setProperties(10, true, false));

        graph.edge(4, 6).setFlags(enc.setProperties(100, true, false));
        graph.edge(5, 4).setFlags(enc.setProperties(10, true, false));

        graph.edge(5, 6).setFlags(enc.setProperties(10, true, false));
        graph.edge(7, 5).setFlags(enc.setProperties(100, true, false));

        graph.edge(6, 7).setFlags(enc.setProperties(100, true, true));

        updateDistancesFor(graph, 0, 0.002, 0);
        updateDistancesFor(graph, 1, 0.002, 0.001);
        updateDistancesFor(graph, 2, 0.002, 0.002);
        updateDistancesFor(graph, 3, 0.002, 0.003);
        updateDistancesFor(graph, 4, 0.0015, 0);
        updateDistancesFor(graph, 5, 0.0015, 0.001);
        updateDistancesFor(graph, 6, 0, 0);
        updateDistancesFor(graph, 7, 0.001, 0.003);

        edge12.setDistance(edge12.getDistance() * 2);
        edge53.setDistance(edge53.getDistance() * 2);
    }

    @Test
    public void testCalcFootPath()
    {
        Graph graphShortest = createGraph(false);
        initFootVsCar(graphShortest);
        Path p1 = createAlgo(graphShortest, AlgorithmOptions.start().flagEncoder(footEncoder).
                weighting(new ShortestWeighting()).build()).
                calcPath(0, 7);
        assertEquals(p1.toString(), 17000, p1.getDistance(), 1e-6);
        assertEquals(p1.toString(), 12240 * 1000, p1.getTime());
        assertEquals(Helper.createTList(0, 4, 5, 7), p1.calcNodes());
    }

    protected void initFootVsCar( Graph graph )
    {
        graph.edge(0, 1).setDistance(7000).setFlags(footEncoder.setProperties(5, true, true) | carEncoder.setProperties(10, true, false));
        graph.edge(0, 4).setDistance(5000).setFlags(footEncoder.setProperties(5, true, true) | carEncoder.setProperties(20, true, false));

        graph.edge(1, 4).setDistance(7000).setFlags(carEncoder.setProperties(10, true, true));
        graph.edge(1, 5).setDistance(7000).setFlags(carEncoder.setProperties(10, true, true));
        graph.edge(1, 2).setDistance(20000).setFlags(footEncoder.setProperties(5, true, true) | carEncoder.setProperties(10, true, true));

        graph.edge(5, 2).setDistance(5000).setFlags(carEncoder.setProperties(10, true, false));
        graph.edge(2, 3).setDistance(5000).setFlags(footEncoder.setProperties(5, true, true) | carEncoder.setProperties(10, true, false));

        graph.edge(5, 3).setDistance(11000).setFlags(carEncoder.setProperties(20, true, false));
        graph.edge(3, 7).setDistance(7000).setFlags(footEncoder.setProperties(5, true, true) | carEncoder.setProperties(10, true, false));

        graph.edge(4, 6).setDistance(5000).setFlags(carEncoder.setProperties(20, true, false));
        graph.edge(5, 4).setDistance(7000).setFlags(footEncoder.setProperties(5, true, true) | carEncoder.setProperties(10, true, false));

        graph.edge(5, 6).setDistance(7000).setFlags(carEncoder.setProperties(10, true, false));
        graph.edge(7, 5).setDistance(5000).setFlags(footEncoder.setProperties(5, true, true) | carEncoder.setProperties(20, true, false));

        graph.edge(6, 7).setDistance(5000).setFlags(carEncoder.setProperties(20, true, true));
    }

    // see test-graph.svg !
    protected Graph createTestGraph()
    {
        Graph graph = createGraph(false);

        graph.edge(0, 1, 7, true);
        graph.edge(0, 4, 6, true);

        graph.edge(1, 4, 2, true);
        graph.edge(1, 5, 8, true);
        graph.edge(1, 2, 2, true);

        graph.edge(2, 5, 5, true);
        graph.edge(2, 3, 2, true);

        graph.edge(3, 5, 2, true);
        graph.edge(3, 7, 10, true);

        graph.edge(4, 6, 4, true);
        graph.edge(4, 5, 7, true);

        graph.edge(5, 6, 2, true);
        graph.edge(5, 7, 1, true);

        EdgeIteratorState edge6_7 = graph.edge(6, 7, 5, true);

        updateDistancesFor(graph, 0, 0.0010, 0.00001);
        updateDistancesFor(graph, 1, 0.0008, 0.0000);
        updateDistancesFor(graph, 2, 0.0005, 0.0001);
        updateDistancesFor(graph, 3, 0.0006, 0.0002);
        updateDistancesFor(graph, 4, 0.0009, 0.0001);
        updateDistancesFor(graph, 5, 0.0007, 0.0001);
        updateDistancesFor(graph, 6, 0.0009, 0.0002);
        updateDistancesFor(graph, 7, 0.0008, 0.0003);

        edge6_7.setDistance(5 * edge6_7.getDistance());
        return graph;
    }

    @Test
    public void testNoPathFound()
    {
        Graph graph = createGraph(false);
        assertFalse(createAlgo(graph).calcPath(0, 1).isFound());

        // two disconnected areas
        graph.edge(0, 1, 7, true);

        graph.edge(5, 6, 2, true);
        graph.edge(5, 7, 1, true);
        graph.edge(5, 8, 1, true);
        graph.edge(7, 8, 1, true);
        RoutingAlgorithm algo = createAlgo(graph);
        assertFalse(algo.calcPath(0, 5).isFound());
        // assertEquals(3, algo.getVisitedNodes());

        // disconnected as directed graph
        graph = createGraph(false);
        graph.edge(0, 1, 1, false);
        graph.edge(0, 2, 1, true);
        assertFalse(createAlgo(graph).calcPath(1, 2).isFound());
    }

    @Test
    public void testWikipediaShortestPath()
    {
        Graph graph = createWikipediaTestGraph();
        Path p = createAlgo(graph).calcPath(0, 4);
        assertEquals(p.toString(), 20, p.getDistance(), 1e-4);
        assertEquals(p.toString(), 4, p.calcNodes().size());
    }

    @Test
    public void testCalcIf1EdgeAway()
    {
        Graph graph = createTestGraph();
        Path p = createAlgo(graph).calcPath(1, 2);
        assertEquals(Helper.createTList(1, 2), p.calcNodes());
        assertEquals(p.toString(), 35.1, p.getDistance(), .1);
    }

    // see wikipedia-graph.svg !
    protected Graph createWikipediaTestGraph()
    {
        Graph graph = createGraph(false);
        graph.edge(0, 1, 7, true);
        graph.edge(0, 2, 9, true);
        graph.edge(0, 5, 14, true);
        graph.edge(1, 2, 10, true);
        graph.edge(1, 3, 15, true);
        graph.edge(2, 5, 2, true);
        graph.edge(2, 3, 11, true);
        graph.edge(3, 4, 6, true);
        graph.edge(4, 5, 9, true);
        return graph;
    }

    // 0-1-2-3-4
    // |     / |
    // |    8  |
    // \   /   |
    //  7-6----5
    public static Graph initBiGraph( Graph graph )
    {
        // distance will be overwritten in second step as we need to calculate it from lat,lon
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 1, true);
        graph.edge(5, 6, 1, true);
        graph.edge(6, 7, 1, true);
        graph.edge(7, 0, 1, true);
        graph.edge(3, 8, 1, true);
        graph.edge(8, 6, 1, true);

        // we need lat,lon for edge precise queries because the distances of snapped point 
        // to adjacent nodes is calculated from lat,lon of the necessary points
        updateDistancesFor(graph, 0, 0.001, 0);
        updateDistancesFor(graph, 1, 0.100, 0.0005);
        updateDistancesFor(graph, 2, 0.010, 0.0010);
        updateDistancesFor(graph, 3, 0.001, 0.0011);
        updateDistancesFor(graph, 4, 0.001, 0.00111);

        updateDistancesFor(graph, 8, 0.0005, 0.0011);

        updateDistancesFor(graph, 7, 0, 0);
        updateDistancesFor(graph, 6, 0, 0.001);
        updateDistancesFor(graph, 5, 0, 0.004);
        return graph;
    }

    private static final DistanceCalc distCalc = new DistanceCalcEarth();

    public static void updateDistancesFor( Graph g, int node, double lat, double lon )
    {
        NodeAccess na = g.getNodeAccess();
        na.setNode(node, lat, lon);
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(node);
        while (iter.next())
        {
            int adj = iter.getAdjNode();
            double adjLat = na.getLatitude(adj);
            double adjLon = na.getLongitude(adj);
            iter.setDistance(distCalc.calcDist(lat, lon, adjLat, adjLon));
            // System.out.println(node + "->" + adj + ": " + iter.getDistance());
        }
    }

    @Test
    public void testBidirectional()
    {
        Graph graph = createGraph(false);
        initBiGraph(graph);

        // PrepareTowerNodesShortcutsTest.printEdges((LevelGraph) graph);
        Path p = createAlgo(graph).calcPath(0, 4);
        // PrepareTowerNodesShortcutsTest.printEdges((LevelGraph) graph);
        assertEquals(p.toString(), Helper.createTList(0, 7, 6, 8, 3, 4), p.calcNodes());
        assertEquals(p.toString(), 335.77, p.getDistance(), 1e-2);

        p = createAlgo(graph).calcPath(1, 2);
        // the other way around is even larger as 0-1 is already 11008.452
        assertEquals(p.toString(), Helper.createTList(1, 2), p.calcNodes());
        assertEquals(p.toString(), 10007.679, p.getDistance(), 1e-4);
    }

    // 1-2-3-4-5
    // |     / |
    // |    9  |
    // \   /   /
    //  8-7-6-/
    @Test
    public void testBidirectional2()
    {
        Graph graph = createGraph(false);

        graph.edge(0, 1, 100, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 20, true);
        graph.edge(5, 6, 10, true);
        graph.edge(6, 7, 5, true);
        graph.edge(7, 0, 5, true);
        graph.edge(3, 8, 20, true);
        graph.edge(8, 6, 20, true);

        Path p = createAlgo(graph).calcPath(0, 4);
        assertEquals(p.toString(), 40, p.getDistance(), 1e-4);
        assertEquals(p.toString(), 5, p.calcNodes().size());
        assertEquals(Helper.createTList(0, 7, 6, 5, 4), p.calcNodes());
    }

    @Test
    public void testRekeyBugOfIntBinHeap()
    {
        // using Dijkstra + IntBinHeap then rekey loops endlessly
        Path p = createAlgo(getMatrixGraph()).calcPath(36, 91);
        assertEquals(12, p.calcNodes().size());

        TIntList list = p.calcNodes();
        if (!Helper.createTList(36, 46, 56, 66, 76, 86, 85, 84, 94, 93, 92, 91).equals(list)
                && !Helper.createTList(36, 46, 56, 66, 76, 86, 85, 84, 83, 82, 92, 91).equals(list))
        {
            assertTrue("wrong locations: " + list.toString(), false);
        }
        assertEquals(66f, p.getDistance(), 1e-3);
    }

    @Test
    public void testBug1()
    {
        Path p = createAlgo(getMatrixGraph()).calcPath(34, 36);
        assertEquals(Helper.createTList(34, 35, 36), p.calcNodes());
        assertEquals(3, p.calcNodes().size());
        assertEquals(17, p.getDistance(), 1e-5);
    }

    @Test
    public void testCorrectWeight()
    {
        Path p = createAlgo(getMatrixGraph()).calcPath(45, 72);
        assertEquals(Helper.createTList(45, 44, 54, 64, 74, 73, 72), p.calcNodes());
        assertEquals(38f, p.getDistance(), 1e-3);
    }

    @Test
    public void testCannotCalculateSP()
    {
        Graph graph = createGraph(false);
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);

        Path p = createAlgo(graph).calcPath(0, 2);
        assertEquals(p.toString(), 3, p.calcNodes().size());
    }

    @Test
    public void testDirectedGraphBug1()
    {
        Graph graph = createGraph(false);
        graph.edge(0, 1, 3, false);
        graph.edge(1, 2, 2.99, false);

        graph.edge(0, 3, 2, false);
        graph.edge(3, 4, 3, false);
        graph.edge(4, 2, 1, false);

        Path p = createAlgo(graph).calcPath(0, 2);
        assertEquals(Helper.createTList(0, 1, 2), p.calcNodes());
        assertEquals(p.toString(), 5.99, p.getDistance(), 1e-4);
        assertEquals(p.toString(), 3, p.calcNodes().size());
    }

    @Test
    public void testDirectedGraphBug2()
    {
        Graph graph = createGraph(false);
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);

        graph.edge(3, 1, 4, true);

        Path p = createAlgo(graph).calcPath(0, 3);
        assertEquals(Helper.createTList(0, 1, 2, 3), p.calcNodes());
    }

    // a-b-0-c-1
    // |   |  _/\
    // |  /  /  |
    // d-2--3-e-4
    @Test
    public void testWithCoordinates()
    {
        Graph graph = createGraph(false);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 2);
        na.setNode(1, 0, 3.5);
        na.setNode(2, 1, 1);
        na.setNode(3, 1.5, 2.5);
        na.setNode(4, 0.5, 4.5);

        graph.edge(0, 1, 2, true).setWayGeometry(Helper.createPointList(0, 3));
        graph.edge(2, 3, 2, true);
        graph.edge(3, 4, 2, true).setWayGeometry(Helper.createPointList(1, 3.5));

        graph.edge(0, 2, 0.8, true).setWayGeometry(Helper.createPointList(0, 1.6, 0, 0, 1, 0));
        graph.edge(0, 2, 1.2, true);
        graph.edge(1, 3, 1.3, true);
        graph.edge(1, 4, 1, true);

        AlgorithmOptions opts = new AlgorithmOptions(AlgorithmOptions.DIJKSTRA_BI, carEncoder, new ShortestWeighting());
        RoutingAlgorithmFactory prepare = createFactory(graph, opts);
        Path p = prepare.createAlgo(graph, opts).calcPath(4, 0);
        assertEquals(Helper.createTList(4, 1, 0), p.calcNodes());
        assertEquals(Helper.createPointList(0.5, 4.5, 0, 3.5, 0, 3, 0, 2), p.calcPoints());
        assertEquals(291110, p.calcPoints().calcDistance(new DistanceCalcEarth()), 1);

        // PrepareTowerNodesShortcutsTest.printEdges((LevelGraph) graph);
        p = prepare.createAlgo(graph, opts).calcPath(2, 1);
        // System.out.println(p.toDetailsString());
        assertEquals(Helper.createTList(2, 0, 1), p.calcNodes());
        assertEquals(Helper.createPointList(1, 1, 1, 0, 0, 0, 0, 1.6, 0, 2, 0, 3, 0, 3.5), p.calcPoints());
        assertEquals(611555, p.calcPoints().calcDistance(new DistanceCalcEarth()), 1);
    }

    @Test
    public void testCalcIfEmptyWay()
    {
        Graph graph = createTestGraph();
        Path p = createAlgo(graph).calcPath(0, 0);
        assertEquals(p.calcNodes().toString(), 1, p.calcNodes().size());
        assertEquals(p.toString(), 0, p.getDistance(), 1e-4);
    }

    @Test
    public void testViaEdges_FromEqualsTo()
    {
        Graph graph = createTestGraph();
        // identical tower nodes
        Path p = calcPathViaQuery(graph, 0.001, 0.000, 0.001, 0.000);
        assertTrue(p.isFound());
        assertEquals(Helper.createTList(0), p.calcNodes());
        // assertEquals(1, p.calcPoints().size());
        assertEquals(p.toString(), 0, p.getDistance(), 1e-4);

        // identical query points on edge
        p = calcPath(graph, 0, 1, 0, 1);
        assertTrue(p.isFound());
        assertEquals(Helper.createTList(8), p.calcNodes());
        // assertEquals(1, p.calcPoints().size());
        assertEquals(p.toString(), 0, p.getDistance(), 1e-4);

        // very close
        p = calcPathViaQuery(graph, 0.00092, 0, 0.00091, 0);
        assertEquals(Helper.createTList(8, 9), p.calcNodes());
        assertEquals(p.toString(), 1.11, p.getDistance(), .1);
    }

    @Test
    public void testViaEdges_BiGraph()
    {
        Graph graph = createGraph(false);
        initBiGraph(graph);

        // 0-7 to 4-3        
        Path p = calcPathViaQuery(graph, 0.0009, 0, 0.001, 0.001105);
        assertEquals(p.toString(), Helper.createTList(10, 7, 6, 8, 3, 9), p.calcNodes());
        assertEquals(p.toString(), 324.11, p.getDistance(), 0.01);

        // 0-1 to 2-3
        p = calcPathViaQuery(graph, 0.001, 0.0001, 0.010, 0.0011);
        assertEquals(p.toString(), Helper.createTList(0, 7, 6, 8, 3, 9), p.calcNodes());
        assertEquals(p.toString(), 1335.35, p.getDistance(), 0.01);
    }

    @Test
    public void testViaEdges_WithCoordinates()
    {
        Graph graph = createTestGraph();
        Path p = calcPath(graph, 0, 1, 2, 3);
        assertEquals(Helper.createTList(9, 1, 2, 8), p.calcNodes());
        assertEquals(p.toString(), 56.7, p.getDistance(), .1);
    }

    @Test
    public void testViaEdges_SpecialCases()
    {
        Graph graph = createGraph(false);
        // 0->1\
        // |    2
        // 4<-3/
        graph.edge(0, 1, 7, false);
        graph.edge(1, 2, 7, true);
        graph.edge(2, 3, 7, true);
        graph.edge(3, 4, 7, false);
        graph.edge(4, 0, 7, true);

        updateDistancesFor(graph, 4, 0, 0);
        updateDistancesFor(graph, 0, 0.00010, 0);
        updateDistancesFor(graph, 1, 0.00010, 0.0001);
        updateDistancesFor(graph, 2, 0.00005, 0.00015);
        updateDistancesFor(graph, 3, 0, 0.0001);

        // 0-1 to 3-4
        Path p = calcPathViaQuery(graph, 0.00010, 0.00001, 0, 0.00009);
        assertEquals(Helper.createTList(6, 1, 2, 3, 5), p.calcNodes());
        assertEquals(p.toString(), 26.81, p.getDistance(), .1);

        // overlapping edges: 2-3 and 3-2
        p = calcPathViaQuery(graph, 0.000049, 0.00014, 0.00001, 0.0001);
        assertEquals(Helper.createTList(5, 6), p.calcNodes());
        assertEquals(p.toString(), 6.2, p.getDistance(), .1);

        // 'from' and 'to' edge share one node '2': 1-2 to 3-2
        p = calcPathViaQuery(graph, 0.00009, 0.00011, 0.00001, 0.00011);
        assertEquals(p.toString(), Helper.createTList(6, 2, 5), p.calcNodes());
        assertEquals(p.toString(), 12.57, p.getDistance(), .1);
    }

    @Test
    public void testQueryGraphAndFastest()
    {
        Graph graph = createGraph(false);
        initDirectedAndDiffSpeed(graph, carEncoder);
        Path p = calcPathViaQuery("fastest", graph, 0.002, 0.0005, 0.0017, 0.0031);
        assertEquals(Helper.createTList(9, 1, 5, 3, 8), p.calcNodes());
        assertEquals(602.98, p.getDistance(), 1e-1);
    }

    // Problem: for contraction hierarchy we cannot easily select egdes by nodes as some edges are skipped
    Path calcPathViaQuery( Graph graph, double fromLat, double fromLon, double toLat, double toLon )
    {
        return calcPathViaQuery("shortest", graph, fromLat, fromLon, toLat, toLon);
    }

    Path calcPathViaQuery( String weighting, Graph graph, double fromLat, double fromLon, double toLat, double toLon )
    {
        LocationIndex index = new LocationIndexTree(graph.getBaseGraph(), new RAMDirectory());
        index.prepareIndex();
        QueryResult from = index.findClosest(fromLat, fromLon, EdgeFilter.ALL_EDGES);
        QueryResult to = index.findClosest(toLat, toLon, EdgeFilter.ALL_EDGES);
        Weighting w = new ShortestWeighting();
        if (weighting.equalsIgnoreCase("fastest"))
            w = new FastestWeighting(carEncoder);

        // correct order for CH: in factory do prepare and afterwards wrap in query graph
        AlgorithmOptions opts = AlgorithmOptions.start().flagEncoder(carEncoder).weighting(w).build();
        RoutingAlgorithmFactory factory = createFactory(graph, opts);
        QueryGraph qGraph = new QueryGraph(graph).lookup(from, to);
        return factory.createAlgo(qGraph, opts).
                calcPath(from.getClosestNode(), to.getClosestNode());
    }

    Path calcPath( Graph graph, int fromNode1, int fromNode2, int toNode1, int toNode2 )
    {
        // lookup two edges: fromNode1-fromNode2 and toNode1-toNode2        
        QueryResult from = newQR(graph, fromNode1, fromNode2);
        QueryResult to = newQR(graph, toNode1, toNode2);

        RoutingAlgorithmFactory factory = createFactory(graph, defaultOpts);
        QueryGraph qGraph = new QueryGraph(graph).lookup(from, to);
        return factory.createAlgo(qGraph, defaultOpts).calcPath(from.getClosestNode(), to.getClosestNode());
    }

    /**
     * Creates query result on edge (node1-node2) very close to node1.
     */
    QueryResult newQR( Graph graph, int node1, int node2 )
    {
        EdgeIteratorState edge = GHUtility.getEdge(graph, node1, node2);
        if (edge == null)
            throw new IllegalStateException("edge not found? " + node1 + "-" + node2);

        NodeAccess na = graph.getNodeAccess();
        double lat = na.getLatitude(edge.getBaseNode());
        double lon = na.getLongitude(edge.getBaseNode());
        double latAdj = na.getLatitude(edge.getAdjNode());
        double lonAdj = na.getLongitude(edge.getAdjNode());
        // calculate query point near the base node but not directly on it!
        QueryResult res = new QueryResult(lat + (latAdj - lat) * .1, lon + (lonAdj - lon) * .1);
        res.setClosestNode(edge.getBaseNode());
        res.setClosestEdge(edge);
        res.setWayIndex(0);
        res.setSnappedPosition(QueryResult.Position.EDGE);
        res.calcSnappedPoint(distCalc);
        return res;
    }

    @Test
    public void testTwoWeightsPerEdge()
    {
        FlagEncoder encoder = new Bike2WeightFlagEncoder();
        Graph graph = initEleGraph(createGraph(new EncodingManager(encoder), true));
        // force the other path
        GHUtility.getEdge(graph, 0, 3).setFlags(encoder.setProperties(10, false, true));

        // for two weights per edge it happened that Path (and also the Weighting) read the wrong side 
        // of the speed and read 0 => infinity weight => overflow of millis => negative millis!
        Path p = createAlgo(graph, AlgorithmOptions.start().flagEncoder(encoder).weighting(new FastestWeighting(encoder)).build()).calcPath(0, 10);
//        assertEquals(Helper.createTList(13, 0, 1, 2, 11, 7, 10, 12), p.calcNodes());
        assertEquals(85124371, p.getTime());
        assertEquals(425622, p.getDistance(), 1);
        assertEquals(85124.4, p.getWeight(), 1);
    }

    @Test
    public void test0SpeedButUnblocked_Issue242()
    {
        Graph graph = createGraph(false);
        long flags = carEncoder.setAccess(carEncoder.setSpeed(0, 0), true, true);

        graph.edge(0, 1).setFlags(flags).setDistance(10);
        graph.edge(1, 2).setFlags(flags).setDistance(10);

        RoutingAlgorithm algo = createAlgo(graph);
        try
        {
            Path p = algo.calcPath(0, 2);
            assertTrue(false);
        } catch (Exception ex)
        {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Speed cannot be 0"));
        }
    }

    @Test
    public void testTwoWeightsPerEdge2()
    {
        // other direction should be different!
        Graph graph = initEleGraph(createGraph(true));
        Path p = createAlgo(graph).calcPath(0, 10);
        // GHUtility.printEdgeInfo(graph, carEncoder);
        assertEquals(Helper.createTList(0, 4, 6, 10), p.calcNodes());
        Weighting fakeWeighting = new Weighting()
        {
            @Override
            public double getMinWeight( double distance )
            {
                return distance;
            }

            @Override
            public double calcWeight( EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId )
            {
                int adj = edgeState.getAdjNode();
                int base = edgeState.getBaseNode();
                if (reverse)
                {
                    int tmp = base;
                    base = adj;
                    adj = tmp;
                }

                // a 'hill' at node 6
                if (adj == 6)
                    return 3 * edgeState.getDistance();
                else if (base == 6)
                    return edgeState.getDistance() * 0.9;
                else if (adj == 4)
                    return 2 * edgeState.getDistance();

                return edgeState.getDistance() * 0.8;
            }
        };

        graph = initEleGraph(createGraph(true));
        QueryResult from = newQR(graph, 3, 0);
        QueryResult to = newQR(graph, 10, 9);

        AlgorithmOptions opts = AlgorithmOptions.start().flagEncoder(carEncoder).weighting(fakeWeighting).build();
        RoutingAlgorithmFactory factory = createFactory(graph, opts);
        QueryGraph qGraph = new QueryGraph(graph).lookup(from, to);
        p = factory.createAlgo(qGraph, opts).calcPath(from.getClosestNode(), to.getClosestNode());
        assertEquals(Helper.createTList(13, 0, 1, 2, 11, 7, 10, 12), p.calcNodes());
        assertEquals(37009621, p.getTime());
        assertEquals(616827, p.getDistance(), 1);
        assertEquals(493462, p.getWeight(), 1);
    }

    // 0-1-2
    // |\| |
    // 3 4-11
    // | | |
    // 5-6-7
    // | |\|
    // 8-9-10
    Graph initEleGraph( Graph g )
    {
        g.edge(0, 1, 10, true);
        g.edge(0, 4, 12, true);
        g.edge(0, 3, 5, true);
        g.edge(1, 2, 10, true);
        g.edge(1, 4, 5, true);
        g.edge(3, 5, 5, false);
        g.edge(5, 6, 10, true);
        g.edge(5, 8, 10, true);
        g.edge(6, 4, 5, true);
        g.edge(6, 7, 10, true);
        g.edge(6, 10, 12, true);
        g.edge(6, 9, 12, true);
        g.edge(2, 11, 5, false);
        g.edge(4, 11, 10, true);
        g.edge(7, 11, 5, true);
        g.edge(7, 10, 5, true);
        g.edge(8, 9, 10, false);
        g.edge(9, 8, 9, false);
        g.edge(10, 9, 10, false);
        updateDistancesFor(g, 0, 3, 0);
        updateDistancesFor(g, 3, 2.5, 0);
        updateDistancesFor(g, 5, 1, 0);
        updateDistancesFor(g, 8, 0, 0);
        updateDistancesFor(g, 1, 3, 1);
        updateDistancesFor(g, 4, 2, 1);
        updateDistancesFor(g, 6, 1, 1);
        updateDistancesFor(g, 9, 0, 1);
        updateDistancesFor(g, 2, 3, 2);
        updateDistancesFor(g, 11, 2, 2);
        updateDistancesFor(g, 7, 1, 2);
        updateDistancesFor(g, 10, 0, 2);
        return g;
    }

    public static Graph initGraphWeightLimit( Graph g )
    {
        //      0----1
        //     /     |
        //    7--    |
        //   /   |   |
        //   6---5   |
        //   |   |   |
        //   4---3---2

        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);

        g.edge(3, 2, 1, true);
        g.edge(3, 5, 1, true);
        g.edge(5, 7, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(4, 6, 1, true);
        g.edge(6, 7, 1, true);
        g.edge(6, 5, 1, true);
        g.edge(0, 7, 1, true);
        return g;
    }

    public Graph getMatrixGraph()
    {
        return getMatrixAlikeGraph();
    }

    public static Graph getMatrixAlikeGraph()
    {
        if (matrixGraph == null)
            matrixGraph = createMatrixAlikeGraph();
        return matrixGraph;
    }

    private static Graph createMatrixAlikeGraph()
    {
        int WIDTH = 10;
        int HEIGHT = 15;
        Graph tmpGraph = new GraphBuilder(encodingManager).create();
        int[][] matrix = new int[WIDTH][HEIGHT];
        int counter = 0;
        Random rand = new Random(12);
        boolean print = false;
        for (int h = 0; h < HEIGHT; h++)
        {
            if (print)
            {
                for (int w = 0; w < WIDTH; w++)
                {
                    System.out.print(" |\t           ");
                }
                System.out.println();
            }

            for (int w = 0; w < WIDTH; w++)
            {
                matrix[w][h] = counter++;
                if (h > 0)
                {
                    float dist = 5 + Math.abs(rand.nextInt(5));
                    if (print)
                        System.out.print(" " + (int) dist + "\t           ");

                    tmpGraph.edge(matrix[w][h], matrix[w][h - 1], dist, true);
                }
            }
            if (print)
            {
                System.out.println();
                if (h > 0)
                {
                    for (int w = 0; w < WIDTH; w++)
                    {
                        System.out.print(" |\t           ");
                    }
                    System.out.println();
                }
            }

            for (int w = 0; w < WIDTH; w++)
            {
                if (w > 0)
                {
                    float dist = 5 + Math.abs(rand.nextInt(5));
                    if (print)
                        System.out.print("-- " + (int) dist + "\t-- ");
                    tmpGraph.edge(matrix[w][h], matrix[w - 1][h], dist, true);
                }
                if (print)
                    System.out.print("(" + matrix[w][h] + ")\t");
            }
            if (print)
                System.out.println();
        }

        return tmpGraph;
    }
}
