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

import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.TurnCost;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.Bike2WeightFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Routing;
import org.junit.Test;

import static com.graphhopper.util.GHUtility.createMockedEdgeIteratorState;
import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class FastestWeightingTest {
    private final FlagEncoder encoder = new CarFlagEncoder(5, 5, 10);
    private final EncodingManager encodingManager = EncodingManager.create(encoder);

    @Test
    public void testMinWeightHasSameUnitAs_getWeight() {
        Weighting instance = new FastestWeighting(encoder);
        IntsRef flags = GHUtility.setProperties(encodingManager.createEdgeFlags(), encoder, encoder.getMaxSpeed(), true, false);
        assertEquals(instance.getMinWeight(10), instance.calcEdgeWeight(createMockedEdgeIteratorState(10, flags), false), 1e-8);
    }

    @Test
    public void testWeightWrongHeading() {
        Weighting instance = new FastestWeighting(encoder, new PMap().putObject(Parameters.Routing.HEADING_PENALTY, 100));

        VirtualEdgeIteratorState virtEdge = new VirtualEdgeIteratorState(0, 1, 1, 2, 10,
                GHUtility.setProperties(encodingManager.createEdgeFlags(), encoder, 10, true, false), "test", Helper.createPointList(51, 0, 51, 1), false);
        double time = instance.calcEdgeWeight(virtEdge, false);

        virtEdge.setUnfavored(true);
        // heading penalty on edge
        assertEquals(time + 100, instance.calcEdgeWeight(virtEdge, false), 1e-8);
        // only after setting it
        virtEdge.setUnfavored(true);
        assertEquals(time + 100, instance.calcEdgeWeight(virtEdge, true), 1e-8);
        // but not after releasing it
        virtEdge.setUnfavored(false);
        assertEquals(time, instance.calcEdgeWeight(virtEdge, true), 1e-8);

        // test default penalty
        virtEdge.setUnfavored(true);
        instance = new FastestWeighting(encoder);
        assertEquals(time + Routing.DEFAULT_HEADING_PENALTY, instance.calcEdgeWeight(virtEdge, false), 1e-8);
    }

    @Test
    public void testSpeed0() {
        Weighting instance = new FastestWeighting(encoder);
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        encoder.getAverageSpeedEnc().setDecimal(false, edgeFlags, 0);
        assertEquals(1.0 / 0, instance.calcEdgeWeight(createMockedEdgeIteratorState(10, edgeFlags), false), 1e-8);

        // 0 / 0 returns NaN but calcWeight should not return NaN!
        assertEquals(1.0 / 0, instance.calcEdgeWeight(createMockedEdgeIteratorState(0, edgeFlags), false), 1e-8);
    }

    @Test
    public void testTime() {
        FlagEncoder tmpEnc = new Bike2WeightFlagEncoder();
        GraphHopperStorage g = new GraphBuilder(EncodingManager.create(tmpEnc)).create();
        Weighting w = new FastestWeighting(tmpEnc);

        IntsRef edgeFlags = GHUtility.setProperties(g.getEncodingManager().createEdgeFlags(), tmpEnc, 15, true, true);
        tmpEnc.getAverageSpeedEnc().setDecimal(true, edgeFlags, 10.0);

        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(100000, edgeFlags);

        assertEquals(375 * 60 * 1000, w.calcEdgeMillis(edge, false));
        assertEquals(600 * 60 * 1000, w.calcEdgeMillis(edge, true));

        g.close();
    }

    @Test
    public void calcWeightAndTime_withTurnCosts() {
        Graph graph = new GraphBuilder(encodingManager).create();
        Weighting weighting = new FastestWeighting(encoder, new DefaultTurnCostProvider(encoder, graph.getTurnCostStorage()));
        graph.edge(0, 1, 100, true);
        EdgeIteratorState edge = graph.edge(1, 2, 100, true);
        // turn costs are given in seconds
        setTurnCost(graph, 0, 1, 2, 5);
        assertEquals(6 + 5, GHUtility.calcWeightWithTurnWeight(weighting, edge, false, 0), 1.e-6);
        assertEquals(6000 + 5000, GHUtility.calcMillisWithTurnMillis(weighting, edge, false, 0), 1.e-6);
    }

    @Test
    public void calcWeightAndTime_uTurnCosts() {
        Graph graph = new GraphBuilder(encodingManager).create();
        Weighting weighting = new FastestWeighting(encoder, new DefaultTurnCostProvider(encoder, graph.getTurnCostStorage(), 40));
        EdgeIteratorState edge = graph.edge(0, 1, 100, true);
        assertEquals(6 + 40, GHUtility.calcWeightWithTurnWeight(weighting, edge, false, 0), 1.e-6);
        assertEquals((6 + 40) * 1000, GHUtility.calcMillisWithTurnMillis(weighting, edge, false, 0), 1.e-6);
    }

    @Test
    public void calcWeightAndTime_withTurnCosts_shortest() {
        Graph graph = new GraphBuilder(encodingManager).create();
        Weighting weighting = new ShortestWeighting(encoder, new DefaultTurnCostProvider(encoder, graph.getTurnCostStorage()));
        graph.edge(0, 1, 100, true);
        EdgeIteratorState edge = graph.edge(1, 2, 100, true);
        // turn costs are given in seconds
        setTurnCost(graph, 0, 1, 2, 5);
        // todo: for the shortest weighting turn costs cannot be interpreted as seconds? at least when they are added
        // to the weight? how much should they contribute ?
//        assertEquals(105, AbstractWeighting.calcWeightWithTurnWeight(weighting, edge, false, 0), 1.e-6);
        assertEquals(6000 + 5000, GHUtility.calcMillisWithTurnMillis(weighting, edge, false, 0), 1.e-6);
    }

    private void setTurnCost(Graph graph, int from, int via, int to, double turnCost) {
        graph.getTurnCostStorage().set(((EncodedValueLookup) encodingManager).getDecimalEncodedValue(TurnCost.key(encoder.toString())), getEdge(graph, from, via).getEdge(), via, getEdge(graph, via, to).getEdge(), turnCost);
    }

}
