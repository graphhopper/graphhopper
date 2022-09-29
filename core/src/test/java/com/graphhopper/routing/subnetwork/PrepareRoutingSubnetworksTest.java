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
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworksTest {

    private static BaseGraph createSubnetworkTestStorage(EncodingManager encodingManager, BooleanEncodedValue accessEnc1, DecimalEncodedValue speedEnc1, BooleanEncodedValue accessEnc2, DecimalEncodedValue speedEnc2) {
        BaseGraph g = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
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
            iter.set(accessEnc1, true, true);
            iter.set(speedEnc1, 10);
            if (accessEnc2 != null)
                iter.set(accessEnc2, true, true);
            if (speedEnc2 != null)
                iter.set(speedEnc2, 10);
        }
        return g;
    }

    @Test
    public void testPrepareSubnetworks_oneVehicle() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        BooleanEncodedValue subnetworkEnc = Subnetwork.create("car");
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).add(subnetworkEnc).build();
        BaseGraph g = createSubnetworkTestStorage(em, accessEnc, speedEnc, null, null);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(createJob(subnetworkEnc, accessEnc, speedEnc, NO_TURN_COST_PROVIDER)));
        // this will make the upper small network a subnetwork
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, subnetworkEnc));

        // this time we lower the threshold and the upper network won't be set to be a subnetwork
        g = createSubnetworkTestStorage(em, accessEnc, speedEnc, null, null);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(createJob(subnetworkEnc, accessEnc, speedEnc, NO_TURN_COST_PROVIDER)));
        instance.setMinNetworkSize(3);
        assertEquals(0, instance.doWork());
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, subnetworkEnc));
    }

    @Test
    public void testPrepareSubnetworks_twoVehicles() {
        BooleanEncodedValue carAccessEnc = new SimpleBooleanEncodedValue("car_access", true);
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
        BooleanEncodedValue carSubnetworkEnc = Subnetwork.create("car");
        BooleanEncodedValue bikeAccessEnc = new SimpleBooleanEncodedValue("bike_access", true);
        DecimalEncodedValue bikeSpeedEnc = new DecimalEncodedValueImpl("bike_speed", 4, 2, false);
        BooleanEncodedValue bikeSubnetworkEnc = Subnetwork.create("bike");
        EncodingManager em = EncodingManager.start()
                .add(carAccessEnc).add(carSpeedEnc).add(carSubnetworkEnc)
                .add(bikeAccessEnc).add(bikeSpeedEnc).add(bikeSubnetworkEnc)
                .build();
        BaseGraph g = createSubnetworkTestStorage(em, carAccessEnc, carSpeedEnc, bikeAccessEnc, bikeSpeedEnc);

        // first we only block the middle edge for cars. this way a subnetwork should be created but only for car
        EdgeIteratorState edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, false, false, carAccessEnc, carSpeedEnc, edge);
        GHUtility.setSpeed(5, true, true, bikeAccessEnc, bikeSpeedEnc, edge);
        List<PrepareRoutingSubnetworks.PrepareJob> prepareJobs = Arrays.asList(
                createJob(carSubnetworkEnc, carAccessEnc, carSpeedEnc, NO_TURN_COST_PROVIDER),
                createJob(bikeSubnetworkEnc, bikeAccessEnc, bikeSpeedEnc, NO_TURN_COST_PROVIDER)
        );
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, carSubnetworkEnc));
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, bikeSubnetworkEnc));

        // now we block the edge for both vehicles -> there should be a subnetwork for both vehicles
        g = createSubnetworkTestStorage(em, carAccessEnc, carSpeedEnc, bikeAccessEnc, bikeSpeedEnc);
        edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, false, false, carAccessEnc, carSpeedEnc, edge);
        GHUtility.setSpeed(5, false, false, bikeAccessEnc, bikeSpeedEnc, edge);
        instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        assertEquals(6, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, carSubnetworkEnc));
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, bikeSubnetworkEnc));
    }

    @Test
    public void testPrepareSubnetwork_withTurnCosts() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        DecimalEncodedValue turnCostEnc = TurnCost.create("car", 1);
        BooleanEncodedValue subnetworkEnc = Subnetwork.create("car");
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).add(subnetworkEnc).addTurnCostEncodedValue(turnCostEnc).build();

        // since the middle edge is blocked the upper component is a subnetwork (regardless of turn costs)
        BaseGraph g = createSubnetworkTestStorage(em, accessEnc, speedEnc, null, null);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                createJob(subnetworkEnc, accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, g.getTurnCostStorage(), 0))));
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, subnetworkEnc));

        // if we open the edge it won't be a subnetwork anymore
        g = createSubnetworkTestStorage(em, accessEnc, speedEnc, null, null);
        EdgeIteratorState edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, true, true, accessEnc, speedEnc, edge);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                createJob(subnetworkEnc, accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, g.getTurnCostStorage(), 0))));
        instance.setMinNetworkSize(4);
        assertEquals(0, instance.doWork());
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, subnetworkEnc));

        // ... and now for something interesting: if we open the edge *and* apply turn restrictions it will be a
        // subnetwork again
        g = createSubnetworkTestStorage(em, accessEnc, speedEnc, null, null);
        edge = GHUtility.getEdge(g, 3, 4);
        GHUtility.setSpeed(10, true, true, accessEnc, speedEnc, edge);
        g.getTurnCostStorage().set(turnCostEnc, 0, 4, 7, Double.POSITIVE_INFINITY);
        g.getTurnCostStorage().set(turnCostEnc, 0, 4, 9, Double.POSITIVE_INFINITY);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                createJob(subnetworkEnc, accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, g.getTurnCostStorage(), 0))));
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, subnetworkEnc));
    }

    private BaseGraph createSubnetworkTestStorageWithOneWays(EncodingManager em, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        BaseGraph g = new BaseGraph.Builder(em).create();
        // 0 - 1 - 2 - 3 - 4 <- 5 - 6
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(5, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(5, 6).setDistance(1));

        // 7 -> 8 - 9 - 10
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(7, 8).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(8, 9).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(9, 10).setDistance(1));
        return g;
    }

    @Test
    public void testPrepareSubnetworks_withOneWays() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        BooleanEncodedValue subnetworkEnc = Subnetwork.create("car");
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).add(subnetworkEnc).build();
        BaseGraph g = createSubnetworkTestStorageWithOneWays(em, accessEnc, speedEnc);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks.PrepareJob job = createJob(subnetworkEnc, accessEnc, speedEnc, NO_TURN_COST_PROVIDER);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(2);
        int subnetworkEdges = instance.doWork();

        // the (7) and the (5,6) components become subnetworks -> 2 remaining components and 3 subnetwork edges
        // note that the subnetworkEV per profile is one bit per *edge*. Before we used the encoder$access with 2 bits
        // and got more fine grained response here (8 removed *edgeKeys*)
        assertEquals(3, subnetworkEdges);
        assertEquals(IntArrayList.from(4, 5, 6), getSubnetworkEdges(g, subnetworkEnc));

        g = createSubnetworkTestStorageWithOneWays(em, accessEnc, speedEnc);
        assertEquals(11, g.getNodes());

        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(3);
        subnetworkEdges = instance.doWork();

        // due to the larger min network size this time also the (8,9,10) component is a subnetwork
        assertEquals(5, subnetworkEdges);
        assertEquals(IntArrayList.from(4, 5, 6, 7, 8), getSubnetworkEdges(g, subnetworkEnc));
    }

    // Previous two-pass implementation failed on 1 -> 2 -> 0
    @Test
    public void testNodeOrderingRegression() {
        // 1 -> 2 -> 0 - 3 - 4 - 5
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        BooleanEncodedValue subnetworkEnc = Subnetwork.create("car");
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).add(subnetworkEnc).build();
        BaseGraph g = new BaseGraph.Builder(em).create();
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(2, 0).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(0, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(4, 5).setDistance(1));

        PrepareRoutingSubnetworks.PrepareJob job = createJob(subnetworkEnc, accessEnc, speedEnc, NO_TURN_COST_PROVIDER);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(2);
        int subnetworkEdges = instance.doWork();
        assertEquals(2, subnetworkEdges);
        assertEquals(IntArrayList.from(0, 1), getSubnetworkEdges(g, subnetworkEnc));
    }

    private static IntArrayList getSubnetworkEdges(BaseGraph graph, BooleanEncodedValue subnetworkEnc) {
        IntArrayList result = new IntArrayList();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next())
            if (iter.get(subnetworkEnc))
                result.add(iter.getEdge());
        return result;
    }

    private static PrepareRoutingSubnetworks.PrepareJob createJob(BooleanEncodedValue subnetworkEnc, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, TurnCostProvider turnCostProvider) {
        return new PrepareRoutingSubnetworks.PrepareJob(subnetworkEnc, new FastestWeighting(accessEnc, speedEnc, turnCostProvider));
    }

}
