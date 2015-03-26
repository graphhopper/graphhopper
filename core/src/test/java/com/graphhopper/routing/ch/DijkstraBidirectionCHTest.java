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
package com.graphhopper.routing.ch;

import com.graphhopper.routing.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.EdgeSkipIterState;
import com.graphhopper.util.Helper;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests if a graph optimized by contraction hierarchies returns the same results as a none
 * optimized one. Additionally fine grained path unpacking is tested.
 * <p/>
 * @author Peter Karich
 */
public class DijkstraBidirectionCHTest extends AbstractRoutingAlgorithmTester
{
    // matrix graph is expensive to create and to prepare!
    private static Graph preparedMatrixGraph;

    @Override
    public Graph getMatrixGraph()
    {
        if (preparedMatrixGraph == null)
        {
            LevelGraph lg = (LevelGraph) createGraph(false);
            getMatrixAlikeGraph().copyTo(lg);
            createFactory(lg, defaultOpts);
            preparedMatrixGraph = lg;
        }
        return preparedMatrixGraph;
    }

    @Override
    protected LevelGraph createGraph( EncodingManager em, boolean is3D )
    {
        return new GraphBuilder(em).set3D(is3D).levelGraphCreate();
    }

    @Override
    public RoutingAlgorithm createAlgo( Graph g, AlgorithmOptions opts )
    {
        return createFactory(g, opts).createAlgo(g, opts);
    }

    @Override
    public RoutingAlgorithmFactory createFactory( Graph g, AlgorithmOptions opts )
    {
        PrepareContractionHierarchies ch = new PrepareContractionHierarchies(new GHDirectory("", DAType.RAM_INT),
                (LevelGraph) g, opts.getFlagEncoder(), opts.getWeighting(), TraversalMode.NODE_BASED);
        // hack: prepare matrixGraph only once
        if (g != preparedMatrixGraph)
            ch.doWork();

        return ch;
    }

    @Test
    public void testPathRecursiveUnpacking()
    {
        // use an encoder where it is possible to store 2 weights per edge
        FlagEncoder encoder = new Bike2WeightFlagEncoder();
        EncodingManager em = new EncodingManager(encoder);
        LevelGraphStorage g2 = (LevelGraphStorage) createGraph(em, false);
        g2.edge(0, 1, 1, true);
        EdgeIteratorState iter1_1 = g2.edge(0, 2, 1.4, false);
        EdgeIteratorState iter1_2 = g2.edge(2, 5, 1.4, false);
        g2.edge(1, 2, 1, true);
        g2.edge(1, 3, 3, true);
        g2.edge(2, 3, 1, true);
        g2.edge(4, 3, 1, true);
        g2.edge(2, 5, 1.4, true);
        g2.edge(3, 5, 1, true);
        g2.edge(5, 6, 1, true);
        g2.edge(4, 6, 1, true);
        g2.edge(6, 7, 1, true);
        EdgeIteratorState iter2_2 = g2.edge(5, 7);
        iter2_2.setDistance(1.4).setFlags(encoder.setProperties(10, true, false));

        // simulate preparation
        EdgeSkipIterState iter2_1 = g2.shortcut(0, 5);
        iter2_1.setDistance(2.8).setFlags(encoder.setProperties(10, true, false));
        iter2_1.setSkippedEdges(iter1_1.getEdge(), iter1_2.getEdge());
        EdgeSkipIterState tmp = g2.shortcut(0, 7);
        tmp.setDistance(4.2).setFlags(encoder.setProperties(10, true, false));
        tmp.setSkippedEdges(iter2_1.getEdge(), iter2_2.getEdge());
        g2.setLevel(1, 0);
        g2.setLevel(3, 1);
        g2.setLevel(4, 2);
        g2.setLevel(6, 3);
        g2.setLevel(2, 4);
        g2.setLevel(5, 5);
        g2.setLevel(7, 6);
        g2.setLevel(0, 7);

        ShortestWeighting weighting = new ShortestWeighting();
        AlgorithmOptions opts = new AlgorithmOptions(AlgorithmOptions.DIJKSTRA_BI, encoder, weighting);
        Path p = new PrepareContractionHierarchies(new GHDirectory("", DAType.RAM_INT), 
                g2, encoder, weighting, TraversalMode.NODE_BASED).
                createAlgo(g2, opts).calcPath(0, 7);

        assertEquals(Helper.createTList(0, 2, 5, 7), p.calcNodes());
        assertEquals(1064, p.getTime());
        assertEquals(4.2, p.getDistance(), 1e-5);
    }

    @Override
    public void testCalcFootPath()
    {
        // disable car encoder and move foot to first position => workaround as CH does not allow multiple vehicles
        FlagEncoder tmpFootEncoder = footEncoder;
        FlagEncoder tmpCarEncoder = carEncoder;
        carEncoder = new CarFlagEncoder()
        {
            @Override
            public long setProperties( double speed, boolean forward, boolean backward )
            {
                return 0;
            }
        };

        footEncoder = new FootFlagEncoder();
        new EncodingManager(footEncoder);

        super.testCalcFootPath();
        footEncoder = tmpFootEncoder;
        carEncoder = tmpCarEncoder;
    }

    @Test
    public void testBaseGraph()
    {
        CarFlagEncoder carFE = new CarFlagEncoder();
        Graph g = createGraph(new EncodingManager(carFE), false);
        initDirectedAndDiffSpeed(g, carFE);

        // do CH preparation for car
        createFactory(g, defaultOpts);

        // use base graph for solving normal Dijkstra
        Path p1 = new RoutingAlgorithmFactorySimple().createAlgo(g, defaultOpts).calcPath(0, 3);
        assertEquals(Helper.createTList(0, 1, 5, 2, 3), p1.calcNodes());
        assertEquals(p1.toString(), 402.293, p1.getDistance(), 1e-6);
        assertEquals(p1.toString(), 144823, p1.getTime());
    }

    @Test
    public void testBaseGraphMultipleVehicles()
    {
        Graph g = createGraph(encodingManager, false);
        initFootVsCar(g);

        AlgorithmOptions footOptions = AlgorithmOptions.start().flagEncoder(footEncoder).
                weighting(new FastestWeighting(footEncoder)).build();
        AlgorithmOptions carOptions = AlgorithmOptions.start().flagEncoder(carEncoder).
                weighting(new FastestWeighting(carEncoder)).build();

        // do CH preparation for car
        RoutingAlgorithmFactory contractedFactory = createFactory(g, carOptions);

        // use contracted graph
        Path p1 = contractedFactory.createAlgo(g, carOptions).calcPath(0, 7);
        assertEquals(Helper.createTList(0, 4, 6, 7), p1.calcNodes());
        assertEquals(p1.toString(), 15000, p1.getDistance(), 1e-6);

        // use base graph for solving normal Dijkstra via car
        Path p2 = new RoutingAlgorithmFactorySimple().createAlgo(g.getBaseGraph(), carOptions).calcPath(0, 7);
        assertEquals(Helper.createTList(0, 4, 6, 7), p2.calcNodes());
        assertEquals(p2.toString(), 15000, p2.getDistance(), 1e-6);
        assertEquals(p2.toString(), 2700 * 1000, p2.getTime());

        // use base graph for solving normal Dijkstra via foot
        Path p3 = new RoutingAlgorithmFactorySimple().createAlgo(g.getBaseGraph(), footOptions).calcPath(0, 7);
        assertEquals(p3.toString(), 17000, p3.getDistance(), 1e-6);
        assertEquals(p3.toString(), 12240 * 1000, p3.getTime());
        assertEquals(Helper.createTList(0, 4, 5, 7), p3.calcNodes());
    }
}
