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

package com.graphhopper.routing.lm;

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.BBox;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LMApproximatorTest {

    @RepeatedTest(value = 10)
    public void randomGraph() {
        final long seed = System.nanoTime();
        runRandomGraph(seed);
    }

    private void runRandomGraph(long seed) {
        FlagEncoder encoder = FlagEncoders.createCar(new PMap("turn_costs=true|speed_two_directions=true"));
        EncodingManager encodingManager = new EncodingManager.Builder().add(encoder).add(Subnetwork.create("car")).build();
        BaseGraph graph = new BaseGraph.Builder(encodingManager).create();

        Random rnd = new Random(seed);
        // todo: try with more nodes and also using one-ways (pBothDir<1) and different speeds (speed=null)
        //       and maybe even with loops (or maybe not, because we don't want loops anyway)
        GHUtility.buildRandomGraph(graph, rnd, 20, 2.2, false, true,
                encoder.getAccessEnc(), encoder.getAverageSpeedEnc(), 60.0, 0, 1.0, 0);

        // todo: maybe we should also test this with turn costs, just setting 'turn_costs=true' above doesn't really do anything
        Weighting weighting = new FastestWeighting(encoder);

        // todo: try with more landmarks, but first make it work with one...
        int landmarks = 1;
        LandmarkStorage landmarkStorage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", weighting), landmarks);
        landmarkStorage.setMaximumWeight(10000);
        landmarkStorage.createLandmarks();

        // create query graph
        LocationIndexTree locationIndex = new LocationIndexTree(graph, graph.getDirectory());
        locationIndex.prepareIndex();
        BBox bounds = graph.getBounds();
        final int numSnaps = 10;
        List<Snap> snaps = new ArrayList<>();
        for (int i = 0; i < numSnaps; i++) {
            double lat = rnd.nextDouble() * (bounds.maxLat - bounds.minLat) + bounds.minLat;
            double lon = rnd.nextDouble() * (bounds.maxLon - bounds.minLon) + bounds.minLon;
            Snap snap = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
            snaps.add(snap);
        }
        QueryGraph queryGraph = QueryGraph.create(graph, snaps);

        for (int t = 0; t < queryGraph.getNodes(); t++) {
            LMApproximator lmApproximator = LMApproximator.forLandmarks(queryGraph, landmarkStorage, Math.min(landmarks, 8));
            WeightApproximator reverseLmApproximator = lmApproximator.reverse();
            BeelineWeightApproximator beelineApproximator = new BeelineWeightApproximator(queryGraph.getNodeAccess(), weighting);
            WeightApproximator reverseBeelineApproximator = beelineApproximator.reverse();
            PerfectApproximator perfectApproximator = new PerfectApproximator(queryGraph, weighting, TraversalMode.NODE_BASED, false);
            PerfectApproximator reversePerfectApproximator = new PerfectApproximator(queryGraph, weighting, TraversalMode.NODE_BASED, true);
            BalancedWeightApproximator balancedWeightApproximator = new BalancedWeightApproximator(lmApproximator);

            lmApproximator.setTo(t);
            reverseLmApproximator.setTo(t);
            beelineApproximator.setTo(t);
            reverseBeelineApproximator.setTo(t);
            perfectApproximator.setTo(t);
            reversePerfectApproximator.setTo(t);
            balancedWeightApproximator.setFromTo(0, t);

            int nOverApproximatedWeights = 0;
            int nInconsistentWeights = 0;
            for (int v = 0; v < queryGraph.getNodes(); v++) {
                Dijkstra dijkstra = new Dijkstra(queryGraph, weighting, TraversalMode.NODE_BASED);
                Path path = dijkstra.calcPath(v, t);
                if (path.isFound()) {
                    // admissibility: the approximators must never overapproximate the remaining weight to the target
                    double realRemainingWeight = path.getWeight();
                    double approximatedRemainingWeight = lmApproximator.approximate(v);
                    if (approximatedRemainingWeight > realRemainingWeight) {
                        System.out.printf("LM: %f\treal: %f\n", approximatedRemainingWeight, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }
                    // Give the beelineApproximator some slack, because the map distance of an edge
                    // can be _smaller_ than its Euklidean distance, due to rounding.
                    double slack = path.getEdgeCount() * (1 / 1000.0);
                    double beelineApproximatedRemainingWeight = beelineApproximator.approximate(v);
                    if (beelineApproximatedRemainingWeight - slack > realRemainingWeight) {
                        System.out.printf("beeline: %f\treal: %f\n", beelineApproximatedRemainingWeight, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }
                    double perfectApproximatedRemainingWeight = perfectApproximator.approximate(v);
                    if (perfectApproximatedRemainingWeight > realRemainingWeight) {
                        System.out.printf("perfect: %f\treal: %f\n", perfectApproximatedRemainingWeight, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }

                    // Triangle inequality for approximator. This is what makes it 'feasible' (or 'monotone/consistent').
                    // That's a requirement for normal A*-implementations, because if it is violated,
                    // the heap-weight of settled nodes can decrease. In this case nodes potentially have to be expanded
                    // multiple times as we do not know for certain the shortest path weight of a settled node. And that
                    // could mean our stopping criterion is not sufficient.
                    EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(v);
                    while (iter.next()) {
                        int w = iter.getAdjNode();
                        double vw = weighting.calcEdgeWeightWithAccess(iter, false);
                        double vwApprox = lmApproximator.approximate(v) - lmApproximator.approximate(w);
                        // we do not try to enforce the inequality relation strictly, but allow a numeric tolerance.
                        // this is required because we do not store the LM weights exactly, and we account for this
                        // imprecision in the stopping criterion of the bidirectional A* algorithm. But the imprecision
                        // must be limited to a known extent. An approximately feasible approximator is still much
                        // better than an infeasible one.
                        double reducedWeight = vw - vwApprox + lmApproximator.getSlack();
                        if (reducedWeight < 0) {
                            boolean virtual = queryGraph.isVirtualNode(iter.getBaseNode()) || queryGraph.isVirtualNode(iter.getAdjNode());
                            System.out.printf("LM not feasible%s! reduced weight: %f\tvw: %f\tvwApprox: %f\tslack: %f\n", (virtual ? " on query graph" : ""), reducedWeight, vw, vwApprox, lmApproximator.getSlack());
                            nInconsistentWeights++;
                        }
                    }

                    iter = queryGraph.createEdgeExplorer().setBaseNode(v);
                    while (iter.next()) {
                        int w = iter.getAdjNode();
                        double vw = weighting.calcEdgeWeightWithAccess(iter, false);
                        double vwApprox = balancedWeightApproximator.approximate(v, false) - balancedWeightApproximator.approximate(w, false);
                        double reducedWeight = vw - vwApprox + balancedWeightApproximator.getSlack();
                        if (reducedWeight < 0) {
                            boolean virtual = queryGraph.isVirtualNode(iter.getBaseNode()) || queryGraph.isVirtualNode(iter.getAdjNode());
                            System.out.printf("Balanced not feasible%s! reduced weight: %f\tw: %f\tvwApprox: %f\tslack: %f\n", (virtual ? " on query graph" : ""), reducedWeight, vw, vwApprox, balancedWeightApproximator.getSlack());
                            nInconsistentWeights++;
                        }
                    }
                }
                Dijkstra reverseDijkstra = new Dijkstra(queryGraph, weighting, TraversalMode.NODE_BASED);
                Path reversePath = reverseDijkstra.calcPath(t, v);
                if (reversePath.isFound()) {
                    double realRemainingWeight = reversePath.getWeight();
                    double approximatedRemainingWeight = reverseLmApproximator.approximate(v);
                    if (approximatedRemainingWeight > realRemainingWeight) {
                        System.out.printf("LM: %f\treal: %f\n", approximatedRemainingWeight, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }
                    // Give the beelineApproximator some slack, because the map distance of an edge
                    // can be _smaller_ than its Euklidean distance, due to rounding.
                    double slack = reversePath.getEdgeCount() * (1 / 1000.0);
                    double beelineApproximatedRemainingWeight = reverseBeelineApproximator.approximate(v);
                    if (beelineApproximatedRemainingWeight - slack > realRemainingWeight) {
                        System.out.printf("beeline: %f\treal: %f\n", beelineApproximatedRemainingWeight, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }
                    double approximate = reversePerfectApproximator.approximate(v);
                    if (approximate > realRemainingWeight) {
                        System.out.printf("perfect: %f\treal: %f\n", approximate, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }
                }

            }

            assertEquals(0, nOverApproximatedWeights, "too many over approximated weights: " + nOverApproximatedWeights + ", seed: " + seed);
            assertEquals(0, nInconsistentWeights, "too many inconsistent weights, " + nInconsistentWeights + ", seed: " + seed);
        }
    }

    @Test
    void virtualNode() {
        // 0-1-snap-2-LM

        // case 1: the snap is closer to node 2, i.e. closer to the node that is closer to the landmark
        checkVirtualNode(52.536227, 13.417928);
        // case 2: the snap is closer to node 1, i.e. closer to the node that is further away from the landmark
        checkVirtualNode(52.535715, 13.417343);
    }

    void checkVirtualNode(double lat, double lon) {
        FlagEncoder encoder = FlagEncoders.createCar(new PMap("speed_two_directions=true"));
        EncodingManager encodingManager = new EncodingManager.Builder().add(encoder).add(Subnetwork.create("car")).build();
        BaseGraph graph = new BaseGraph.Builder(encodingManager).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 52.532344, 13.413556);
        na.setNode(1, 52.535372, 13.416989);
        na.setNode(2, 52.536599, 13.418319);
        na.setNode(3, 52.539405, 13.421431);
        BiConsumer<Integer, Integer> addEdge = (from, to) ->
                GHUtility.setSpeed(60, 60, encoder, graph.edge(from, to).setDistance(GHUtility.getDistance(from, to, na)));
        // 0-1-2-3
        addEdge.accept(0, 1);
        addEdge.accept(1, 2);
        addEdge.accept(2, 3);

        // we create a landmark at node 3
        final int landmarkNode = 3;
        Weighting weighting = new FastestWeighting(encoder);
        LandmarkStorage landmarkStorage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", weighting), 1);
        landmarkStorage.setLandmarkSuggestions(Arrays.asList(
                new LandmarkSuggestion(Arrays.asList(landmarkNode), new BBox(10, 15, 50, 55))
        ));
        landmarkStorage.createLandmarks();

        LocationIndexTree locationIndex = new LocationIndexTree(graph, graph.getDirectory());
        locationIndex.prepareIndex();

        Snap snap = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        // we want a snap that snaps between nodes 1 and 2
        assertEquals(1, snap.getClosestEdge().getEdge());
        assertEquals(1, snap.getClosestEdge().getBaseNode());
        assertEquals(2, snap.getClosestEdge().getAdjNode());
        QueryGraph queryGraph = QueryGraph.create(graph, snap);

        IntToDoubleFunction getExactWeight = from -> new Dijkstra(queryGraph, weighting, TraversalMode.NODE_BASED).calcPath(from, landmarkNode).getWeight();
        LMApproximator lmApproximator = LMApproximator.forLandmarks(queryGraph, landmarkStorage, 1);
        lmApproximator.setTo(landmarkNode);
        // make sure the approximator never over-approximates
        for (int i = 0; i < 5; i++)
            assertTrue(lmApproximator.approximate(i) <= getExactWeight.applyAsDouble(i));

        // check feasibility
        BiFunction<Integer, Integer, Optional<String>> checkFeasibility = (u, v) -> {
            double w_uv = weighting.calcEdgeWeight(GHUtility.getEdge(queryGraph, u, v), false);
            double h_u = lmApproximator.approximate(u);
            double h_v = lmApproximator.approximate(v);
            boolean feasible = w_uv + h_v - h_u + lmApproximator.getSlack() >= 0;
            return feasible ? Optional.empty() : Optional.of("Feasibility condition violated, u=" + u + ", v=" + v + ", w_uv=" + w_uv + ", h_u=" + h_u + ", h_v=" + h_v + ", slack: " + lmApproximator.getSlack() + "\n");
        };
        List<String> errors = new ArrayList<>();
        // 0-1-4-2-3, node 4 is the virtual one
        checkFeasibility.apply(4, 2).ifPresent(errors::add);
        checkFeasibility.apply(4, 1).ifPresent(errors::add);
        checkFeasibility.apply(2, 4).ifPresent(errors::add);
        checkFeasibility.apply(1, 4).ifPresent(errors::add);
        assertTrue(errors.isEmpty(), errors.toString());
    }
}