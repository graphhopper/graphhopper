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
package com.graphhopper.routing;

import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

import java.util.PriorityQueue;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;

/**
 * This class implements the A* algorithm according to
 * http://en.wikipedia.org/wiki/A*_search_algorithm
 * <p>
 * Different distance calculations can be used via setApproximation.
 * <p>
 *
 * @author Peter Karich
 */
public class AStar extends AbstractRoutingAlgorithm implements EdgeToEdgeRoutingAlgorithm {
    private GHIntObjectHashMap<AStarEntry> fromMap;
    private PriorityQueue<AStarEntry> fromHeap;
    private AStarEntry currEdge;
    private int visitedNodes;
    private int to = -1;
    private WeightApproximator weightApprox;
    private int fromOutEdge;
    private int toInEdge;

    public AStar(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
        BeelineWeightApproximator defaultApprox = new BeelineWeightApproximator(nodeAccess, weighting);
        defaultApprox.setDistanceCalc(DistancePlaneProjection.DIST_PLANE);
        setApproximation(defaultApprox);
    }

    /**
     * @param approx defines how distance to goal Node is approximated
     */
    public AStar setApproximation(WeightApproximator approx) {
        weightApprox = approx;
        return this;
    }

    protected void initCollections(int size) {
        fromMap = new GHIntObjectHashMap<>();
        fromHeap = new PriorityQueue<>(size);
    }

    @Override
    public Path calcPath(int from, int to) {
        return calcPath(from, to, EdgeIterator.ANY_EDGE, EdgeIterator.ANY_EDGE);
    }

    @Override
    public Path calcPath(int from, int to, int fromOutEdge, int toInEdge) {
        if ((fromOutEdge != ANY_EDGE || toInEdge != ANY_EDGE) && !traversalMode.isEdgeBased()) {
            throw new IllegalArgumentException("Restricting the start/target edges is only possible for edge-based graph traversal");
        }
        this.fromOutEdge = fromOutEdge;
        this.toInEdge = toInEdge;
        checkAlreadyRun();
        this.to = to;
        if (fromOutEdge == NO_EDGE || toInEdge == NO_EDGE)
            return extractPath();
        weightApprox.setTo(to);
        double weightToGoal = weightApprox.approximate(from);
        if (Double.isInfinite(weightToGoal))
            return extractPath();
        AStarEntry startEntry = new AStarEntry(EdgeIterator.NO_EDGE, from, 0 + weightToGoal, 0);
        fromHeap.add(startEntry);
        if (!traversalMode.isEdgeBased())
            fromMap.put(from, currEdge);
        runAlgo();
        return extractPath();
    }

    private void runAlgo() {
        double currWeightToGoal, estimationFullWeight;
        while (!fromHeap.isEmpty()) {
            currEdge = fromHeap.poll();
            if (currEdge.isDeleted())
                continue;
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int currNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            while (iter.next()) {
                if (!accept(iter, currEdge.edge) || (currEdge.edge == NO_EDGE && fromOutEdge != ANY_EDGE && iter.getEdge() != fromOutEdge))
                    continue;

                double tmpWeight = GHUtility.calcWeightWithTurnWeightWithAccess(weighting, iter, false, currEdge.edge) + currEdge.weightOfVisitedPath;
                if (Double.isInfinite(tmpWeight)) {
                    continue;
                }
                int traversalId = traversalMode.createTraversalId(iter, false);

                AStarEntry ase = fromMap.get(traversalId);
                if (ase == null || ase.weightOfVisitedPath > tmpWeight) {
                    int neighborNode = iter.getAdjNode();
                    currWeightToGoal = weightApprox.approximate(neighborNode);
                    if (Double.isInfinite(currWeightToGoal))
                        continue;
                    estimationFullWeight = tmpWeight + currWeightToGoal;
                    if (ase == null) {
                        ase = new AStarEntry(iter.getEdge(), neighborNode, estimationFullWeight, tmpWeight, currEdge);
                        fromMap.put(traversalId, ase);
                    } else {
//                        assert (ase.weight > 0.9999999 * estimationFullWeight) : "Inconsistent distance estimate. It is expected weight >= estimationFullWeight but was "
//                                + ase.weight + " < " + estimationFullWeight + " (" + ase.weight / estimationFullWeight + "), and weightOfVisitedPath:"
//                                + ase.weightOfVisitedPath + " vs. alreadyVisitedWeight:" + alreadyVisitedWeight + " (" + ase.weightOfVisitedPath / alreadyVisitedWeight + ")";
                        ase.setDeleted();
                        ase = new AStarEntry(iter.getEdge(), neighborNode, estimationFullWeight, tmpWeight, currEdge);
                        fromMap.put(traversalId, ase);
                    }
                    fromHeap.add(ase);
                    updateBestPath(iter, ase, traversalId);
                }
            }
        }
    }

    private boolean finished() {
        return currEdge.adjNode == to && (toInEdge == ANY_EDGE || currEdge.edge == toInEdge) && (fromOutEdge == ANY_EDGE || currEdge.edge != NO_EDGE);
    }

    protected Path extractPath() {
        if (currEdge == null || !finished())
            return createEmptyPath();

        return PathExtractor.extractPath(graph, weighting, currEdge)
                // the path extractor uses currEdge.weight to set the weight, but this is the one that includes the
                // A* approximation, not the weight of the visited path! this is still correct, because the approximation
                // at the to-node (the end of the route) must be zero. Still it seems clearer to set the weight explicitly.
                .setWeight(currEdge.getWeightOfVisitedPath());
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry bestSPTEntry, int traversalId) {
    }

    public static class AStarEntry extends SPTEntry {
        double weightOfVisitedPath;

        public AStarEntry(int edgeId, int adjNode, double weightForHeap, double weightOfVisitedPath) {
            this(edgeId, adjNode, weightForHeap, weightOfVisitedPath, null);
        }

        public AStarEntry(int edgeId, int adjNode, double weightForHeap, double weightOfVisitedPath, SPTEntry parent) {
            super(edgeId, adjNode, weightForHeap, parent);
            this.weightOfVisitedPath = weightOfVisitedPath;
        }

        @Override
        public final double getWeightOfVisitedPath() {
            return weightOfVisitedPath;
        }

        @Override
        public AStarEntry getParent() {
            return (AStarEntry) parent;
        }
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.ASTAR + "|" + weightApprox;
    }
}
