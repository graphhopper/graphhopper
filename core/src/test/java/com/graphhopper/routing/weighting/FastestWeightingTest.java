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
package com.graphhopper.routing.weighting;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Routing;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.weighting.FastestWeighting.DESTINATION_FACTOR;
import static com.graphhopper.routing.weighting.FastestWeighting.PRIVATE_FACTOR;
import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Peter Karich
 */
public class FastestWeightingTest {
    private final BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
    private final BooleanEncodedValue turnRestrictionEnc = TurnRestriction.create("car");
    private final EncodingManager encodingManager = EncodingManager.start().add(accessEnc).add(speedEnc).addTurnCostEncodedValue(turnRestrictionEnc).build();
    private final BaseGraph graph = new BaseGraph.Builder(encodingManager).create();

    @Test
    public void testMinWeightHasSameUnitAs_getWeight() {
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(10);
        GHUtility.setSpeed(140, 0, accessEnc, speedEnc, edge);
        Weighting instance = new FastestWeighting(accessEnc, speedEnc);
        assertEquals(instance.calcMinWeight(10), instance.calcEdgeWeight(edge, false), 1e-8);
    }

    @Test
    public void testWeightWrongHeading() {
        final double penalty = 100;
        Weighting instance = new FastestWeighting(accessEnc, speedEnc, null, new PMap().putObject(Parameters.Routing.HEADING_PENALTY, penalty), TurnCostProvider.NO_TURN_COST_PROVIDER);
        EdgeIteratorState edge = graph.edge(1, 2).setDistance(10).setWayGeometry(Helper.createPointList(51, 0, 51, 1));
        GHUtility.setSpeed(10, 10, accessEnc, speedEnc, edge);
        VirtualEdgeIteratorState virtEdge = new VirtualEdgeIteratorState(edge.getEdgeKey(), 99, 5, 6, edge.getDistance(), edge.getFlags(),
                edge.getKeyValues(), edge.fetchWayGeometry(FetchMode.PILLAR_ONLY), false);
        double time = instance.calcEdgeWeight(virtEdge, false);

        // no penalty on edge
        assertEquals(time, instance.calcEdgeWeight(virtEdge, false), 1e-8);
        assertEquals(time, instance.calcEdgeWeight(virtEdge, true), 1e-8);
        // ... unless setting it to unfavored (in both directions)
        virtEdge.setUnfavored(true);
        assertEquals(time + penalty, instance.calcEdgeWeight(virtEdge, false), 1e-8);
        assertEquals(time + penalty, instance.calcEdgeWeight(virtEdge, true), 1e-8);
        // but not after releasing it
        virtEdge.setUnfavored(false);
        assertEquals(time, instance.calcEdgeWeight(virtEdge, false), 1e-8);
        assertEquals(time, instance.calcEdgeWeight(virtEdge, true), 1e-8);

        // test default penalty
        virtEdge.setUnfavored(true);
        instance = new FastestWeighting(accessEnc, speedEnc);
        assertEquals(time + Routing.DEFAULT_HEADING_PENALTY, instance.calcEdgeWeight(virtEdge, false), 1e-8);
        assertEquals(time + Routing.DEFAULT_HEADING_PENALTY, instance.calcEdgeWeight(virtEdge, true), 1e-8);
    }

    @Test
    public void testSpeed0() {
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(10);
        Weighting instance = new FastestWeighting(accessEnc, speedEnc);
        edge.set(speedEnc, 0);
        assertTrue(Double.isInfinite(instance.calcEdgeWeight(edge, false)));

        // 0 / 0 returns NaN but calcWeight should not return NaN!
        edge.setDistance(0);
        assertTrue(Double.isInfinite(instance.calcEdgeWeight(edge, false)));
    }

    @Test
    public void testTime() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 4, 2, true);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph g = new BaseGraph.Builder(em).create();
        Weighting w = new FastestWeighting(accessEnc, speedEnc);
        EdgeIteratorState edge = g.edge(0, 1).setDistance(100_000);
        GHUtility.setSpeed(15, 10, accessEnc, speedEnc, edge);
        assertEquals(375 * 60 * 1000, w.calcEdgeMillis(edge, false));
        assertEquals(600 * 60 * 1000, w.calcEdgeMillis(edge, true));
    }

    @Test
    public void calcWeightAndTime_withTurnCosts() {
        BaseGraph graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        Weighting weighting = new FastestWeighting(accessEnc, speedEnc, new DefaultTurnCostProvider(turnRestrictionEnc, graph.getTurnCostStorage()));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(100));
        EdgeIteratorState edge = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(100));
        setTurnRestriction(graph, 0, 1, 2);
        assertTrue(Double.isInfinite(GHUtility.calcWeightWithTurnWeight(weighting, edge, false, 0)));
        assertEquals(Long.MAX_VALUE, GHUtility.calcMillisWithTurnMillis(weighting, edge, false, 0));
    }

    @Test
    public void calcWeightAndTime_uTurnCosts() {
        BaseGraph graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        Weighting weighting = new FastestWeighting(accessEnc, speedEnc, new DefaultTurnCostProvider(turnRestrictionEnc, graph.getTurnCostStorage(), 40));
        EdgeIteratorState edge = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(100));
        assertEquals(6 + 40, GHUtility.calcWeightWithTurnWeight(weighting, edge, false, 0), 1.e-6);
        assertEquals((6 + 40) * 1000, GHUtility.calcMillisWithTurnMillis(weighting, edge, false, 0), 1.e-6);
    }

    @Test
    public void calcWeightAndTime_withTurnCosts_shortest() {
        BaseGraph graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        Weighting weighting = new ShortestWeighting(accessEnc, speedEnc,
                new DefaultTurnCostProvider(turnRestrictionEnc, graph.getTurnCostStorage()));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(100));
        EdgeIteratorState edge = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(100));
        setTurnRestriction(graph, 0, 1, 2);
        assertTrue(Double.isInfinite(GHUtility.calcWeightWithTurnWeight(weighting, edge, false, 0)));
        assertEquals(Long.MAX_VALUE, GHUtility.calcMillisWithTurnMillis(weighting, edge, false, 0));
    }

    @Test
    public void testDestinationTag() {
        BooleanEncodedValue carAccessEnc = new SimpleBooleanEncodedValue("car_access", true);
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
        BooleanEncodedValue bikeAccessEnc = new SimpleBooleanEncodedValue("bike_access", true);
        DecimalEncodedValue bikeSpeedEnc = new DecimalEncodedValueImpl("bike_speed", 4, 2, false);
        EncodingManager em = EncodingManager.start().add(carAccessEnc).add(carSpeedEnc).add(bikeAccessEnc).add(bikeSpeedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1000);
        edge.set(carAccessEnc, true, true);
        edge.set(bikeAccessEnc, true, true);
        edge.set(carSpeedEnc, 60);
        edge.set(bikeSpeedEnc, 18);
        EnumEncodedValue<RoadAccess> roadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);

        FastestWeighting weighting = new FastestWeighting(carAccessEnc, carSpeedEnc, roadAccessEnc,
                new PMap().putObject(DESTINATION_FACTOR, 10), TurnCostProvider.NO_TURN_COST_PROVIDER);
        FastestWeighting bikeWeighting = new FastestWeighting(bikeAccessEnc, bikeSpeedEnc, roadAccessEnc,
                new PMap().putObject(DESTINATION_FACTOR, 1), TurnCostProvider.NO_TURN_COST_PROVIDER);

        edge.set(roadAccessEnc, RoadAccess.YES);
        assertEquals(60, weighting.calcEdgeWeight(edge, false), 1.e-6);
        assertEquals(200, bikeWeighting.calcEdgeWeight(edge, false), 1.e-6);

        // the destination tag does not change the weight for the bike weighting
        edge.set(roadAccessEnc, RoadAccess.DESTINATION);
        assertEquals(600, weighting.calcEdgeWeight(edge, false), 0.1);
        assertEquals(200, bikeWeighting.calcEdgeWeight(edge, false), 0.1);
    }

    @Test
    public void testPrivateTag() {
        BooleanEncodedValue carAccessEnc = new SimpleBooleanEncodedValue("car_access", true);
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
        BooleanEncodedValue bikeAccessEnc = new SimpleBooleanEncodedValue("bike_access", true);
        DecimalEncodedValue bikeSpeedEnc = new DecimalEncodedValueImpl("bike_speed", 4, 2, false);
        EncodingManager em = EncodingManager.start().add(carAccessEnc).add(carSpeedEnc).add(bikeAccessEnc).add(bikeSpeedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1000);
        edge.set(carAccessEnc, true, true);
        edge.set(bikeAccessEnc, true, true);
        edge.set(carSpeedEnc, 60);
        edge.set(bikeSpeedEnc, 18);
        EnumEncodedValue<RoadAccess> roadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);

        FastestWeighting weighting = new FastestWeighting(carAccessEnc, carSpeedEnc, roadAccessEnc,
                new PMap().putObject(PRIVATE_FACTOR, 10), TurnCostProvider.NO_TURN_COST_PROVIDER);
        FastestWeighting bikeWeighting = new FastestWeighting(bikeAccessEnc, bikeSpeedEnc, roadAccessEnc,
                new PMap().putObject(PRIVATE_FACTOR, 1.2), TurnCostProvider.NO_TURN_COST_PROVIDER);

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");

        edge.set(roadAccessEnc, RoadAccess.YES);
        assertEquals(60, weighting.calcEdgeWeight(edge, false), 1.e-6);
        assertEquals(200, bikeWeighting.calcEdgeWeight(edge, false), 1.e-6);

        edge.set(roadAccessEnc, RoadAccess.PRIVATE);
        assertEquals(600, weighting.calcEdgeWeight(edge, false), 1.e-6);
        // private should influence bike only slightly
        assertEquals(240, bikeWeighting.calcEdgeWeight(edge, false), 1.e-6);
    }

    private void setTurnRestriction(Graph graph, int from, int via, int to) {
        graph.getTurnCostStorage().set(turnRestrictionEnc, getEdge(graph, from, via).getEdge(), via, getEdge(graph, via, to).getEdge(), true);
    }

}
