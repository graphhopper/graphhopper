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
package com.graphhopper.routing;

import com.graphhopper.routing.util.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import static org.junit.Assert.*;
import static com.graphhopper.util.GHUtility.*;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author Peter Karich
 */
@RunWith(Parameterized.class)
public class EdgeBasedRoutingAlgorithmTest
{
    private FlagEncoder carEncoder;

    EncodingManager createEncodingManager( boolean restrictedOnly )
    {
        if (restrictedOnly)
            carEncoder = new CarFlagEncoder(5, 5, 1);
        else
            // allow for basic costs too
            carEncoder = new CarFlagEncoder(5, 5, 3);
        return new EncodingManager(carEncoder);
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> configs()
    {
        return Arrays.asList(new Object[][]
                {
                        {AlgorithmOptions.DIJKSTRA},
                        {AlgorithmOptions.DIJKSTRA_BI},
                        {AlgorithmOptions.ASTAR},
                        {AlgorithmOptions.ASTAR_BI}
                        // TODO { AlgorithmOptions.DIJKSTRA_ONE_TO_MANY }
                });
    }

    private final String algoStr;

    public EdgeBasedRoutingAlgorithmTest( String algo )
    {
        this.algoStr = algo;
    }

    public RoutingAlgorithm createAlgo( Graph g, AlgorithmOptions opts )
    {
        opts = AlgorithmOptions.start(opts).algorithm(algoStr).build();
        return new RoutingAlgorithmFactorySimple().createAlgo(g, opts);
    }

    protected GraphStorage createGraph( EncodingManager em )
    {
        return new GraphBuilder(em).create();
    }

    // 0---1
    // |   /
    // 2--3--4
    // |  |  |
    // 5--6--7
    public static void initGraph( Graph g )
    {
        g.edge(0, 1, 3, true);
        g.edge(0, 2, 1, true);
        g.edge(1, 3, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(2, 5, .5, true);
        g.edge(3, 6, 1, true);
        g.edge(4, 7, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 7, 1, true);
    }

    private void initTurnRestrictions( Graph g, TurnCostExtension tcs, TurnCostEncoder tEncoder )
    {
        long tflags = tEncoder.getTurnFlags(true, 0);

        // only forward from 2-3 to 3-4 => limit 2,3->3,6 and 2,3->3,1
        tcs.addTurnInfo(getEdge(g, 2, 3).getEdge(), 3, getEdge(g, 3, 6).getEdge(), tflags);
        tcs.addTurnInfo(getEdge(g, 2, 3).getEdge(), 3, getEdge(g, 3, 1).getEdge(), tflags);

        // only right   from 5-2 to 2-3 => limit 5,2->2,0
        tcs.addTurnInfo(getEdge(g, 5, 2).getEdge(), 2, getEdge(g, 2, 0).getEdge(), tflags);

        // only right   from 7-6 to 6-3 => limit 7,6->6,5
        tcs.addTurnInfo(getEdge(g, 7, 6).getEdge(), 6, getEdge(g, 6, 5).getEdge(), tflags);

        // no 5-6 to 6-3
        tcs.addTurnInfo(getEdge(g, 5, 6).getEdge(), 6, getEdge(g, 6, 3).getEdge(), tflags);
        // no 4-3 to 3-1
        tcs.addTurnInfo(getEdge(g, 4, 3).getEdge(), 3, getEdge(g, 3, 1).getEdge(), tflags);
        // no 4-3 to 3-2
        tcs.addTurnInfo(getEdge(g, 4, 3).getEdge(), 3, getEdge(g, 3, 2).getEdge(), tflags);

        // no u-turn at 6-7
        tcs.addTurnInfo(getEdge(g, 6, 7).getEdge(), 7, getEdge(g, 7, 6).getEdge(), tflags);

        // no u-turn at 3-6
        tcs.addTurnInfo(getEdge(g, 3, 6).getEdge(), 6, getEdge(g, 6, 3).getEdge(), tflags);
    }

    Weighting createWeighting( FlagEncoder encoder, TurnCostExtension tcs, double turnCosts )
    {
        return new TurnWeighting(new FastestWeighting(encoder), encoder, tcs).setDefaultUTurnCost(turnCosts);
    }

    @Test
    public void testBasicTurnRestriction()
    {
        GraphStorage g = createGraph(createEncodingManager(true));
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();
        initTurnRestrictions(g, tcs, carEncoder);
        Path p = createAlgo(g, AlgorithmOptions.start().
                flagEncoder(carEncoder).
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR).build()).
                calcPath(5, 1);
        assertEquals(Helper.createTList(5, 2, 3, 4, 7, 6, 3, 1), p.calcNodes());

        // test 7-6-5 and reverse
        p = createAlgo(g, AlgorithmOptions.start().
                flagEncoder(carEncoder).
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_1DIR).build()).
                calcPath(5, 7);
        assertEquals(Helper.createTList(5, 6, 7), p.calcNodes());

        p = createAlgo(g, AlgorithmOptions.start().
                flagEncoder(carEncoder).
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_1DIR).build()).
                calcPath(7, 5);
        assertEquals(Helper.createTList(7, 6, 3, 2, 5), p.calcNodes());
    }

    @Test
    public void testUTurns()
    {
        GraphStorage g = createGraph(createEncodingManager(true));
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();

        long tflags = carEncoder.getTurnFlags(true, 0);

        // force u-turn via lowering the cost for it
        EdgeIteratorState e3_6 = getEdge(g, 3, 6);
        e3_6.setDistance(0.1);
        getEdge(g, 3, 2).setDistance(864);
        getEdge(g, 1, 0).setDistance(864);

        tcs.addTurnInfo(getEdge(g, 7, 6).getEdge(), 6, getEdge(g, 6, 5).getEdge(), tflags);
        tcs.addTurnInfo(getEdge(g, 4, 3).getEdge(), 3, e3_6.getEdge(), tflags);
        AlgorithmOptions opts = AlgorithmOptions.start().
                flagEncoder(carEncoder).
                weighting(createWeighting(carEncoder, tcs, 50)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR_UTURN).build();
        Path p = createAlgo(g, opts).calcPath(7, 5);

        assertEquals(Helper.createTList(7, 6, 3, 6, 5), p.calcNodes());

        // no u-turn for 6-3
        opts = AlgorithmOptions.start().
                flagEncoder(carEncoder).
                weighting(createWeighting(carEncoder, tcs, 100)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR_UTURN).build();
        tcs.addTurnInfo(getEdge(g, 6, 3).getEdge(), 3, getEdge(g, 3, 6).getEdge(), tflags);
        p = createAlgo(g, opts).calcPath(7, 5);

        assertEquals(Helper.createTList(7, 6, 3, 2, 5), p.calcNodes());
    }

    @Test
    public void testBasicTurnCosts()
    {
        GraphStorage g = createGraph(createEncodingManager(false));
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();
        Path p = createAlgo(g, AlgorithmOptions.start().
                flagEncoder(carEncoder).
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_1DIR).build()).
                calcPath(5, 1);

        // no restriction and costs
        EdgeIteratorState e3_6 = getEdge(g, 5, 6);
        e3_6.setDistance(2);
        assertEquals(Helper.createTList(5, 2, 3, 1), p.calcNodes());

        // now introduce some turn costs
        long tflags = carEncoder.getTurnFlags(false, 2);
        tcs.addTurnInfo(getEdge(g, 5, 2).getEdge(), 2, getEdge(g, 2, 3).getEdge(), tflags);

        p = createAlgo(g, AlgorithmOptions.start().
                flagEncoder(carEncoder).
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_1DIR).build()).
                calcPath(5, 1);
        assertEquals(Helper.createTList(5, 6, 3, 1), p.calcNodes());
    }
}
