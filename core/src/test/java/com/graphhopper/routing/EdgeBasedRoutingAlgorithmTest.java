/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static com.graphhopper.util.GHUtility.getEdge;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
@RunWith(Parameterized.class)
public class EdgeBasedRoutingAlgorithmTest {
    private final String algoStr;
    private FlagEncoder carEncoder;

    public EdgeBasedRoutingAlgorithmTest(String algo) {
        this.algoStr = algo;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][]{
                // todo: make this test run also for edge-based CH or otherwise make sure time calculation is tested also for edge-based CH (at the moment it will fail!)
                // todo: make this test run also for ALT or otherwise make sure time calculation is tested also for ALT (at the moment it will fail?!)
                {DIJKSTRA},
                {DIJKSTRA_BI},
                {ASTAR},
                {ASTAR_BI}
                // TODO { AlgorithmOptions.DIJKSTRA_ONE_TO_MANY }
        });
    }

    // 0---1
    // |   /
    // 2--3--4
    // |  |  |
    // 5--6--7
    public static void initGraph(Graph g) {
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

    private EncodingManager createEncodingManager(boolean restrictedOnly) {
        if (restrictedOnly)
            carEncoder = new CarFlagEncoder(5, 5, 1);
        else
            // allow for basic costs too
            carEncoder = new CarFlagEncoder(5, 5, 3);
        return EncodingManager.create(carEncoder);
    }

    public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
        opts = AlgorithmOptions.start(opts).algorithm(algoStr).build();
        return new RoutingAlgorithmFactorySimple().createAlgo(g, opts);
    }

    private GraphHopperStorage createStorage(EncodingManager em) {
        return new GraphBuilder(em).create();
    }

    private void initTurnRestrictions(Graph g, TurnCostExtension tcs) {
        // only forward from 2-3 to 3-4 => limit 2,3->3,6 and 2,3->3,1
        addTurnRestriction(g, tcs, 2, 3, 6);
        addTurnRestriction(g, tcs, 2, 3, 1);

        // only right   from 5-2 to 2-3 => limit 5,2->2,0
        addTurnRestriction(g, tcs, 5, 2, 0);

        // only right   from 7-6 to 6-3 => limit 7,6->6,5
        addTurnRestriction(g, tcs, 7, 6, 5);

        // no 5-6 to 6-3
        addTurnRestriction(g, tcs, 5, 6, 3);
        // no 4-3 to 3-1
        addTurnRestriction(g, tcs, 4, 3, 1);
        // no 4-3 to 3-2
        addTurnRestriction(g, tcs, 4, 3, 2);

        // no u-turn at 6-7
        addTurnRestriction(g, tcs, 6, 7, 6);

        // no u-turn at 3-6
        addTurnRestriction(g, tcs, 3, 6, 3);
    }

    private Weighting createWeighting(FlagEncoder encoder, TurnCostExtension tcs, double uTurnCosts) {
        return new TurnWeighting(new FastestWeighting(encoder), tcs).setDefaultUTurnCost(uTurnCosts);
    }

    @Test
    public void testBasicTurnRestriction() {
        GraphHopperStorage g = createStorage(createEncodingManager(true));
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();
        initTurnRestrictions(g, tcs);
        Path p = createAlgo(g, AlgorithmOptions.start().
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR).build()).
                calcPath(5, 1);
        assertEquals(IntArrayList.from(5, 2, 3, 4, 7, 6, 3, 1), p.calcNodes());

        // test 7-6-5 and reverse
        p = createAlgo(g, AlgorithmOptions.start().
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR).build()).
                calcPath(5, 7);
        assertEquals(IntArrayList.from(5, 6, 7), p.calcNodes());

        p = createAlgo(g, AlgorithmOptions.start().
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR).build()).
                calcPath(7, 5);
        assertEquals(IntArrayList.from(7, 6, 3, 2, 5), p.calcNodes());
    }

    @Test
    public void testLoop_issue1592() {
        GraphHopperStorage g = createStorage(createEncodingManager(true));
        // 0-6
        //  \ \
        //   4-3
        //   |
        //   1o
        g.edge(0, 6, 10, true);
        g.edge(6, 3, 10, true);
        g.edge(0, 4, 1, true);
        g.edge(4, 1, 1, true);
        g.edge(4, 3, 1, true);
        g.edge(1, 1, 10, true);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();
        addTurnRestriction(g, tcs, 0, 4, 3);

        Path p = createAlgo(g, AlgorithmOptions.start().
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR).build()).
                calcPath(0, 3);
        assertEquals(14, p.getDistance(), 1.e-3);
        assertEquals(IntArrayList.from(0, 4, 1, 1, 4, 3), p.calcNodes());
    }

    @Test
    public void testTurnCosts_timeCalculation() {
        // 0 - 1 - 2 - 3 - 4
        GraphHopperStorage g = createStorage(createEncodingManager(false));
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();
        final int distance = 100;
        final int turnCosts = 2;
        g.edge(0, 1, distance, true);
        g.edge(1, 2, distance, true);
        g.edge(2, 3, distance, true);
        g.edge(3, 4, distance, true);
        addTurnCost(g, tcs, turnCosts, 1, 2, 3);

        AlgorithmOptions opts = AlgorithmOptions.start()
                .weighting(createWeighting(carEncoder, tcs, 40))
                .traversalMode(TraversalMode.EDGE_BASED_2DIR)
                .build();

        {
            // simple case where turn cost is encountered during forward search
            Path p14 = createAlgo(g, opts).calcPath(1, 4);
            assertDistTimeWeight(p14, 3, distance, 6, turnCosts);
            assertEquals(20, p14.getWeight(), 1.e-6);
            assertEquals(20000, p14.getTime());
        }

        {
            // this test is more involved for bidir algos: the turn costs have to be taken into account also at the
            // node where fwd and bwd searches meet
            Path p04 = createAlgo(g, opts).calcPath(0, 4);
            assertDistTimeWeight(p04, 4, distance, 6, turnCosts);
            assertEquals(26, p04.getWeight(), 1.e-6);
            assertEquals(26000, p04.getTime());
        }
    }

    private void assertDistTimeWeight(Path path, int numEdges, double distPerEdge, double weightPerEdge, int turnCost) {
        assertEquals("wrong distance", numEdges * distPerEdge, path.getDistance(), 1.e-6);
        assertEquals("wrong weight", numEdges * weightPerEdge + turnCost, path.getWeight(), 1.e-6);
        assertEquals("wrong time", 1000 * (numEdges * weightPerEdge + turnCost), path.getTime(), 1.e-6);
    }


    private void blockNode3(Graph g, TurnCostExtension tcs) {
        // Totally block this node (all 9 turn relations)
        addTurnRestriction(g, tcs, 2, 3, 1);
        addTurnRestriction(g, tcs, 2, 3, 4);
        addTurnRestriction(g, tcs, 4, 3, 1);
        addTurnRestriction(g, tcs, 4, 3, 2);
        addTurnRestriction(g, tcs, 6, 3, 1);
        addTurnRestriction(g, tcs, 6, 3, 4);
        addTurnRestriction(g, tcs, 1, 3, 6);
        addTurnRestriction(g, tcs, 1, 3, 2);
        addTurnRestriction(g, tcs, 1, 3, 4);
    }

    @Test
    public void testBlockANode() {
        GraphHopperStorage g = createStorage(createEncodingManager(true));
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();
        blockNode3(g, tcs);
        for (int i = 0; i <= 7; i++) {
            if (i == 3) continue;
            for (int j = 0; j <= 7; j++) {
                if (j == 3) continue;
                Path p = createAlgo(g, AlgorithmOptions.start().
                        weighting(createWeighting(carEncoder, tcs, 40)).
                        traversalMode(TraversalMode.EDGE_BASED_2DIR).build()).
                        calcPath(i, j);
                assertTrue(p.isFound()); // We can go from everywhere to everywhere else without using node 3
                for (IntCursor node : p.calcNodes()) {
                    assertNotEquals(p.calcNodes().toString(), 3, node.value);
                }
            }
        }
    }

    @Test
    public void testUTurns() {
        GraphHopperStorage g = createStorage(createEncodingManager(true));
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();

        // force u-turn via lowering the cost for it
        EdgeIteratorState e3_6 = getEdge(g, 3, 6);
        e3_6.setDistance(0.1);
        getEdge(g, 3, 2).setDistance(864);
        getEdge(g, 1, 0).setDistance(864);

        addTurnRestriction(g, tcs, 7, 6, 5);
        addTurnRestriction(g, tcs, 4, 3, 6);
        AlgorithmOptions opts = AlgorithmOptions.start().
                weighting(createWeighting(carEncoder, tcs, 50)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR_UTURN).build();
        Path p = createAlgo(g, opts).calcPath(7, 5);

        assertEquals(IntArrayList.from(7, 6, 3, 6, 5), p.calcNodes());

        // no u-turn for 6-3
        opts = AlgorithmOptions.start().
                weighting(createWeighting(carEncoder, tcs, 100)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR_UTURN).build();
        addTurnRestriction(g, tcs, 6, 3, 6);
        p = createAlgo(g, opts).calcPath(7, 5);

        assertEquals(IntArrayList.from(7, 6, 3, 2, 5), p.calcNodes());
    }

    @Test
    public void testBasicTurnCosts() {
        GraphHopperStorage g = createStorage(createEncodingManager(false));
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();
        Path p = createAlgo(g, AlgorithmOptions.start().
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR).build()).
                calcPath(5, 1);

        // no restriction and costs
        assertEquals(IntArrayList.from(5, 2, 3, 1), p.calcNodes());

        // now introduce some turn costs
        getEdge(g, 5, 6).setDistance(2);
        addTurnCost(g, tcs, 2, 5, 2, 3);

        p = createAlgo(g, AlgorithmOptions.start().
                weighting(createWeighting(carEncoder, tcs, 40)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR).build()).
                calcPath(5, 1);
        assertEquals(IntArrayList.from(5, 6, 3, 1), p.calcNodes());
    }

    @Test
    public void testTurnCostsBug_991() {
        final GraphHopperStorage g = createStorage(createEncodingManager(false));
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();

        addTurnCost(g, tcs, 2, 5, 2, 3);
        addTurnCost(g, tcs, 2, 2, 0, 1);
        addTurnCost(g, tcs, 2, 5, 6, 3);
        addTurnCost(g, tcs, 1, 6, 7, 4);

        Path p = createAlgo(g, AlgorithmOptions.start().
                weighting(new TurnWeighting(new FastestWeighting(carEncoder), tcs) {
                    @Override
                    public double calcTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
                        if (edgeFrom >= 0)
                            assertNotNull("edge " + edgeFrom + " to " + nodeVia + " does not exist", g.getEdgeIteratorState(edgeFrom, nodeVia));
                        if (edgeTo >= 0)
                            assertNotNull("edge " + edgeTo + " to " + nodeVia + " does not exist", g.getEdgeIteratorState(edgeTo, nodeVia));
                        return super.calcTurnWeight(edgeFrom, nodeVia, edgeTo);
                    }
                }.setDefaultUTurnCost(40)).
                traversalMode(TraversalMode.EDGE_BASED_2DIR).build()).
                calcPath(5, 1);
        assertEquals(IntArrayList.from(5, 6, 7, 4, 3, 1), p.calcNodes());
        assertEquals(5 * 0.06 + 1, p.getWeight(), 1.e-6);
        assertEquals(1300, p.getTime(), .1);
    }

    private void addTurnRestriction(Graph g, TurnCostExtension tcs, int from, int via, int to) {
        long turnFlags = carEncoder.getTurnFlags(true, 0);
        addTurnFlags(g, tcs, from, via, to, turnFlags);
    }

    private void addTurnCost(Graph g, TurnCostExtension tcs, int costs, int from, int via, int to) {
        long turnFlags = carEncoder.getTurnFlags(false, costs);
        addTurnFlags(g, tcs, from, via, to, turnFlags);
    }

    private void addTurnFlags(Graph g, TurnCostExtension tcs, int from, int via, int to, long turnFlags) {
        tcs.addTurnInfo(getEdge(g, from, via).getEdge(), via, getEdge(g, via, to).getEdge(), turnFlags);
    }

}
