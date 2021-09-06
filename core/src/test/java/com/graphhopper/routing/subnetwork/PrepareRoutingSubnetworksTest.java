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

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworksTest {
    private final FlagEncoder carFlagEncoder = new CarFlagEncoder();
    private final EncodingManager em = EncodingManager.create(carFlagEncoder);
    private final BooleanEncodedValue accessEnc = carFlagEncoder.getAccessEnc();

    private GraphHopperStorage createSubnetworkTestStorage(EncodingManager em) {
        GraphHopperStorage g = new GraphBuilder(em).create();
        //         5 - 6
        //         | /
        //         4
        //         | <- (no access flags unless we change it)
        // 0 - 1 - 3 - 7 - 8
        // |       |
        // 2 -------
        g.edge(0, 1, 1, true);
        g.edge(1, 3, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 7, 1, true);
        g.edge(7, 8, 1, true);
        // connecting both but do no set access yet
        g.edge(3, 4).setDistance(1);

        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(4, 6, 1, true);
        return g;
    }

    @Test
    public void testRemoveSubnetworkIfOnlyOneVehicle() {
        GraphHopperStorage g = createSubnetworkTestStorage(em);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, null)));
        // this rules out the upper small network
        instance.setMinNetworkSize(4);
        instance.doWork();
        g.optimize();
        assertEquals(6, g.getNodes());
        assertTrue(GHUtility.getProblems(g).isEmpty());
        EdgeExplorer explorer = g.createEdgeExplorer();
        assertEquals(GHUtility.asSet(2, 1, 5), GHUtility.getNeighbors(explorer.setBaseNode(3)));

        // this time we lower the threshold and the small network will remain
        g = createSubnetworkTestStorage(em);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, null)));
        instance.setMinNetworkSize(3);
        instance.doWork();
        g.optimize();
        assertEquals(9, g.getNodes());
    }

    @Test
    public void testRemoveSubnetworkIfOnlyOneVehicleEdgeBased() {
        EncodingManager encodingManager = EncodingManager.create("car|turn_costs=true");
        FlagEncoder encoder = encodingManager.fetchEdgeEncoders().iterator().next();
        GraphHopperStorage g = createSubnetworkTestStorage(encodingManager);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob(encoder.toString(), encoder.getAccessEnc(), new DefaultTurnCostProvider(encoder, g.getTurnCostStorage(), 0))));
        // this rules out the upper small network
        instance.setMinNetworkSize(4);
        instance.doWork();
        g.optimize();
        assertEquals(6, g.getNodes());
        assertTrue(GHUtility.getProblems(g).isEmpty());
        EdgeExplorer explorer = g.createEdgeExplorer();
        assertEquals(GHUtility.asSet(2, 1, 5), GHUtility.getNeighbors(explorer.setBaseNode(3)));

        // this time we lower the threshold and the small network will remain
        g = createSubnetworkTestStorage(em);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, null)));
        instance.setMinNetworkSize(3);
        instance.doWork();
        g.optimize();
        assertEquals(9, g.getNodes());
    }

    @Test
    public void testRemoveNode() {
        FlagEncoder carEncoder = new CarFlagEncoder();
        BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();
        EncodingManager em = EncodingManager.create(carEncoder, bikeEncoder);
        GraphHopperStorage g = createSubnetworkTestStorage(em);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Arrays.asList(
                new PrepareRoutingSubnetworks.PrepareJob(carEncoder.toString(), carEncoder.getAccessEnc(), null),
                new PrepareRoutingSubnetworks.PrepareJob(bikeEncoder.toString(), bikeEncoder.getAccessEnc(), null)
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
                edge.set(encoder.getAccessEnc(), false).setReverse(encoder.getAccessEnc(), false);
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
        GraphHopperStorage g = createSubnetworkTestStorage(em);

        EdgeIteratorState edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setProperties(edge, carEncoder, 10, false, false);
        GHUtility.setProperties(edge, bikeEncoder, 5, true, true);
        List<PrepareRoutingSubnetworks.PrepareJob> prepareJobs = Arrays.asList(
                new PrepareRoutingSubnetworks.PrepareJob(carEncoder.toString(), carEncoder.getAccessEnc(), null),
                new PrepareRoutingSubnetworks.PrepareJob(bikeEncoder.toString(), bikeEncoder.getAccessEnc(), null)
        );
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        instance.doWork();
        g.optimize();
        // remove nothing because of two vehicles with different subnetworks
        assertEquals(9, g.getNodes());

        EdgeExplorer carExplorer = g.createEdgeExplorer(DefaultEdgeFilter.allEdges(carEncoder));
        assertEquals(GHUtility.asSet(7, 2, 1), GHUtility.getNeighbors(carExplorer.setBaseNode(3)));
        EdgeExplorer bikeExplorer = g.createEdgeExplorer(DefaultEdgeFilter.allEdges(bikeEncoder));
        assertEquals(GHUtility.asSet(7, 2, 1, 4), GHUtility.getNeighbors(bikeExplorer.setBaseNode(3)));

        // now we block the edge for both vehicles, in which case the smaller subnetwork gets removed
        edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setProperties(edge, carEncoder, 10, false, false);
        GHUtility.setProperties(edge, bikeEncoder, 5, false, false);
        instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        instance.doWork();
        g.optimize();
        assertEquals(6, g.getNodes());
    }

    GraphHopperStorage createSubnetworkTestStorageWithOneWays(EncodingManager em) {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // 0 - 1 - 2 - 3 - 4 <- 5 - 6
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(5, 4, 1, false);
        g.edge(5, 6, 1, true);

        // 7 -> 8 - 9 - 10
        g.edge(7, 8, 1, false);
        g.edge(8, 9, 1, true);
        g.edge(9, 10, 1, true);

        return g;
    }

    @Test
    public void testRemoveSubNetworks_withOneWays() {
        GraphHopperStorage g = createSubnetworkTestStorageWithOneWays(em);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, null))).
                setMinNetworkSize(3);
        int removed = instance.removeSmallSubNetworks(accessEnc, null);

        // the (7) and the (5,6) components get removed -> 2 remaining components and
        // 3 removed edges total
        assertEquals(3, removed);
        instance.markNodesRemovedIfUnreachable();
        g.optimize();

        assertEquals(8, g.getNodes());
    }

    @Test
    public void testAddEdgesAfterwards() {
        GraphHopperStorage g = createSubnetworkTestStorageWithOneWays(em);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, null))).
                setMinNetworkSize(3);
        int removed = instance.removeSmallSubNetworks(accessEnc, null);

        assertEquals(3, removed);
        instance.markNodesRemovedIfUnreachable();
        g.optimize();

        assertEquals(8, g.getNodes());

        assertTrue(isConsistent(g));
        g.edge(7, 8);
        assertTrue(isConsistent(g));
    }

    // Previous two-pass implementation failed on 1 -> 2 -> 0
    @Test
    public void testNodeOrderingRegression() {
        // 1 -> 2 -> 0 - 3 - 4 - 5
        GraphHopperStorage g = new GraphBuilder(em).create();
        g.edge(1, 2, 1, false);
        g.edge(2, 0, 1, false);
        g.edge(0, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(4, 5, 1, true);

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                new PrepareRoutingSubnetworks.PrepareJob("car", accessEnc, null))).
                setMinNetworkSize(2);
        int removedEdges = instance.removeSmallSubNetworks(accessEnc, null);
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
