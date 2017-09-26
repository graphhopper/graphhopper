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
import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks.PrepEdgeFilter;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworksTest {
    protected GHJson json = new GHJsonFactory().create();
    private final FlagEncoder carFlagEncoder = new CarFlagEncoder();
    private final EncodingManager em = new EncodingManager.Builder().addGlobalEncodedValues().addAll(carFlagEncoder).build();
    private final BooleanEncodedValue accessEnc = em.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS);
    private final DecimalEncodedValue avSpeedEnc = em.getDecimalEncodedValue(TagParserFactory.CAR_AVERAGE_SPEED);

    GraphHopperStorage createStorage(EncodingManager eman) {
        return new GraphBuilder(eman, json).create();
    }

    GraphHopperStorage createSubnetworkTestStorage() {
        GraphHopperStorage g = createStorage(em);
        // big network
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 2, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 4, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 8, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 2, 4, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 8, 4, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 8, 11, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 12, 11, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 9, 12, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 9, 15, true, 1);

        // large network
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 13, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 3, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 7, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 3, 7, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 3, 5, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 13, 5, true, 1);

        // small network
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 6, 14, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 10, 14, true, 1);
        return g;
    }

    GraphHopperStorage createSubnetworkTestStorage2(EncodingManager em, BooleanEncodedValue accessEnc) {
        GraphHopperStorage g = createStorage(em);
        // large network
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 1, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 3, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 2, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 2, 3, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 3, 7, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 7, 8, true, 1);

        // connecting both but do not allow CAR!
        g.edge(3, 4).setDistance(1);

        // small network
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 4, 5, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 5, 6, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 4, 6, true, 1);
        return g;
    }

    @Test
    public void testFindSubnetworks() {
        GraphHopperStorage g = createSubnetworkTestStorage();
        PrepEdgeFilter filter = new PrepEdgeFilter(accessEnc);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(carFlagEncoder));
        List<IntArrayList> components = instance.findSubnetworks(filter);

        assertEquals(3, components.size());

        // start is at 0 => large network
        assertEquals(Helper.createTList(0, 7, 3, 13, 5), components.get(0));
        // next smallest and unvisited node is 1 => big network
        assertEquals(Helper.createTList(1, 8, 4, 2, 11, 12, 9, 15), components.get(1));
        assertEquals(Helper.createTList(6, 14, 10), components.get(2));
    }

    @Test
    public void testKeepLargestNetworks() {
        GraphHopperStorage g = createSubnetworkTestStorage();
        PrepEdgeFilter filter = new PrepEdgeFilter(accessEnc);
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
        GraphHopperStorage g = createSubnetworkTestStorage2(em, accessEnc);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em.fetchEdgeEncoders());
        instance.setMinNetworkSize(4);
        instance.doWork();
        g.optimize();
        assertEquals(6, g.getNodes());
        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(g));
        EdgeExplorer explorer = g.createEdgeExplorer();
        assertEquals(GHUtility.asSet(2, 1, 5), GHUtility.getNeighbors(explorer.setBaseNode(3)));

        // do not remove because small network is big enough
        g = createSubnetworkTestStorage2(em, accessEnc);
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
        EncodingManager em2 = new EncodingManager.Builder().addGlobalEncodedValues().addAll(carEncoder, bikeEncoder).build();
        GraphHopperStorage g = createSubnetworkTestStorage2(em2, em2.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS));
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em2.fetchEdgeEncoders());

        EdgeExplorer edgeExplorer = g.createEdgeExplorer();
        assertFalse(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 4));
        assertFalse(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 5));
        assertFalse(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 6));

        assertTrue(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 4));
        assertTrue(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 5));
        assertTrue(instance.detectNodeRemovedForAllEncoders(edgeExplorer, 6));
    }

    @Test
    public void testRemoveSubnetworkWhenMultipleVehicles() {
        FlagEncoder carEncoder = new CarFlagEncoder();
        BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();
        EncodingManager em2 = new EncodingManager.Builder().addGlobalEncodedValues().addAll(carEncoder, bikeEncoder).build();
        BooleanEncodedValue carAccessEnc = em2.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS);
        BooleanEncodedValue bikeAccessEnc = em2.getBooleanEncodedValue(TagParserFactory.BIKE_ACCESS);
        DecimalEncodedValue bikeAverageSpeedEnc = em2.getDecimalEncodedValue(TagParserFactory.BIKE_AVERAGE_SPEED);
        GraphHopperStorage g = createSubnetworkTestStorage2(em2, carAccessEnc);
        IntsRef ints = em2.createIntsRef();
        carAccessEnc.setBool(false, ints, false);
        carAccessEnc.setBool(true, ints, false);
        bikeAccessEnc.setBool(false, ints, true);
        bikeAccessEnc.setBool(true, ints, true);
        bikeAverageSpeedEnc.setDecimal(false, ints, 5d);
        GHUtility.getEdge(g, 3, 4).setData(ints);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em2.fetchEdgeEncoders());
        instance.setMinNetworkSize(5);
        instance.doWork();
        g.optimize();
        // remove nothing because of two vehicles with different subnetworks
        assertEquals(9, g.getNodes());

        EdgeExplorer carExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(carAccessEnc));
        assertEquals(GHUtility.asSet(7, 2, 1), GHUtility.getNeighbors(carExplorer.setBaseNode(3)));
        EdgeExplorer bikeExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(bikeAccessEnc));
        assertEquals(GHUtility.asSet(7, 2, 1, 4), GHUtility.getNeighbors(bikeExplorer.setBaseNode(3)));
        ints = em2.createIntsRef();
        carAccessEnc.setBool(false, ints, false);
        carAccessEnc.setBool(true, ints, false);
        bikeAccessEnc.setBool(false, ints, false);
        bikeAccessEnc.setBool(true, ints, false);
        GHUtility.getEdge(g, 3, 4).setData(ints);
        instance = new PrepareRoutingSubnetworks(g, em2.fetchEdgeEncoders());
        instance.setMinNetworkSize(5);
        instance.doWork();
        g.optimize();
        assertEquals(6, g.getNodes());
    }

    GraphHopperStorage createDeadEndUnvisitedNetworkStorage(EncodingManager em) {
        GraphHopperStorage g = createStorage(em);
        // 0 <-> 1 <-> 2 <-> 3 <-> 4 <- 5 <-> 6
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 1, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 2, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 2, 3, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 3, 4, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 5, 4, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 5, 6, true, 1);

        // 7 -> 8 <-> 9 <-> 10
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 7, 8, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 8, 9, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 9, 10, true, 1);

        return g;
    }

    GraphHopperStorage createTarjanTestStorage() {
        GraphHopperStorage g = createStorage(em);

        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 2, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 2, 3, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 3, 1, false, 1);

        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 4, 2, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 4, 3, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 4, 5, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 5, 6, false, 1);

        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 6, 3, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 6, 7, true, 1);

        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 8, 5, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 8, 7, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 8, 8, false, 1);

        return g;
    }

    @Test
    public void testRemoveDeadEndUnvisitedNetworks() {
        GraphHopperStorage g = createDeadEndUnvisitedNetworkStorage(em);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(carFlagEncoder)).
                setMinOneWayNetworkSize(3);
        int removed = instance.removeDeadEndUnvisitedNetworks(new PrepEdgeFilter(accessEnc));

        assertEquals(3, removed);
        instance.markNodesRemovedIfUnreachable();
        g.optimize();

        assertEquals(8, g.getNodes());
    }

    @Test
    public void testTarjan() {
        GraphHopperStorage g = createSubnetworkTestStorage();

        // Requires a single vehicle type, otherwise we throw.
        final EdgeFilter filter = new DefaultEdgeFilter(accessEnc, true, false);
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
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 2, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 2, 0, false, 1);

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(carFlagEncoder)).
                setMinOneWayNetworkSize(2);
        int removedEdges = instance.removeDeadEndUnvisitedNetworks(new PrepEdgeFilter(accessEnc));
        assertEquals(2, removedEdges);
    }

    @Test
    public void test481() {
        // 0->1->3->4->5->6
        //  2        7<--/
        GraphHopperStorage g = createStorage(em);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 0, 1, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 2, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 2, 0, false, 1);

        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 3, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 3, 4, false, 1);

        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 4, 5, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 5, 6, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 6, 7, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 7, 4, false, 1);

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(carFlagEncoder)).
                setMinOneWayNetworkSize(2).
                setMinNetworkSize(4);
        instance.doWork();

        // only one remaining network
        List<IntArrayList> components = instance.findSubnetworks(new PrepEdgeFilter(accessEnc));
        assertEquals(1, components.size());
    }
}
