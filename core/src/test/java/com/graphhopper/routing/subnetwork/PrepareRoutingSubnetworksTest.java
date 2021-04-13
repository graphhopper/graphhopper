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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworksTest {
    private final FlagEncoder encoder = new CarFlagEncoder();
    private final EncodingManager em = EncodingManager.create(encoder);
    private final BooleanEncodedValue accessEnc = encoder.getAccessEnc();

    private static GraphHopperStorage createSubnetworkTestStorage(EncodingManager em, FlagEncoder encoder) {
        GraphHopperStorage g = new GraphBuilder(em).create();
        //         5 - 6
        //         | /
        //         4
        //         | <- (no access flags unless we change it)
        // 0 - 1 - 3 - 7 - 8
        // |       |
        // 2 -------
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(3, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(7, 8).setDistance(1));
        // connecting both but do no set access yet
        g.edge(3, 4).setDistance(1);

        GHUtility.setSpeed(60, true, true, encoder, g.edge(4, 5).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(4, 6).setDistance(1));
        return g;
    }

    @Test
    public void testRemoveSubnetworkIfOnlyOneVehicle() {
        FlagEncoder encoder = em.fetchEdgeEncoders().iterator().next();
        GraphHopperStorage g = createSubnetworkTestStorage(em, encoder);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, NO_TURN_COST_PROVIDER)));
        // this rules out the upper small network
        instance.setMinNetworkSize(4);
        instance.doWork();
        assertTrue(GHUtility.getProblems(g).isEmpty());
        EdgeExplorer explorer = g.createEdgeExplorer(DefaultEdgeFilter.allEdges(encoder.getAccessEnc()));
        assertEquals(GHUtility.asSet(), GHUtility.getNeighbors(explorer.setBaseNode(4)));

        // this time we lower the threshold and the small network will remain
        g = createSubnetworkTestStorage(em, encoder);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, NO_TURN_COST_PROVIDER)));
        instance.setMinNetworkSize(3);
        instance.doWork();
        explorer = g.createEdgeExplorer(DefaultEdgeFilter.allEdges(encoder.getAccessEnc()));
        assertEquals(GHUtility.asSet(5, 6), GHUtility.getNeighbors(explorer.setBaseNode(4)));
    }

    @Test
    public void testRemoveSubnetworkIfOnlyOneVehicleEdgeBased() {
        EncodingManager encodingManager = EncodingManager.create("car|turn_costs=true");
        FlagEncoder encoder = encodingManager.fetchEdgeEncoders().iterator().next();
        GraphHopperStorage g = createSubnetworkTestStorage(encodingManager, encoder);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob(encoder.toString(), encoder.getAccessEnc(), new DefaultTurnCostProvider(encoder, g.getTurnCostStorage(), 0))));
        // this rules out the upper small network
        instance.setMinNetworkSize(4);
        instance.doWork();
        assertTrue(GHUtility.getProblems(g).isEmpty());
        EdgeExplorer explorer = g.createEdgeExplorer(DefaultEdgeFilter.allEdges(encoder.getAccessEnc()));
        assertEquals(GHUtility.asSet(), GHUtility.getNeighbors(explorer.setBaseNode(4)));

        // this time we lower the threshold and the small network will remain
        g = createSubnetworkTestStorage(em, encoder);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, NO_TURN_COST_PROVIDER)));
        instance.setMinNetworkSize(3);
        instance.doWork();
        explorer = g.createEdgeExplorer(DefaultEdgeFilter.allEdges(encoder.getAccessEnc()));
        assertEquals(GHUtility.asSet(5, 6), GHUtility.getNeighbors(explorer.setBaseNode(4)));
    }

    @Test
    public void testRemoveNode() {
        FlagEncoder carEncoder = new CarFlagEncoder();
        BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();
        EncodingManager em = EncodingManager.create(carEncoder, bikeEncoder);
        GraphHopperStorage g = createSubnetworkTestStorage(em, encoder);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Arrays.asList(
                new PrepareRoutingSubnetworks.PrepareJob(carEncoder.toString(), carEncoder.getAccessEnc(), NO_TURN_COST_PROVIDER),
                new PrepareRoutingSubnetworks.PrepareJob(bikeEncoder.toString(), bikeEncoder.getAccessEnc(), NO_TURN_COST_PROVIDER)
        ));

        EdgeExplorer edgeExplorer = g.createEdgeExplorer();
        assertFalse(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 4));
        assertFalse(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 5));
        assertFalse(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 6));

        // mark certain edges inaccessible for all encoders
        for (EdgeIteratorState edge : Arrays.asList(
                GHUtility.getEdge(g, 5, 6),
                GHUtility.getEdge(g, 4, 5),
                GHUtility.getEdge(g, 4, 6))
        ) {
            for (FlagEncoder encoder : em.fetchEdgeEncoders()) {
                edge.set(encoder.getAccessEnc(), false, false);
            }
        }

        assertTrue(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 4));
        assertTrue(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 5));
        assertTrue(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 6));
    }

    @Test
    public void testRemoveSubnetworkWhenMultipleVehicles() {
        FlagEncoder carEncoder = new CarFlagEncoder();
        BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();
        EncodingManager em = EncodingManager.create(carEncoder, bikeEncoder);
        GraphHopperStorage g = createSubnetworkTestStorage(em, carEncoder);
        AllEdgesIterator allIter = g.getAllEdges();
        while (allIter.next()) {
            GHUtility.setSpeed(bikeEncoder.getMaxSpeed() / 2, true, true, bikeEncoder, allIter);
        }

        EdgeIteratorState edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, false, false, carEncoder, edge);
        GHUtility.setSpeed(5, true, true, bikeEncoder, edge);
        List<PrepareRoutingSubnetworks.PrepareJob> prepareJobs = Arrays.asList(
                new PrepareRoutingSubnetworks.PrepareJob(carEncoder.toString(), carEncoder.getAccessEnc(), NO_TURN_COST_PROVIDER),
                new PrepareRoutingSubnetworks.PrepareJob(bikeEncoder.toString(), bikeEncoder.getAccessEnc(), NO_TURN_COST_PROVIDER)
        );
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        instance.doWork();

        EdgeExplorer carExplorer = g.createEdgeExplorer(DefaultEdgeFilter.allEdges(carEncoder.getAccessEnc()));
        assertEquals(GHUtility.asSet(7, 2, 1), GHUtility.getNeighbors(carExplorer.setBaseNode(3)));
        EdgeExplorer bikeExplorer = g.createEdgeExplorer(DefaultEdgeFilter.allEdges(((FlagEncoder) bikeEncoder).getAccessEnc()));
        assertEquals(GHUtility.asSet(7, 2, 1, 4), GHUtility.getNeighbors(bikeExplorer.setBaseNode(3)));

        // now we block the edge for both vehicles, in which case the smaller subnetwork gets removed
        edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, false, false, carEncoder, edge);
        GHUtility.setSpeed(5, false, false, bikeEncoder, edge);
        instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        instance.doWork();
    }

    GraphHopperStorage createSubnetworkTestStorageWithOneWays(EncodingManager em) {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // 0 - 1 - 2 - 3 - 4 <- 5 - 6
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(5, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(5, 6).setDistance(1));

        // 7 -> 8 - 9 - 10
        GHUtility.setSpeed(60, true, false, encoder, g.edge(7, 8).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(8, 9).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(9, 10).setDistance(1));

        return g;
    }

    @Test
    public void testRemoveSubNetworks_withOneWays() {
        GraphHopperStorage g = createSubnetworkTestStorageWithOneWays(em);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks.PrepareJob job = new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, NO_TURN_COST_PROVIDER);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(3);
        int removed = instance.removeSmallSubNetworks(job);

        // the (7) and the (5,6) components get removed
        // -> 2 remaining components and 6 removed edges plus the 2 connecting oneway edges
        assertEquals(8, removed);
    }

    @Test
    public void testAddEdgesAfterwards() {
        GraphHopperStorage g = createSubnetworkTestStorageWithOneWays(em);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks.PrepareJob job = new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, NO_TURN_COST_PROVIDER);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(3);
        int removed = instance.removeSmallSubNetworks(job);

        assertEquals(8, removed);
        assertTrue(isConsistent(g));
        g.edge(7, 8);
        assertTrue(isConsistent(g));
    }

    // Previous two-pass implementation failed on 1 -> 2 -> 0
    @Test
    public void testNodeOrderingRegression() {
        // 1 -> 2 -> 0 - 3 - 4 - 5
        GraphHopperStorage g = new GraphBuilder(em).create();
        GHUtility.setSpeed(60, true, false, encoder, g.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(2, 0).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(4, 5).setDistance(1));

        PrepareRoutingSubnetworks.PrepareJob job = new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, NO_TURN_COST_PROVIDER);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(2);
        int removedEdges = instance.removeSmallSubNetworks(job);
        assertEquals(2, removedEdges);
    }

    public static boolean isConsistent(GraphHopperStorage storage) {
        EdgeExplorer edgeExplorer = storage.createEdgeExplorer();
        for (int i = 0; i < storage.getNodes(); i++) {
            if (!check(storage, edgeExplorer, i)) return false;
        }
        return true;
    }

    public static boolean check(GraphHopperStorage storage, EdgeExplorer edgeExplorer, int node) {
        List<Integer> toNodes = new ArrayList<>();
        List<Integer> edges = new ArrayList<>();
        EdgeIterator iter = edgeExplorer.setBaseNode(node);
        while (iter.next()) {
            if (iter.getBaseNode() < 0 || iter.getAdjNode() < 0) {
                return false;
            }
            toNodes.add(iter.getAdjNode());
            edges.add(iter.getEdge());
        }

        for (int i = 0; i < toNodes.size(); i++) {
            EdgeIteratorState edgeIteratorState = storage.getEdgeIteratorState(edges.get(i), toNodes.get(i));
            if (edgeIteratorState == null) {
                return false;
            }
            EdgeIteratorState edgeIteratorState2 = storage.getEdgeIteratorState(edges.get(i), node);
            if (edgeIteratorState2 == null) {
                return false;
            }
        }
        return true;
    }

}
