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
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks.PrepEdgeFilter;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworksTest {
    private final FlagEncoder carFlagEncoder = new CarFlagEncoder();
    private final EncodingManager em = EncodingManager.create(carFlagEncoder);

    GraphHopperStorage createStorage(EncodingManager eman) {
        return new GraphBuilder(eman).create();
    }

    GraphHopperStorage createSubnetworkTestStorage() {
        GraphHopperStorage g = createStorage(em);
        // big network
        g.edge(1, 2, 1, true);
        g.edge(1, 4, 1, false);
        g.edge(1, 8, 1, true);
        g.edge(2, 4, 1, true);
        g.edge(8, 4, 1, false);
        g.edge(8, 11, 1, true);
        g.edge(12, 11, 1, true);
        g.edge(9, 12, 1, false);
        g.edge(9, 15, 1, true);

        // large network
        g.edge(0, 13, 1, true);
        g.edge(0, 3, 1, true);
        g.edge(0, 7, 1, true);
        g.edge(3, 7, 1, true);
        g.edge(3, 5, 1, true);
        g.edge(13, 5, 1, true);

        // small network
        g.edge(6, 14, 1, true);
        g.edge(10, 14, 1, true);
        return g;
    }

    GraphHopperStorage createSubnetworkTestStorage2(EncodingManager em) {
        GraphHopperStorage g = createStorage(em);
        // large network
        g.edge(0, 1, 1, true);
        g.edge(1, 3, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 7, 1, true);
        g.edge(7, 8, 1, true);

        // connecting both but do not allow CAR!
        g.edge(3, 4).setDistance(1);

        // small network
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(4, 6, 1, true);
        return g;
    }

    @Test
    public void testFindSubnetworks() {
        GraphHopperStorage g = createSubnetworkTestStorage();
        PrepEdgeFilter filter = new PrepEdgeFilter(carFlagEncoder);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(carFlagEncoder));
        List<IntArrayList> components = instance.findSubnetworks(filter);

        assertEquals(3, components.size());

        // start is at 0 => large network
        assertEquals(IntArrayList.from(0, 7, 3, 13, 5), components.get(0));
        // next smallest and unvisited node is 1 => big network
        assertEquals(IntArrayList.from(1, 8, 4, 2, 11, 12, 9, 15), components.get(1));
        assertEquals(IntArrayList.from(6, 14, 10), components.get(2));
    }

    @Test
    public void testKeepLargestNetworks() {
        GraphHopperStorage g = createSubnetworkTestStorage();
        PrepEdgeFilter filter = new PrepEdgeFilter(carFlagEncoder);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(carFlagEncoder));
        List<IntArrayList> components = instance.findSubnetworks(filter);
        assertEquals(3, components.size());
        int removedEdges = instance.keepLargeNetworks(filter, components);
        assertEquals(8, removedEdges);
        instance.markNodesRemovedIfUnreachable();
        g.optimize();

        assertEquals(8, g.getNodes());
        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(g));

        components = instance.findSubnetworks(filter);
        assertEquals(1, components.size());
    }

    @Test
    public void testRemoveSubnetworkIfOnlyOneVehicle() {
        GraphHopperStorage g = createSubnetworkTestStorage2(em);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em.fetchEdgeEncoders());
        instance.setMinNetworkSize(4);
        instance.doWork();
        g.optimize();
        assertEquals(6, g.getNodes());
        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(g));
        EdgeExplorer explorer = g.createEdgeExplorer();
        assertEquals(GHUtility.asSet(2, 1, 5), GHUtility.getNeighbors(explorer.setBaseNode(3)));

        // do not remove because small network is big enough
        g = createSubnetworkTestStorage2(em);
        instance = new PrepareRoutingSubnetworks(g, em.fetchEdgeEncoders());
        instance.setMinNetworkSize(3);
        instance.doWork();
        g.optimize();
        assertEquals(9, g.getNodes());
    }

    @Test
    public void testRemoveNode() {
        FlagEncoder carEncoder = new CarFlagEncoder();
        BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();
        EncodingManager em2 = EncodingManager.create(carEncoder, bikeEncoder);
        GraphHopperStorage g = createSubnetworkTestStorage2(em2);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em2.fetchEdgeEncoders());

        EdgeExplorer edgeExplorer = g.createEdgeExplorer();
        assertFalse(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 4));
        assertFalse(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 5));
        assertFalse(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 6));

        // mark certain edges inaccessible for all encoders
        for (EdgeIteratorState edge : Arrays.asList(GHUtility.getEdge(g, 5, 6), GHUtility.getEdge(g, 4, 5), GHUtility.getEdge(g, 4, 6))) {
            for (FlagEncoder encoder : em2.fetchEdgeEncoders()) {
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
        EncodingManager em2 = EncodingManager.create(carEncoder, bikeEncoder);
        GraphHopperStorage g = createSubnetworkTestStorage2(em2);

        EdgeIteratorState edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setProperties(edge, carEncoder, 10, false, false);
        GHUtility.setProperties(edge, bikeEncoder, 5, true, true);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em2.fetchEdgeEncoders());
        instance.setMinNetworkSize(5);
        instance.doWork();
        g.optimize();
        // remove nothing because of two vehicles with different subnetworks
        assertEquals(9, g.getNodes());

        EdgeExplorer carExplorer = g.createEdgeExplorer(DefaultEdgeFilter.allEdges(carEncoder));
        assertEquals(GHUtility.asSet(7, 2, 1), GHUtility.getNeighbors(carExplorer.setBaseNode(3)));
        EdgeExplorer bikeExplorer = g.createEdgeExplorer(DefaultEdgeFilter.allEdges(bikeEncoder));
        assertEquals(GHUtility.asSet(7, 2, 1, 4), GHUtility.getNeighbors(bikeExplorer.setBaseNode(3)));

        edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setProperties(edge, carEncoder, 10, false, false);
        GHUtility.setProperties(edge, bikeEncoder, 5, false, false);
        instance = new PrepareRoutingSubnetworks(g, em2.fetchEdgeEncoders());
        instance.setMinNetworkSize(5);
        instance.doWork();
        g.optimize();
        assertEquals(6, g.getNodes());
    }

    GraphHopperStorage createDeadEndUnvisitedNetworkStorage(EncodingManager em) {
        GraphHopperStorage g = createStorage(em);
        // 0 <-> 1 <-> 2 <-> 3 <-> 4 <- 5 <-> 6
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(5, 4, 1, false);
        g.edge(5, 6, 1, true);

        // 7 -> 8 <-> 9 <-> 10
        g.edge(7, 8, 1, false);
        g.edge(8, 9, 1, true);
        g.edge(9, 10, 1, true);

        return g;
    }

    GraphHopperStorage createTarjanTestStorage() {
        GraphHopperStorage g = createStorage(em);

        g.edge(1, 2, 1, false);
        g.edge(2, 3, 1, false);
        g.edge(3, 1, 1, false);

        g.edge(4, 2, 1, false);
        g.edge(4, 3, 1, false);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, false);

        g.edge(6, 3, 1, false);
        g.edge(6, 7, 1, true);

        g.edge(8, 5, 1, false);
        g.edge(8, 7, 1, false);
        g.edge(8, 8, 1, false);

        return g;
    }

    @Test
    public void testRemoveDeadEndUnvisitedNetworks() {
        GraphHopperStorage g = createDeadEndUnvisitedNetworkStorage(em);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(carFlagEncoder)).
                setMinOneWayNetworkSize(3);
        int removed = instance.removeDeadEndUnvisitedNetworks(new PrepEdgeFilter(carFlagEncoder));

        assertEquals(3, removed);
        instance.markNodesRemovedIfUnreachable();
        g.optimize();

        assertEquals(8, g.getNodes());
    }

    @Test
    public void testAddEdgesAfterwards() {
        GraphHopperStorage g = createDeadEndUnvisitedNetworkStorage(em);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(carFlagEncoder)).
                setMinOneWayNetworkSize(3);
        int removed = instance.removeDeadEndUnvisitedNetworks(new PrepEdgeFilter(carFlagEncoder));

        assertEquals(3, removed);
        instance.markNodesRemovedIfUnreachable();
        g.optimize();

        assertEquals(8, g.getNodes());

        assertTrue(isConsistent(g));
        g.edge(7, 8);
        assertTrue(isConsistent(g));
    }

    @Test
    public void testTarjan() {
        GraphHopperStorage g = createSubnetworkTestStorage();

        // Requires a single vehicle type, otherwise we throw.
        final EdgeFilter filter = DefaultEdgeFilter.outEdges(carFlagEncoder);
        TarjansSCCAlgorithm tarjan = new TarjansSCCAlgorithm(g, filter, false);

        List<IntArrayList> components = tarjan.findComponents();

        assertEquals(4, components.size());
        assertEquals(IntArrayList.from(13, 5, 3, 7, 0), components.get(0));
        assertEquals(IntArrayList.from(2, 4, 12, 11, 8, 1), components.get(1));
        assertEquals(IntArrayList.from(10, 14, 6), components.get(2));
        assertEquals(IntArrayList.from(15, 9), components.get(3));
    }

    // Previous two-pass implementation failed on 1 -> 2 -> 0
    @Test
    public void testNodeOrderingRegression() {
        // 1 -> 2 -> 0
        GraphHopperStorage g = createStorage(em);
        g.edge(1, 2, 1, false);
        g.edge(2, 0, 1, false);

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(carFlagEncoder)).
                setMinOneWayNetworkSize(2);
        int removedEdges = instance.removeDeadEndUnvisitedNetworks(new PrepEdgeFilter(carFlagEncoder));
        assertEquals(2, removedEdges);
    }

    @Test
    public void test481() {
        // 0->1->3->4->5->6
        //  2        7<--/
        GraphHopperStorage g = createStorage(em);
        g.edge(0, 1, 1, false);
        g.edge(1, 2, 1, false);
        g.edge(2, 0, 1, false);

        g.edge(1, 3, 1, false);
        g.edge(3, 4, 1, false);

        g.edge(4, 5, 1, false);
        g.edge(5, 6, 1, false);
        g.edge(6, 7, 1, false);
        g.edge(7, 4, 1, false);

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(carFlagEncoder)).
                setMinOneWayNetworkSize(2).
                setMinNetworkSize(4);
        instance.doWork();

        // only one remaining network
        List<IntArrayList> components = instance.findSubnetworks(new PrepEdgeFilter(carFlagEncoder));
        assertEquals(1, components.size());
    }

    public static boolean isConsistent(GraphHopperStorage storage) {
        EdgeExplorer edgeExplorer = storage.createEdgeExplorer();
        int nNodes = storage.getNodes();
        for (int i = 0; i < nNodes; i++) {
            if (!check(storage, edgeExplorer, i)) return false;
        }
        return true;
    }

    public static boolean check(GraphHopperStorage storage, EdgeExplorer edgeExplorer, int node) {
        List<Integer> toNodes = new ArrayList<>();
        List<Integer> edges = new ArrayList<>();
        EdgeIterator edgeIterator = edgeExplorer.setBaseNode(node);
        while (edgeIterator.next()) {
            if (edgeIterator.getBaseNode() < 0 || edgeIterator.getAdjNode() < 0) {
                return false;
            }
            toNodes.add(edgeIterator.getAdjNode());
            edges.add(edgeIterator.getEdge());
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
