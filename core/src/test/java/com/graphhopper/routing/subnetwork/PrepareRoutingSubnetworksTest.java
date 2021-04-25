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
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworksTest {

    private static GraphHopperStorage createSubnetworkTestStorage(EncodingManager encodingManager, FlagEncoder encoder) {
        GraphHopperStorage g = new GraphBuilder(encodingManager).create();
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
    public void testPrepareSubnetworkIfOnlyOneVehicle() {
        EncodingManager em = createEncodingManager("car");
        FlagEncoder encoder = em.getEncoder("car");
        GraphHopperStorage g = createSubnetworkTestStorage(em, encoder);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(createJob(em, encoder, NO_TURN_COST_PROVIDER)));
        // this will make the upper small network a subnetwork
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, encoder));

        // this time we lower the threshold and the upper network won't be set to be a subnetwork
        em = createEncodingManager("car");
        encoder = em.getEncoder("car");
        g = createSubnetworkTestStorage(em, encoder);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(createJob(em, encoder, NO_TURN_COST_PROVIDER)));
        instance.setMinNetworkSize(3);
        assertEquals(0, instance.doWork());
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, encoder));
    }

    @Test
    public void testPrepareSubnetworkWithTurnCosts() {
        EncodingManager em = createEncodingManager("car|turn_costs=true");
        FlagEncoder encoder = em.fetchEdgeEncoders().iterator().next();
        GraphHopperStorage g = createSubnetworkTestStorage(em, encoder);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                createJob(em, encoder, new DefaultTurnCostProvider(encoder, g.getTurnCostStorage(), 0))));
        // this will make the upper small network a subnetwork
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, encoder));

        // this time we lower the threshold and the small network will remain
        em = createEncodingManager("car|turn_costs=true");
        encoder = em.fetchEdgeEncoders().iterator().next();
        g = createSubnetworkTestStorage(em, encoder);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(createJob(em, encoder, NO_TURN_COST_PROVIDER)));
        instance.setMinNetworkSize(3);
        assertEquals(0, instance.doWork());
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, encoder));
    }

    @Test
    public void testCountSubnetworkEdgesAtNodes() {
        EncodingManager em = createEncodingManager("car,bike");
        List<FlagEncoder> encoders = Arrays.asList(
                em.getEncoder("car"),
                em.getEncoder("bike")
        );
        FlagEncoder bikeEncoder = encoders.get(1);
        GraphHopperStorage g = createSubnetworkTestStorage(em, encoders.get(0));
        AllEdgesIterator allIter = g.getAllEdges();
        while (allIter.next()) {
            if (allIter.getEdge() != 6)
                GHUtility.setSpeed(bikeEncoder.getMaxSpeed() / 2, true, true, bikeEncoder, allIter);
        }
        List<PrepareRoutingSubnetworks.PrepareJob> jobs = new ArrayList<>();
        for (FlagEncoder encoder : encoders) {
            BooleanEncodedValue subnetworkEnc = em.getBooleanEncodedValue(Subnetwork.key(encoder.toString()));
            jobs.add(new PrepareRoutingSubnetworks.PrepareJob(subnetworkEnc, new FastestWeighting(encoder)));
            assertEquals(0, getSubnetworkEdgesAdjacentToNode(g, 4, subnetworkEnc));
            assertEquals(0, getSubnetworkEdgesAdjacentToNode(g, 5, subnetworkEnc));
            assertEquals(0, getSubnetworkEdgesAdjacentToNode(g, 6, subnetworkEnc));
        }

        g = createSubnetworkTestStorage(em, encoders.get(0));
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, jobs);
        allIter = g.getAllEdges();
        while (allIter.next()) {
            if (allIter.getEdge() != 6)
                GHUtility.setSpeed(bikeEncoder.getMaxSpeed() / 2, true, true, bikeEncoder, allIter);
        }
        instance.setMinNetworkSize(4);
        assertEquals(6, instance.doWork());
        for (FlagEncoder encoder : encoders) {
            BooleanEncodedValue subnetworkEnc = em.getBooleanEncodedValue(Subnetwork.key(encoder.toString()));
            assertEquals(2, getSubnetworkEdgesAdjacentToNode(g, 4, subnetworkEnc));
            assertEquals(2, getSubnetworkEdgesAdjacentToNode(g, 5, subnetworkEnc));
            assertEquals(2, getSubnetworkEdgesAdjacentToNode(g, 6, subnetworkEnc));
        }
    }

    private int getSubnetworkEdgesAdjacentToNode(Graph graph, int node, BooleanEncodedValue subnetworkEnc) {
        EdgeExplorer explorer = graph.createEdgeExplorer();
        EdgeIterator iter = explorer.setBaseNode(node);
        int result = 0;
        while (iter.next()) {
            if (iter.get(subnetworkEnc))
                result++;
        }
        return result;
    }

    @Test
    public void testRemoveSubnetworkWhenMultipleVehicles() {
        EncodingManager em = createEncodingManager("car,bike");
        FlagEncoder carEncoder = em.getEncoder("car");
        FlagEncoder bikeEncoder = em.getEncoder("bike");
        GraphHopperStorage g = createSubnetworkTestStorage(em, carEncoder);
        AllEdgesIterator allIter = g.getAllEdges();
        while (allIter.next()) {
            GHUtility.setSpeed(bikeEncoder.getMaxSpeed() / 2, true, true, bikeEncoder, allIter);
        }

        // first we only block the middle edge for cars. this way a subnetwork should be created but only for car
        EdgeIteratorState edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, false, false, carEncoder, edge);
        GHUtility.setSpeed(5, true, true, bikeEncoder, edge);
        List<PrepareRoutingSubnetworks.PrepareJob> prepareJobs = Arrays.asList(
                createJob(em, carEncoder, NO_TURN_COST_PROVIDER),
                createJob(em, bikeEncoder, NO_TURN_COST_PROVIDER)
        );
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        assertEquals(3, instance.doWork());

        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, carEncoder));
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, bikeEncoder));

        // now we block the edge for both vehicles -> there should be a subnetwork for both
        g = createSubnetworkTestStorage(em, carEncoder);
        allIter = g.getAllEdges();
        while (allIter.next()) {
            GHUtility.setSpeed(bikeEncoder.getMaxSpeed() / 2, true, true, bikeEncoder, allIter);
        }
        edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, false, false, carEncoder, edge);
        GHUtility.setSpeed(5, false, false, bikeEncoder, edge);
        instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        assertEquals(6, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, carEncoder));
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, bikeEncoder));
    }

    private GraphHopperStorage createSubnetworkTestStorageWithOneWays(EncodingManager em, FlagEncoder encoder) {
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
        EncodingManager em = createEncodingManager("car");
        FlagEncoder encoder = em.fetchEdgeEncoders().iterator().next();
        GraphHopperStorage g = createSubnetworkTestStorageWithOneWays(em, encoder);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks.PrepareJob job = createJob(g.getEncodingManager(), encoder, NO_TURN_COST_PROVIDER);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(2);
        int subnetworkEdges = instance.doWork();

        // the (7) and the (5,6) components become subnetworks -> 2 remaining components and 3 subnetwork edges
        // note that the subnetworkEV per profile is one bit per *edge*. Before we used the encoder$access with 2 bits
        // and got more fine grained response here (8 removed *edgeKeys*)
        assertEquals(3, subnetworkEdges);
        assertEquals(IntArrayList.from(4, 5, 6), getSubnetworkEdges(g, encoder));
    }

    @Test
    public void testRemoveSubNetworks_withOneWays_higherMinNetworkSize() {
        EncodingManager em = createEncodingManager("car");
        FlagEncoder encoder = em.fetchEdgeEncoders().iterator().next();
        GraphHopperStorage g = createSubnetworkTestStorageWithOneWays(em, encoder);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks.PrepareJob job = createJob(g.getEncodingManager(), encoder, NO_TURN_COST_PROVIDER);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(3);
        int subnetworkEdges = instance.doWork();

        // this time also the (8,9,10) component is a subnetwork
        assertEquals(5, subnetworkEdges);
        assertEquals(IntArrayList.from(4, 5, 6, 7, 8), getSubnetworkEdges(g, encoder));
    }

    // Previous two-pass implementation failed on 1 -> 2 -> 0
    @Test
    public void testNodeOrderingRegression() {
        // 1 -> 2 -> 0 - 3 - 4 - 5
        GraphHopperStorage g = new GraphBuilder(createEncodingManager("car")).create();
        FlagEncoder encoder = g.getEncodingManager().fetchEdgeEncoders().iterator().next();
        GHUtility.setSpeed(60, true, false, encoder, g.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(2, 0).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(4, 5).setDistance(1));

        PrepareRoutingSubnetworks.PrepareJob job = createJob(g.getEncodingManager(), encoder, NO_TURN_COST_PROVIDER);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(2);
        int subnetworkEdges = instance.doWork();
        assertEquals(2, subnetworkEdges);
        assertEquals(IntArrayList.from(0, 1), getSubnetworkEdges(g, encoder));
    }

    private static IntArrayList getSubnetworkEdges(GraphHopperStorage graph, FlagEncoder encoder) {
        BooleanEncodedValue subnetworkEnc = graph.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(encoder.toString()));
        IntArrayList result = new IntArrayList();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            if (iter.get(subnetworkEnc)) {
                result.add(iter.getEdge());
            }
        }
        return result;
    }

    private static EncodingManager createEncodingManager(String flagEncodersStr) {
        EncodingManager.Builder builder = new EncodingManager.Builder();
        for (String encoderStr : flagEncodersStr.split(",")) {
            encoderStr = encoderStr.trim();
            FlagEncoder encoder = new DefaultFlagEncoderFactory().createFlagEncoder(encoderStr.split("\\|")[0], new PMap(encoderStr));
            builder.add(encoder);
            builder.add(Subnetwork.create(encoder.toString()));
        }
        return builder.build();
    }

    private static PrepareRoutingSubnetworks.PrepareJob createJob(EncodingManager em, FlagEncoder encoder, TurnCostProvider turnCostProvider) {
        return new PrepareRoutingSubnetworks.PrepareJob(em.getBooleanEncodedValue(Subnetwork.key(encoder.toString())),
                new FastestWeighting(encoder, turnCostProvider));
    }

}
