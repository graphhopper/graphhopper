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
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.index.LocationIDResult;
import com.graphhopper.util.*;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import java.util.Random;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public abstract class AbstractRoutingAlgorithmTester
{
    // problem is: matrix graph is expensive to create to cache it in a static variable
    private static Graph matrixGraph;
    protected static EncodingManager encodingManager = new EncodingManager("CAR,FOOT");
    protected CarFlagEncoder carEncoder = (CarFlagEncoder) encodingManager.getEncoder("CAR");
    protected FootFlagEncoder footEncoder = (FootFlagEncoder) encodingManager.getEncoder("FOOT");

    protected Graph createGraph()
    {
        return new GraphBuilder(encodingManager).create();
    }

    public AlgorithmPreparation prepareGraph( Graph g )
    {
        return prepareGraph(g, carEncoder, new ShortestCalc());
    }

    public abstract AlgorithmPreparation prepareGraph( Graph g, FlagEncoder encoder, WeightCalculation calc );

    @Test
    public void testCalcShortestPath()
    {
        Graph graph = createTestGraph();
        Path p = prepareGraph(graph).createAlgo().calcPath(0, 7);
        assertEquals(p.toString(), 13, p.getDistance(), 1e-4);
        assertEquals(p.toString(), 5, p.calcNodes().size());
    }

    // see calc-fastest-graph.svg
    @Test
    public void testCalcFastestPath()
    {
        Graph graphShortest = createGraph();
        initDirectedAndDiffSpeed(graphShortest);
        Path p1 = prepareGraph(graphShortest, carEncoder, new ShortestCalc()).createAlgo().calcPath(0, 3);
        assertEquals(Helper.createTList(0, 1, 5, 2, 3), p1.calcNodes());
        assertEquals(p1.toString(), 24000, p1.getDistance(), 1e-6);
        assertEquals(p1.toString(), 8640, p1.getTime());

        Graph graphFastest = createGraph();
        initDirectedAndDiffSpeed(graphFastest);
        Path p2 = prepareGraph(graphFastest, carEncoder, new FastestCalc(carEncoder)).createAlgo().calcPath(0, 3);
        assertEquals(Helper.createTList(0, 4, 6, 7, 5, 3), p2.calcNodes());
        assertEquals(p2.toString(), 31000, p2.getDistance(), 1e-6);
        assertEquals(p2.toString(), 5580, p2.getTime());
    }

    // 0-1-2-3
    // |/|/ /|
    // 4-5-- |
    // |/ \--7
    // 6----/
    void initDirectedAndDiffSpeed( Graph graph )
    {
        graph.edge(0, 1, 7000, carEncoder.flags(10, false));
        graph.edge(0, 4, 5000, carEncoder.flags(20, false));

        graph.edge(1, 4, 7000, carEncoder.flags(10, true));
        graph.edge(1, 5, 7000, carEncoder.flags(10, true));
        graph.edge(1, 2, 20000, carEncoder.flags(10, true));

        graph.edge(5, 2, 5000, carEncoder.flags(10, false));
        graph.edge(2, 3, 5000, carEncoder.flags(10, false));

        graph.edge(5, 3, 11000, carEncoder.flags(20, false));
        graph.edge(3, 7, 7000, carEncoder.flags(10, false));

        graph.edge(4, 6, 5000, carEncoder.flags(20, false));
        graph.edge(5, 4, 7000, carEncoder.flags(10, false));

        graph.edge(5, 6, 7000, carEncoder.flags(10, false));
        graph.edge(7, 5, 5000, carEncoder.flags(20, false));

        graph.edge(6, 7, 5000, carEncoder.flags(20, true));
    }

    @Test
    public void testCalcFootPath()
    {
        Graph graphShortest = createGraph();
        initFootVsCar(graphShortest);
        Path p1 = prepareGraph(graphShortest, footEncoder, new ShortestCalc()).createAlgo().calcPath(0, 7);
        assertEquals(p1.toString(), 17000, p1.getDistance(), 1e-6);
        assertEquals(p1.toString(), 12240, p1.getTime());
        assertEquals(Helper.createTList(0, 4, 5, 7), p1.calcNodes());
    }

    void initFootVsCar( Graph graph )
    {
        graph.edge(0, 1, 7000, footEncoder.flags(5, true) | carEncoder.flags(10, false));
        graph.edge(0, 4, 5000, footEncoder.flags(5, true) | carEncoder.flags(20, false));

        graph.edge(1, 4, 7000, carEncoder.flags(10, true));
        graph.edge(1, 5, 7000, carEncoder.flags(10, true));
        graph.edge(1, 2, 20000, footEncoder.flags(5, true) | carEncoder.flags(10, true));

        graph.edge(5, 2, 5000, carEncoder.flags(10, false));
        graph.edge(2, 3, 5000, footEncoder.flags(5, true) | carEncoder.flags(10, false));

        graph.edge(5, 3, 11000, carEncoder.flags(20, false));
        graph.edge(3, 7, 7000, footEncoder.flags(5, true) | carEncoder.flags(10, false));

        graph.edge(4, 6, 5000, carEncoder.flags(20, false));
        graph.edge(5, 4, 7000, footEncoder.flags(5, true) | carEncoder.flags(10, false));

        graph.edge(5, 6, 7000, carEncoder.flags(10, false));
        graph.edge(7, 5, 5000, footEncoder.flags(5, true) | carEncoder.flags(20, false));

        graph.edge(6, 7, 5000, carEncoder.flags(20, true));
    }

    // see test-graph.svg !
    protected Graph createTestGraph()
    {
        Graph graph = createGraph();

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

        graph.edge(6, 7, 5, true);
        return graph;
    }

    @Test
    public void testCalcIfEmptyWay()
    {
        Graph graph = createTestGraph();
        Path p = prepareGraph(graph).createAlgo().calcPath(0, 0);
        assertEquals(p.toString(), 0, p.calcNodes().size());
        assertEquals(p.toString(), 0, p.getDistance(), 1e-4);
    }

    @Test
    public void testNoPathFound()
    {
        Graph graph = createGraph();
        assertFalse(prepareGraph(graph).createAlgo().calcPath(0, 1).isFound());

        // two disconnected areas
        graph.edge(0, 1, 7, true);

        graph.edge(5, 6, 2, true);
        graph.edge(5, 7, 1, true);
        graph.edge(5, 8, 1, true);
        graph.edge(7, 8, 1, true);
        RoutingAlgorithm algo = prepareGraph(graph).createAlgo();
        assertFalse(algo.calcPath(0, 5).isFound());
        // assertEquals(4, algo.getVisitedNodes());

        // disconnected as directed graph
        graph = createGraph();
        graph.edge(0, 1, 1, false);
        graph.edge(0, 2, 1, true);
        algo = prepareGraph(graph).createAlgo();
        assertFalse(algo.calcPath(1, 2).isFound());
    }

    @Test
    public void testWikipediaShortestPath()
    {
        Graph graph = createWikipediaTestGraph();
        Path p = prepareGraph(graph).createAlgo().calcPath(0, 4);
        assertEquals(p.toString(), 20, p.getDistance(), 1e-4);
        assertEquals(p.toString(), 4, p.calcNodes().size());
    }

    @Test
    public void testCalcIf1EdgeAway()
    {
        Graph graph = createTestGraph();
        Path p = prepareGraph(graph).createAlgo().calcPath(1, 2);
        assertEquals(Helper.createTList(1, 2), p.calcNodes());
        assertEquals(p.toString(), 2, p.getDistance(), 1e-4);
    }

    // see wikipedia-graph.svg !
    protected Graph createWikipediaTestGraph()
    {
        Graph graph = createGraph();
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
    public static void initBiGraph( Graph graph )
    {
        graph.edge(0, 1, 200, true);
        graph.edge(1, 2, 20, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 5, true);
        graph.edge(4, 5, 25, true);
        graph.edge(5, 6, 25, true);
        graph.edge(6, 7, 5, true);
        graph.edge(7, 0, 5, true);
        graph.edge(3, 8, 20, true);
        graph.edge(8, 6, 20, true);

        // we need lat,lon for edge precise queries because the distances of snapped point 
        // to adjacent nodes is calculated from lat,lon of the necessary points
        graph.setNode(0, 0.001, 0);
        graph.setNode(1, 0.100, 0.0005);
        graph.setNode(2, 0.010, 0.0010);
        graph.setNode(3, 0.001, 0.0011);
        graph.setNode(4, 0.001, 0.00111);

        graph.setNode(8, 0.0005, 0.0011);

        graph.setNode(7, 0, 0);
        graph.setNode(6, 0, 0.001);
        graph.setNode(5, 0, 0.004);
    }

    @Test
    public void testBidirectional()
    {
        Graph graph = createGraph();
        initBiGraph(graph);

        // PrepareTowerNodesShortcutsTest.printEdges((LevelGraph) graph);
        Path p = prepareGraph(graph).createAlgo().calcPath(0, 4);
        // PrepareTowerNodesShortcutsTest.printEdges((LevelGraph) graph);
        assertEquals(p.toString(), Helper.createTList(0, 7, 6, 8, 3, 4), p.calcNodes());
        assertEquals(p.toString(), 55, p.getDistance(), 1e-4);

        p = prepareGraph(graph).createAlgo().calcPath(1, 2);
        assertEquals(p.toString(), 20, p.getDistance(), 1e-4);
        assertEquals(p.toString(), Helper.createTList(1, 2), p.calcNodes());
    }

    // 1-2-3-4-5
    // |     / |
    // |    9  |
    // \   /   /
    //  8-7-6-/
    @Test
    public void testBidirectional2()
    {
        Graph graph = createGraph();

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

        Path p = prepareGraph(graph).createAlgo().calcPath(0, 4);
        assertEquals(p.toString(), 40, p.getDistance(), 1e-4);
        assertEquals(p.toString(), 5, p.calcNodes().size());
        assertEquals(Helper.createTList(0, 7, 6, 5, 4), p.calcNodes());
    }

    @Test
    public void testRekeyBugOfIntBinHeap()
    {
        // using DijkstraSimple + IntBinHeap then rekey loops endlessly
        Path p = prepareGraph(getMatrixGraph()).createAlgo().calcPath(36, 91);
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
        Path p = prepareGraph(getMatrixGraph()).createAlgo().calcPath(34, 36);
        assertEquals(Helper.createTList(34, 35, 36), p.calcNodes());
        assertEquals(3, p.calcNodes().size());
        assertEquals(17, p.getDistance(), 1e-5);
    }

    @Test
    public void testCorrectWeight()
    {
        Path p = prepareGraph(getMatrixGraph()).createAlgo().calcPath(45, 72);
        assertEquals(Helper.createTList(45, 44, 54, 64, 74, 73, 72), p.calcNodes());
        assertEquals(38f, p.getDistance(), 1e-3);
    }

    @Test
    public void testCannotCalculateSP()
    {
        Graph graph = createGraph();
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);

        Path p = prepareGraph(graph).createAlgo().calcPath(0, 2);
        assertEquals(p.toString(), 3, p.calcNodes().size());
    }

    @Test
    public void testDirectedGraphBug1()
    {
        Graph graph = createGraph();
        graph.edge(0, 1, 3, false);
        graph.edge(1, 2, 2.99, false);

        graph.edge(0, 3, 2, false);
        graph.edge(3, 4, 3, false);
        graph.edge(4, 2, 1, false);

        Path p = prepareGraph(graph).createAlgo().calcPath(0, 2);
        assertEquals(Helper.createTList(0, 1, 2), p.calcNodes());
        assertEquals(p.toString(), 5.99, p.getDistance(), 1e-4);
        assertEquals(p.toString(), 3, p.calcNodes().size());
    }

    @Test
    public void testDirectedGraphBug2()
    {
        Graph graph = createGraph();
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);

        graph.edge(3, 1, 4, true);

        Path p = prepareGraph(graph).createAlgo().calcPath(0, 3);
        assertEquals(Helper.createTList(0, 1, 2, 3), p.calcNodes());
    }

    // a-b-0-c-1
    // |   |  _/\
    // |  /  /  |
    // d-2--3-e-4
    @Test
    public void testWithCoordinates()
    {
        Graph graph = createGraph();
        graph.setNode(0, 0, 2);
        graph.setNode(1, 0, 3.5);
        graph.setNode(2, 1, 1);
        graph.setNode(3, 1.5, 2.5);
        graph.setNode(4, 0.5, 4.5);

        graph.edge(0, 1, 2, true).setWayGeometry(Helper.createPointList(0, 3));
        graph.edge(2, 3, 2, true);
        graph.edge(3, 4, 2, true).setWayGeometry(Helper.createPointList(1, 3.5));

        graph.edge(0, 2, 0.8, true).setWayGeometry(Helper.createPointList(0, 1.6, 0, 0, 1, 0));
        graph.edge(0, 2, 1.2, true);
        graph.edge(1, 3, 1.3, true);
        graph.edge(1, 4, 1, true);

        AlgorithmPreparation prepare = prepareGraph(graph);
        Path p = prepare.createAlgo().calcPath(4, 0);
        assertEquals(Helper.createTList(4, 1, 0), p.calcNodes());
        assertEquals(Helper.createPointList(0.5, 4.5, 0, 3.5, 0, 3, 0, 2), p.calcPoints());
        assertEquals(291110, p.calcPoints().calcDistance(new DistanceCalc()), 1);

        // PrepareTowerNodesShortcutsTest.printEdges((LevelGraph) graph);
        p = prepare.createAlgo().calcPath(2, 1);
        // System.out.println(p.toDetailsString());
        assertEquals(Helper.createTList(2, 0, 1), p.calcNodes());
        assertEquals(Helper.createPointList(1, 1, 1, 0, 0, 0, 0, 1.6, 0, 2, 0, 3, 0, 3.5), p.calcPoints());
        assertEquals(611555, p.calcPoints().calcDistance(new DistanceCalc()), 1);
    }

    @Test
    public void testViaEdges_BiGraph()
    {
        Graph graph = createGraph();
        initBiGraph(graph);

        Path p = calcPath(graph, 0, 7, 4, 3);
        assertEquals(p.toString(), Helper.createTList(9, 7, 6, 8, 3, 10), p.calcNodes());
        assertEquals(p.toString(), 157.3, p.getDistance(), 1e-2);

        p = calcPath(graph, 0, 1, 2, 3);
        assertEquals(p.toString(), Helper.createTList(9, 0, 7, 6, 8, 3, 10), p.calcNodes());
        assertEquals(p.toString(), 1050.83, p.getDistance(), .2);
    }

    @Test
    public void testViaEdges_FromEqualsTo()
    {
        Graph graph = createTestGraph();

        QueryGraph qGraph = new QueryGraph(null, graph, EdgeFilter.ALL_EDGES);
        LocationIDResult from = newQR(qGraph, 0, 1);
        qGraph.lookup(from);
        LocationIDResult to = newQR(qGraph, from.getClosestNode(), 1);
        to.setOnTowerNode(true);
        qGraph.lookup(to);
        RoutingAlgorithm algo = prepareGraph(graph).createAlgo();
        algo.setGraph(qGraph);
        Path p = algo.calcPath(from, to);
        assertEquals(new TIntArrayList(), p.calcNodes());
        assertEquals(p.toString(), 0, p.getDistance(), 1e-4);
    }

    @Test
    public void testViaEdges_WithCoordinates()
    {
        Graph graph = createTestGraph();
        Path p = calcPath(graph, 0, 1, 2, 3);
        assertEquals(Helper.createTList(8, 1, 2, 9), p.calcNodes());
        assertEquals(p.toString(), 2, p.getDistance(), 1e-4);
    }

    @Test
    public void testViaEdges_SpecialCases()
    {
        Graph graph = createGraph();
        // 0->1\
        // |    2
        // 4<-3/
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, false);
        graph.edge(4, 0, 1, true);

        graph.setNode(4, 0, 0);
        graph.setNode(0, 0.00010, 0);
        graph.setNode(1, 0.00010, 0.0001);
        graph.setNode(2, 0.00005, 0.00015);
        graph.setNode(3, 0, 0.0001);
        double dist23 = new DistanceCalc().calcDist(0.00005, 0.00015, 0, 0.0001);
        // todo include assertEquals(0.8, dist23, 0.01);

        Path p = calcPath(graph, 0, 1, 3, 4);
        assertEquals(Helper.createTList(5, 1, 2, 3, 6), p.calcNodes());
        assertEquals(p.toString(), 13.1, p.getDistance(), .1);

        // now overlapping edges: 2-3 creates new node '5'
        p = calcPath(graph, 2, 3, 3, 5);
        assertEquals(Helper.createTList(5, 6), p.calcNodes());
        assertEquals(p.toString(), 7.86, p.getDistance(), .1);

        // 'from' and 'to' edge share one node '2'
        p = calcPath(graph, 1, 2, 3, 2);
        assertEquals(p.toString(), Helper.createTList(5, 2, 6), p.calcNodes());
        assertEquals(p.toString(), 15.73, p.getDistance(), .1);
    }

    Path calcPath( Graph graph, int fromNode1, int fromNode2, int toNode1, int toNode2 )
    {
        // lookup two edges: fromNode1-fromNode2 and toNode1-toNode2
        // use QueryGraph to make special cases like identical edge working
        QueryGraph qGraph = new QueryGraph(null, graph, EdgeFilter.ALL_EDGES);
        LocationIDResult from = newQR(qGraph, fromNode1, fromNode2);
        qGraph.lookup(from);
        LocationIDResult to = newQR(qGraph, toNode1, toNode2);
        qGraph.lookup(to);
        RoutingAlgorithm algo = prepareGraph(graph).createAlgo();
        algo.setGraph(qGraph);
        return algo.calcPath(from, to);
    }

    /**
     * Creates query result on edge (node1-node2) very close to node1.
     */
    LocationIDResult newQR( Graph graph, int node1, int node2 )
    {
        EdgeIteratorState edge = GHUtility.getEdge(graph, node1, node2);
        if (edge == null)
            throw new IllegalStateException("edge not found? " + node1 + "-" + node2);

        double lat = graph.getLatitude(edge.getBaseNode());
        double lon = graph.getLongitude(edge.getBaseNode());
        double latAdj = graph.getLatitude(edge.getAdjNode());
        double lonAdj = graph.getLongitude(edge.getAdjNode());
        // calculate query point near the base node but not directly on it!
        LocationIDResult res = new LocationIDResult(lat + (latAdj - lat) * .1, lon + (lonAdj - lon) * .1);
        res.setClosestNode(edge.getBaseNode());
        res.setClosestEdge(edge);
        res.setWayIndex(0);
        return res;
    }

    public Graph getMatrixGraph()
    {
        return getMatrixAlikeGraph();
    }

    public static Graph getMatrixAlikeGraph()
    {
        if (matrixGraph == null)
        {
            matrixGraph = createMatrixAlikeGraph();
        }
        return matrixGraph;
    }

    private static Graph createMatrixAlikeGraph()
    {
        int WIDTH = 10;
        int HEIGHT = 15;
        Graph tmp = new GraphBuilder(encodingManager).create();
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
                    {
                        System.out.print(" " + (int) dist + "\t           ");
                    }
                    tmp.edge(matrix[w][h], matrix[w][h - 1], dist, true);
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
                    {
                        System.out.print("-- " + (int) dist + "\t-- ");
                    }
                    tmp.edge(matrix[w][h], matrix[w - 1][h], dist, true);
                }
                if (print)
                {
                    System.out.print("(" + matrix[w][h] + ")\t");
                }
            }
            if (print)
            {
                System.out.println();
            }
        }

        return tmp;
    }
}
