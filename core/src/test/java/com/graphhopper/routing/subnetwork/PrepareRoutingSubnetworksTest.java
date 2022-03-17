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
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworksTest {

    private static BaseGraph createSubnetworkTestStorage(EncodingManager encodingManager) {
        BaseGraph g = new BaseGraph.Builder(encodingManager).create();
        //         5 - 6
        //         | /
        //         4
        //         | <- (no access flags unless we change it)
        // 0 - 1 - 3 - 7 - 8
        // |       |
        // 2 -------
        g.edge(3, 4).setDistance(1);
        g.edge(0, 1).setDistance(1);
        g.edge(1, 3).setDistance(1);
        g.edge(0, 2).setDistance(1);
        g.edge(2, 3).setDistance(1);
        g.edge(3, 7).setDistance(1);
        g.edge(7, 8).setDistance(1);
        g.edge(4, 5).setDistance(1);
        g.edge(5, 6).setDistance(1);
        g.edge(4, 6).setDistance(1);

        // set access for all encoders
        AllEdgesIterator iter = g.getAllEdges();
        while (iter.next()) {
            // edge 3-4 gets no speed/access by default
            if (iter.getEdge() == 0)
                continue;
            for (FlagEncoder encoder : encodingManager.fetchEdgeEncoders()) {
                iter.set(encoder.getAverageSpeedEnc(), 10);
                iter.set(encoder.getAccessEnc(), true, true);
            }
        }
        return g;
    }

    @Test
    public void testPrepareSubnetworks_oneVehicle() {
        EncodingManager em = createEncodingManager("car");
        FlagEncoder encoder = em.getEncoder("car");
        BaseGraph g = createSubnetworkTestStorage(em);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(createJob(em, encoder, NO_TURN_COST_PROVIDER)));
        // this will make the upper small network a subnetwork
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, encoder));

        // this time we lower the threshold and the upper network won't be set to be a subnetwork
        g = createSubnetworkTestStorage(em);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(createJob(em, encoder, NO_TURN_COST_PROVIDER)));
        instance.setMinNetworkSize(3);
        assertEquals(0, instance.doWork());
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, encoder));
    }

    @Test
    public void testPrepareSubnetworks_twoVehicles() {
        EncodingManager em = createEncodingManager("car,bike");
        FlagEncoder carEncoder = em.getEncoder("car");
        FlagEncoder bikeEncoder = em.getEncoder("bike");
        BaseGraph g = createSubnetworkTestStorage(em);

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

        // now we block the edge for both vehicles -> there should be a subnetwork for both vehicles
        g = createSubnetworkTestStorage(em);
        edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, false, false, carEncoder, edge);
        GHUtility.setSpeed(5, false, false, bikeEncoder, edge);
        instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        assertEquals(6, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, carEncoder));
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, bikeEncoder));
    }

    @Test
    public void testPrepareSubnetwork_withTurnCosts() {
        EncodingManager em = createEncodingManager("car|turn_costs=true");
        FlagEncoder encoder = em.fetchEdgeEncoders().iterator().next();

        // since the middle edge is blocked the upper component is a subnetwork (regardless of turn costs)
        BaseGraph g = createSubnetworkTestStorage(em);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                createJob(em, encoder, new DefaultTurnCostProvider(encoder, g.getTurnCostStorage(), 0))));
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, encoder));

        // if we open the edge it won't be a subnetwork anymore
        g = createSubnetworkTestStorage(em);
        EdgeIteratorState edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, true, true, encoder, edge);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                createJob(em, encoder, new DefaultTurnCostProvider(encoder, g.getTurnCostStorage(), 0))));
        instance.setMinNetworkSize(4);
        assertEquals(0, instance.doWork());
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, encoder));

        // ... and now for something interesting: if we open the edge *and* apply turn restrictions it will be a
        // subnetwork again
        g = createSubnetworkTestStorage(em);
        edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, true, true, encoder, edge);
        DecimalEncodedValue turnCostEnc = em.getDecimalEncodedValue(TurnCost.key(encoder.toString()));
        g.getTurnCostStorage().set(turnCostEnc, 0, 4, 7, 1);
        g.getTurnCostStorage().set(turnCostEnc, 0, 4, 9, 1);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                createJob(em, encoder, new DefaultTurnCostProvider(encoder, g.getTurnCostStorage(), 0))));
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, encoder));

    }


    private BaseGraph createSubnetworkTestStorageWithOneWays(EncodingManager em, FlagEncoder encoder) {
        if (em.fetchEdgeEncoders().size() > 1)
            fail("Warning: This method only sets access/speed for a single encoder, but the given encoding manager has multiple encoders");
        BaseGraph g = new BaseGraph.Builder(em).create();
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
    public void testPrepareSubnetworks_withOneWays() {
        EncodingManager em = createEncodingManager("car");
        FlagEncoder encoder = em.fetchEdgeEncoders().iterator().next();
        BaseGraph g = createSubnetworkTestStorageWithOneWays(em, encoder);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks.PrepareJob job = createJob(em, encoder, NO_TURN_COST_PROVIDER);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(2);
        int subnetworkEdges = instance.doWork();

        // the (7) and the (5,6) components become subnetworks -> 2 remaining components and 3 subnetwork edges
        // note that the subnetworkEV per profile is one bit per *edge*. Before we used the encoder$access with 2 bits
        // and got more fine grained response here (8 removed *edgeKeys*)
        assertEquals(3, subnetworkEdges);
        assertEquals(IntArrayList.from(4, 5, 6), getSubnetworkEdges(g, encoder));

        g = createSubnetworkTestStorageWithOneWays(em, encoder);
        assertEquals(11, g.getNodes());

        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(3);
        subnetworkEdges = instance.doWork();

        // due to the larger min network size this time also the (8,9,10) component is a subnetwork
        assertEquals(5, subnetworkEdges);
        assertEquals(IntArrayList.from(4, 5, 6, 7, 8), getSubnetworkEdges(g, encoder));
    }

    // Previous two-pass implementation failed on 1 -> 2 -> 0
    @Test
    public void testNodeOrderingRegression() {
        // 1 -> 2 -> 0 - 3 - 4 - 5
        EncodingManager em = createEncodingManager("car");
        BaseGraph g = new BaseGraph.Builder(em).create();
        FlagEncoder encoder = em.fetchEdgeEncoders().iterator().next();
        GHUtility.setSpeed(60, true, false, encoder, g.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(2, 0).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(4, 5).setDistance(1));

        PrepareRoutingSubnetworks.PrepareJob job = createJob(em, encoder, NO_TURN_COST_PROVIDER);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(2);
        int subnetworkEdges = instance.doWork();
        assertEquals(2, subnetworkEdges);
        assertEquals(IntArrayList.from(0, 1), getSubnetworkEdges(g, encoder));
    }

    private static IntArrayList getSubnetworkEdges(BaseGraph graph, FlagEncoder encoder) {
        BooleanEncodedValue subnetworkEnc = encoder.getBooleanEncodedValue(Subnetwork.key(encoder.toString()));
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
