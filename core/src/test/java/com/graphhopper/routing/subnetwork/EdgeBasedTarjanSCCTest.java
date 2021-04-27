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

package com.graphhopper.routing.subnetwork;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.routing.subnetwork.EdgeBasedTarjanSCC.ConnectedComponents;
import com.graphhopper.routing.subnetwork.TarjanSCCTest.IntWithArray;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;

import static com.graphhopper.routing.subnetwork.TarjanSCCTest.buildComponentSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeBasedTarjanSCCTest {
    private final FlagEncoder encoder = new CarFlagEncoder(5, 5, 1);
    private final EncodingManager em = EncodingManager.create(encoder);
    private final EdgeBasedTarjanSCC.EdgeTransitionFilter fwdAccessFilter = (prev, edge) -> edge.get(encoder.getAccessEnc());

    @Test
    public void linearSingle() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // 0 - 1
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 1).setDistance(1));
        ConnectedComponents result = EdgeBasedTarjanSCC.findComponentsRecursive(g, fwdAccessFilter, false);
        assertEquals(2, result.getEdgeKeys());
        assertEquals(1, result.getTotalComponents());
        assertEquals(1, result.getComponents().size());
        assertTrue(result.getSingleEdgeComponents().isEmpty());
        assertEquals(result.getComponents().get(0), result.getBiggestComponent());
        assertEquals(IntArrayList.from(1, 0), result.getComponents().get(0));
    }

    @Test
    public void linearSimple() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // 0 - 1 - 2
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 2).setDistance(1));
        ConnectedComponents result = EdgeBasedTarjanSCC.findComponentsRecursive(g, fwdAccessFilter, false);
        assertEquals(4, result.getEdgeKeys());
        assertEquals(1, result.getTotalComponents());
        assertEquals(1, result.getComponents().size());
        assertTrue(result.getSingleEdgeComponents().isEmpty());
        assertEquals(result.getComponents().get(0), result.getBiggestComponent());
        assertEquals(IntArrayList.from(1, 3, 2, 0), result.getComponents().get(0));
    }

    @Test
    public void linearOneWay() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // 0 -> 1 -> 2
        GHUtility.setSpeed(60, true, false, encoder, g.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(1, 2).setDistance(1));
        ConnectedComponents result = EdgeBasedTarjanSCC.findComponentsRecursive(g, fwdAccessFilter, false);
        assertEquals(4, result.getEdgeKeys());
        assertEquals(4, result.getTotalComponents());
        assertEquals(0, result.getComponents().size());
        // we only have two directed edges here, but we always calculate the component indices for all edge keys and
        // here every (directed) edge belongs to its own component
        assertEquals(4, result.getSingleEdgeComponents().cardinality());
        assertEquals(IntArrayList.from(), result.getBiggestComponent());
    }

    @Test
    public void linearBidirectionalEdge() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // 0 -> 1 - 2 <- 3
        GHUtility.setSpeed(60, true, false, encoder, g.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(3, 2).setDistance(1));
        ConnectedComponents result = EdgeBasedTarjanSCC.findComponentsRecursive(g, fwdAccessFilter, false);
        assertEquals(6, result.getEdgeKeys());
        assertEquals(5, result.getTotalComponents());
        // a single bidirectional edge is treated as a 'real' component with two edge-keys. this is not nearly as
        // common as the single-edge-key components so no real need to do a special treatment for these as well.
        assertEquals(1, result.getComponents().size());
        assertEquals(4, result.getSingleEdgeComponents().cardinality());
        assertEquals(result.getComponents().get(0), result.getBiggestComponent());
    }

    @Test
    public void oneWayBridges() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // 0 - 1 -> 2 - 3
        //          |   |
        //          4 - 5 -> 6 - 7
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(2, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(3, 5).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(4, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(6, 7).setDistance(1));
        ConnectedComponents result = EdgeBasedTarjanSCC.findComponentsRecursive(g, fwdAccessFilter, false);
        assertEquals(16, result.getEdgeKeys());
        assertEquals(7, result.getTotalComponents());
        // 0-1, 2-3-5-4-2 and 6-7
        assertEquals(3, result.getComponents().size());
        // 1->2, 2->1 and 5->6, 6<-5
        assertEquals(4, result.getSingleEdgeComponents().cardinality());
        assertEquals(result.getComponents().get(1), result.getBiggestComponent());
    }

    @Test
    public void tree() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // 0 - 1 - 2 - 4 - 5
        //     |    \- 6 - 7
        //     3        \- 8
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(2, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(2, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(4, 5).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(6, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(6, 8).setDistance(1));
        ConnectedComponents result = EdgeBasedTarjanSCC.findComponentsRecursive(g, fwdAccessFilter, false);
        assertEquals(16, result.getEdgeKeys());
        assertEquals(1, result.getTotalComponents());
        assertEquals(1, result.getComponents().size());
        assertTrue(result.getSingleEdgeComponents().isEmpty());
        assertEquals(result.getComponents().get(0), result.getBiggestComponent());
        assertEquals(IntArrayList.from(1, 3, 7, 11, 10, 6, 9, 13, 12, 15, 14, 8, 2, 5, 4, 0), result.getComponents().get(0));
    }

    @Test
    public void smallGraphWithLoops() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // 3<-0->2-1o
        //    o
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 0).setDistance(1));// edge-keys 0,1
        GHUtility.setSpeed(60, true, false, encoder, g.edge(0, 2).setDistance(1)); // edge-keys 2,3
        GHUtility.setSpeed(60, true, false, encoder, g.edge(0, 3).setDistance(1)); // edge-keys 4,5
        GHUtility.setSpeed(60, true, true, encoder, g.edge(2, 1).setDistance(1)); // edge-keys 6,7
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 1).setDistance(1)); // edge-keys 8,9
        ConnectedComponents result = EdgeBasedTarjanSCC.findComponentsRecursive(g, fwdAccessFilter, false);
        assertEquals(10, result.getEdgeKeys());
        assertEquals(6, result.getTotalComponents());
        assertEquals(2, result.getComponents().size());
        assertEquals(result.getComponents().get(0), result.getBiggestComponent());
        assertEquals(IntArrayList.from(7, 9, 8, 6), result.getComponents().get(0));
        assertEquals(IntArrayList.from(1, 0), result.getComponents().get(1));
        assertEquals(4, result.getSingleEdgeComponents().cardinality());
        for (IntCursor c : IntArrayList.from(2, 3, 4, 5)) {
            assertTrue(result.getSingleEdgeComponents().get(c.value));
        }
    }

    @Test
    public void biggerGraph() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // this graph has two bigger components (nodes 0, 1, 3 and the others). Still there are some (directed) edges
        // that do not belong to these components but rather represent isolated single-edge components
        // 0 - 1 < 2 - 4 > 5
        //     |   |       |
        //     |    \< 6 - 7
        //     3        \- 8
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 1).setDistance(1)); // edge-keys 0,1
        GHUtility.setSpeed(60, true, false, encoder, g.edge(2, 1).setDistance(1)); // edge-keys 2,3
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 3).setDistance(1)); // edge-keys 4,5
        GHUtility.setSpeed(60, true, true, encoder, g.edge(2, 4).setDistance(1)); // edge-keys 6,7
        GHUtility.setSpeed(60, true, false, encoder, g.edge(6, 2).setDistance(1)); // edge-keys 8,9
        GHUtility.setSpeed(60, true, false, encoder, g.edge(4, 5).setDistance(1)); // edge-keys 10,11
        GHUtility.setSpeed(60, true, true, encoder, g.edge(5, 7).setDistance(1)); // edge-keys 12,13
        GHUtility.setSpeed(60, true, true, encoder, g.edge(6, 7).setDistance(1)); // edge-keys 14,15
        GHUtility.setSpeed(60, true, true, encoder, g.edge(6, 8).setDistance(1)); // edge-keys 16,17
        ConnectedComponents result = EdgeBasedTarjanSCC.findComponentsRecursive(g, fwdAccessFilter, false);
        assertEquals(18, result.getEdgeKeys());
        assertEquals(6, result.getTotalComponents());
        assertEquals(2, result.getComponents().size());
        assertEquals(result.getComponents().get(1), result.getBiggestComponent());
        assertEquals(IntArrayList.from(1, 5, 4, 0), result.getComponents().get(0));
        assertEquals(IntArrayList.from(7, 8, 13, 14, 17, 16, 15, 12, 10, 6), result.getComponents().get(1));
        assertEquals(4, result.getSingleEdgeComponents().cardinality());
        for (IntCursor c : IntArrayList.from(9, 2, 3, 11)) {
            assertTrue(result.getSingleEdgeComponents().get(c.value));
        }
    }

    @Test
    public void withTurnRestriction() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // here 0-1-2-3 would be a circle and thus belong to same connected component. but if there is a
        // turn restriction for going 0->2->3 this splits the graph into multiple components
        // 0->1
        // |  |
        // 3<-2->4
        GHUtility.setSpeed(60, true, false, encoder, g.edge(0, 1).setDistance(1)); // edge-keys 0,1
        GHUtility.setSpeed(60, true, false, encoder, g.edge(1, 2).setDistance(1)); // edge-keys 2,3
        GHUtility.setSpeed(60, true, false, encoder, g.edge(2, 3).setDistance(1)); // edge-keys 4,5
        GHUtility.setSpeed(60, true, false, encoder, g.edge(3, 0).setDistance(1)); // edge-keys 6,7
        GHUtility.setSpeed(60, true, false, encoder, g.edge(2, 4).setDistance(1)); // edge-keys 8,9

        // first lets check what happens without turn costs
        ConnectedComponents result = EdgeBasedTarjanSCC.findComponentsRecursive(g, fwdAccessFilter, false);
        assertEquals(7, result.getTotalComponents());
        assertEquals(1, result.getComponents().size());
        assertEquals(IntArrayList.from(6, 4, 2, 0), result.getBiggestComponent());
        assertEquals(6, result.getSingleEdgeComponents().cardinality());
        for (IntCursor c : IntArrayList.from(1, 3, 5, 7, 8, 9)) {
            assertTrue(result.getSingleEdgeComponents().get(c.value));
        }

        // now lets try with a restricted turn
        result = EdgeBasedTarjanSCC.findComponentsRecursive(g,
                (prev, edge) -> fwdAccessFilter.accept(prev, edge) && !(prev == 1 && edge.getBaseNode() == 2 && edge.getEdge() == 2), false);
        // none of the edges are strongly connected anymore!
        assertEquals(10, result.getTotalComponents());
        assertEquals(0, result.getComponents().size());
        assertEquals(IntArrayList.from(), result.getBiggestComponent());
        assertEquals(10, result.getSingleEdgeComponents().cardinality());
        for (IntCursor c : IntArrayList.from(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)) {
            assertTrue(result.getSingleEdgeComponents().get(c.value));
        }
    }

    @RepeatedTest(20)
    public void implicitVsExplicitRecursion() {
        doImplicitVsExplicit(true);
        doImplicitVsExplicit(false);
    }

    private void doImplicitVsExplicit(boolean excludeSingle) {
        GraphHopperStorage g = new GraphBuilder(em).create();
        long seed = System.nanoTime();
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(g, rnd, 500, 2, true, true,
                encoder.getAccessEnc(), encoder.getAverageSpeedEnc(), 60d, 0.8, 0.7, 0);
        ConnectedComponents implicit = EdgeBasedTarjanSCC.findComponentsRecursive(g, fwdAccessFilter, excludeSingle);
        ConnectedComponents explicit = EdgeBasedTarjanSCC.findComponents(g, fwdAccessFilter, excludeSingle);
        assertEquals(2 * g.getEdges(), implicit.getEdgeKeys(), "total number of edge keys in connected components should equal twice the number of edges in graph");
        assertEquals(2 * g.getEdges(), explicit.getEdgeKeys(), "total number of edge keys in connected components should equal twice the number of edges in graph");

        compareResults(g, seed, implicit, explicit);
    }

    @RepeatedTest(20)
    public void withStartEdges() {
        // we test the case where we specify all start edges (in this case the behavior should be the same for both methods)
        GraphHopperStorage g = new GraphBuilder(em).create();
        long seed = System.nanoTime();
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(g, rnd, 500, 2, true, true,
                encoder.getAccessEnc(), encoder.getAverageSpeedEnc(), 60d, 0.8, 0.7, 0);
        ConnectedComponents components = EdgeBasedTarjanSCC.findComponents(g, fwdAccessFilter, true);
        IntArrayList edges = new IntArrayList();
        AllEdgesIterator iter = g.getAllEdges();
        while (iter.next())
            edges.add(iter.getEdge());
        ConnectedComponents componentsForStartEdges = EdgeBasedTarjanSCC.findComponentsForStartEdges(g, fwdAccessFilter, edges);
        compareResults(g, seed, components, componentsForStartEdges);
    }

    private void compareResults(GraphHopperStorage g, long seed, ConnectedComponents expected, ConnectedComponents given) {
        assertEquals(expected.getEdgeKeys(), given.getEdgeKeys());
        // Unfortunately the results are not always expected to be identical because the edges are traversed in reversed
        // order for the explicit stack version. To make sure the components are the same we need to check for every
        // edge key that the component it is contained in is the same for both cases
        Set<IntWithArray> componentsImplicit = buildComponentSet(expected.getComponents());
        Set<IntWithArray> componentsExplicit = buildComponentSet(given.getComponents());
        if (!componentsExplicit.equals(componentsImplicit)) {
            System.out.println("seed: " + seed);
            GHUtility.printGraphForUnitTest(g, encoder);
            assertEquals(componentsExplicit, componentsImplicit, "Components for this graph are not the same for the two implementations");
        }

        if (!expected.getSingleEdgeComponents().equals(given.getSingleEdgeComponents())) {
            System.out.println("seed: " + seed);
            GHUtility.printGraphForUnitTest(g, encoder);
            assertEquals(expected.getSingleEdgeComponents(), given.getSingleEdgeComponents());
        }

        assertEquals(expected.getBiggestComponent(), given.getBiggestComponent(), "seed: " + seed);
        assertEquals(expected.getEdgeKeys(), given.getEdgeKeys(), "seed: " + seed);
        assertEquals(expected.getTotalComponents(), given.getTotalComponents(), "seed: " + seed);
    }
}
