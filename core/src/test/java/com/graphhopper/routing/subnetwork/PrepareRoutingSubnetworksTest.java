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
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworksTest {

    private static BaseGraph createSubnetworkTestStorage(EncodingManager encodingManager, DecimalEncodedValue speedEnc1, DecimalEncodedValue speedEnc2) {
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
            iter.set(speedEnc1, 10, 10);
            if (speedEnc2 != null)
                iter.set(speedEnc2, 10, 10);
        }
        return g;
    }

    @Test
    public void testPrepareSubnetworks_oneVehicle() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        BooleanEncodedValue subnetworkEnc = Subnetwork.create("car");
        EncodingManager em = EncodingManager.start().add(speedEnc).add(subnetworkEnc).build();
        BaseGraph g = createSubnetworkTestStorage(em, speedEnc, null);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(createJob(subnetworkEnc, speedEnc)));
        // this will make the upper small network a subnetwork
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, subnetworkEnc));

        // this time we lower the threshold and the upper network won't be set to be a subnetwork
        g = createSubnetworkTestStorage(em, speedEnc, null);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(createJob(subnetworkEnc, speedEnc)));
        instance.setMinNetworkSize(3);
        assertEquals(0, instance.doWork());
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, subnetworkEnc));
    }

    @Test
    public void testPrepareSubnetworks_twoVehicles() {
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, true);
        BooleanEncodedValue carSubnetworkEnc = Subnetwork.create("car");
        DecimalEncodedValue bikeSpeedEnc = new DecimalEncodedValueImpl("bike_speed", 4, 2, true);
        BooleanEncodedValue bikeSubnetworkEnc = Subnetwork.create("bike");
        EncodingManager em = EncodingManager.start()
                .add(carSpeedEnc).add(carSubnetworkEnc)
                .add(bikeSpeedEnc).add(bikeSubnetworkEnc)
                .build();
        BaseGraph g = createSubnetworkTestStorage(em, carSpeedEnc, bikeSpeedEnc);

        // first we only block the middle edge for cars. this way a subnetwork should be created but only for car
        EdgeIteratorState edge = GHUtility.getEdge(g, 3, 4);
        edge.set(carSpeedEnc, 0, 0);
        edge.set(bikeSpeedEnc, 5, 5);
        List<PrepareRoutingSubnetworks.PrepareJob> prepareJobs = Arrays.asList(
                createJob(carSubnetworkEnc, carSpeedEnc),
                createJob(bikeSubnetworkEnc, bikeSpeedEnc)
        );
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, carSubnetworkEnc));
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, bikeSubnetworkEnc));

        // now we block the edge for both vehicles -> there should be a subnetwork for both vehicles
        g = createSubnetworkTestStorage(em, carSpeedEnc, bikeSpeedEnc);
        edge = GHUtility.getEdge(g, 3, 4);
        edge.set(carSpeedEnc, 0, 0);
        edge.set(bikeSpeedEnc, 0, 0);
        instance = new PrepareRoutingSubnetworks(g, prepareJobs);
        instance.setMinNetworkSize(5);
        assertEquals(6, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, carSubnetworkEnc));
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, bikeSubnetworkEnc));
    }

    @Test
    public void testPrepareSubnetwork_withTurnCosts() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        DecimalEncodedValue turnCostEnc = TurnCost.create("car", 1);
        BooleanEncodedValue subnetworkEnc = Subnetwork.create("car");
        EncodingManager em = EncodingManager.start().add(speedEnc).add(subnetworkEnc).addTurnCostEncodedValue(turnCostEnc).build();

        // since the middle edge is blocked the upper component is a subnetwork (regardless of turn costs)
        BaseGraph g = createSubnetworkTestStorage(em, speedEnc, null);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                createJob(subnetworkEnc, speedEnc, turnCostEnc, g.getTurnCostStorage(), 0)));
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, subnetworkEnc));

        // if we open the edge it won't be a subnetwork anymore
        g = createSubnetworkTestStorage(em, speedEnc, null);
        EdgeIteratorState edge = GHUtility.getEdge(g, 3, 4);
        edge.set(speedEnc, 10, 10);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                createJob(subnetworkEnc, speedEnc, turnCostEnc, g.getTurnCostStorage(), 0)));
        instance.setMinNetworkSize(4);
        assertEquals(0, instance.doWork());
        assertEquals(IntArrayList.from(), getSubnetworkEdges(g, subnetworkEnc));

        // ... and now for something interesting: if we open the edge *and* apply turn restrictions it will be a
        // subnetwork again
        g = createSubnetworkTestStorage(em, speedEnc, null);
        edge = GHUtility.getEdge(g, 3, 4);
        edge.set(speedEnc, 10, 10);
        g.getTurnCostStorage().set(turnCostEnc, 0, 4, 7, Double.POSITIVE_INFINITY);
        g.getTurnCostStorage().set(turnCostEnc, 0, 4, 9, Double.POSITIVE_INFINITY);
        instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(
                createJob(subnetworkEnc, speedEnc, turnCostEnc, g.getTurnCostStorage(), 0)));
        instance.setMinNetworkSize(4);
        assertEquals(3, instance.doWork());
        assertEquals(IntArrayList.from(7, 8, 9), getSubnetworkEdges(g, subnetworkEnc));
    }

    private BaseGraph createSubnetworkTestStorageWithOneWays(EncodingManager em, DecimalEncodedValue speedEnc) {
        BaseGraph g = new BaseGraph.Builder(em).create();
        // 0 - 1 - 2 - 3 - 4 <- 5 - 6
        g.edge(0, 1).setDistance(1).set(speedEnc, 60, 60);
        g.edge(1, 2).setDistance(1).set(speedEnc, 60, 60);
        g.edge(2, 3).setDistance(1).set(speedEnc, 60, 60);
        g.edge(3, 4).setDistance(1).set(speedEnc, 60, 60);
        g.edge(5, 4).setDistance(1).set(speedEnc, 60, 0);
        g.edge(5, 6).setDistance(1).set(speedEnc, 60, 60);

        // 7 -> 8 - 9 - 10
        g.edge(7, 8).setDistance(1).set(speedEnc, 60, 0);
        g.edge(8, 9).setDistance(1).set(speedEnc, 60, 60);
        g.edge(9, 10).setDistance(1).set(speedEnc, 60, 60);
        return g;
    }

    @Test
    public void testPrepareSubnetworks_withOneWays() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        BooleanEncodedValue subnetworkEnc = Subnetwork.create("car");
        EncodingManager em = EncodingManager.start().add(speedEnc).add(subnetworkEnc).build();
        BaseGraph g = createSubnetworkTestStorageWithOneWays(em, speedEnc);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks.PrepareJob job = createJob(subnetworkEnc, speedEnc);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, Collections.singletonList(job)).
                setMinNetworkSize(2);
        int subnetworkEdges = instance.doWork();

        // the (7) and the (5,6) components become subnetworks -> 2 remaining components and 3 subnetwork edges
        // note that the subnetworkEV per profile is one bit per *edge*. Before we used the encoder$access with 2 bits
        // and got more fine grained response here (8 removed *edgeKeys*)
        assertEquals(3, subnetworkEdges);
        assertEquals(IntArrayList.from(4, 5, 6), getSubnetworkEdges(g, subnetworkEnc));

        g = createSubnetworkTestStorageWithOneWays(em, speedEnc);
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
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        BooleanEncodedValue subnetworkEnc = Subnetwork.create("car");
        EncodingManager em = EncodingManager.start().add(speedEnc).add(subnetworkEnc).build();
        BaseGraph g = new BaseGraph.Builder(em).create();
        g.edge(1, 2).setDistance(1).set(speedEnc, 60, 0);
        g.edge(2, 0).setDistance(1).set(speedEnc, 60, 0);
        g.edge(0, 3).setDistance(1).set(speedEnc, 60, 60);
        g.edge(3, 4).setDistance(1).set(speedEnc, 60, 60);
        g.edge(4, 5).setDistance(1).set(speedEnc, 60, 60);

        PrepareRoutingSubnetworks.PrepareJob job = createJob(subnetworkEnc, speedEnc);
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

    private static PrepareRoutingSubnetworks.PrepareJob createJob(BooleanEncodedValue subnetworkEnc, DecimalEncodedValue speedEnc) {
        return createJob(subnetworkEnc, speedEnc, null, null, 0);
    }

    private static PrepareRoutingSubnetworks.PrepareJob createJob(BooleanEncodedValue subnetworkEnc, DecimalEncodedValue speedEnc,
                                                                  DecimalEncodedValue turnCostEnc, TurnCostStorage turnCostStorage, double uTurnCosts) {
        return new PrepareRoutingSubnetworks.PrepareJob(subnetworkEnc, new SpeedWeighting(speedEnc, turnCostEnc, turnCostStorage, uTurnCosts));
    }

}
