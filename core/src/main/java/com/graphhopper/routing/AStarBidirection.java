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

import com.graphhopper.routing.AStar.AStarEntry;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.ConsistentWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.*;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.PriorityQueue;

/**
 * This class implements a bidirectional A* algorithm. It is interesting to note that a
 * bidirectional dijkstra is far more efficient than a single direction one. The same does not hold
 * for a bidirectional A* as the heuristic can not be as tight.
 * <p>
 * See http://research.microsoft.com/apps/pubs/default.aspx?id=64511
 * http://i11www.iti.uni-karlsruhe.de/_media/teaching/sommer2012/routenplanung/vorlesung4.pdf
 * http://research.microsoft.com/pubs/64504/goldberg-sofsem07.pdf
 * http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
 * <p>
 * and
 * <p>
 * 1. Ikeda, T., Hsu, M.-Y., Imai, H., Nishimura, S., Shimoura, H., Hashimoto, T., Tenmoku, K., and
 * Mitoh, K. (1994). A fast algorithm for finding better routes by ai search techniques. In VNIS,
 * pages 291–296.
 * <p>
 * 2. Whangbo, T. K. (2007). Efficient modified bidirectional a* algorithm for optimal route-
 * finding. In IEA/AIE, volume 4570, pages 344–353. Springer.
 * <p>
 * or could we even use this three phase approach?
 * www.lix.polytechnique.fr/~giacomon/papers/bidirtimedep.pdf
 * <p>
 *
 * @author Peter Karich
 * @author jansoe
 */
public class AStarBidirection extends AbstractBidirAlgo {
    protected TIntObjectMap<AStarEntry> bestWeightMapFrom;
    protected TIntObjectMap<AStarEntry> bestWeightMapTo;
    protected AStarEntry currFrom;
    protected AStarEntry currTo;
    protected PathBidirRef bestPath;
    private ConsistentWeightApproximator weightApprox;
    private PriorityQueue<AStarEntry> prioQueueOpenSetFrom;
    private PriorityQueue<AStarEntry> prioQueueOpenSetTo;
    private TIntObjectMap<AStarEntry> bestWeightMapOther;

    public AStarBidirection(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
        BeelineWeightApproximator defaultApprox = new BeelineWeightApproximator(nodeAccess, weighting);
        defaultApprox.setDistanceCalc(new DistancePlaneProjection());
        setApproximation(defaultApprox);
    }

    protected void initCollections(int size) {
        prioQueueOpenSetFrom = new PriorityQueue<AStarEntry>(size);
        bestWeightMapFrom = new TIntObjectHashMap<AStarEntry>(size);

        prioQueueOpenSetTo = new PriorityQueue<AStarEntry>(size);
        bestWeightMapTo = new TIntObjectHashMap<AStarEntry>(size);
    }

    /**
     * @param approx if true it enables approximative distance calculation from lat,lon values
     */
    public AStarBidirection setApproximation(WeightApproximator approx) {
        weightApprox = new ConsistentWeightApproximator(approx);
        return this;
    }

    @Override
    protected SPTEntry createSPTEntry(int node, double weight) {
        throw new IllegalStateException("use AStarEdge constructor directly");
    }

    @Override
    public void initFrom(int from, double weight) {
        currFrom = new AStarEntry(EdgeIterator.NO_EDGE, from, weight, weight);
        weightApprox.setSourceNode(from);
        prioQueueOpenSetFrom.add(currFrom);

        if (currTo != null) {
            currFrom.weight += weightApprox.approximate(currFrom.adjNode, false);
            currTo.weight += weightApprox.approximate(currTo.adjNode, true);
        }

        if (!traversalMode.isEdgeBased()) {
            bestWeightMapFrom.put(from, currFrom);
            if (currTo != null) {
                bestWeightMapOther = bestWeightMapTo;
                updateBestPath(GHUtility.getEdge(graph, from, currTo.adjNode), currTo, from);
            }
        } else if (currTo != null && currTo.adjNode == from) {
            // special case of identical start and end
            bestPath.sptEntry = currFrom;
            bestPath.edgeTo = currTo;
            finishedFrom = true;
            finishedTo = true;
        }
    }

    @Override
    public void initTo(int to, double weight) {
        currTo = new AStarEntry(EdgeIterator.NO_EDGE, to, weight, weight);
        weightApprox.setGoalNode(to);
        prioQueueOpenSetTo.add(currTo);

        if (currFrom != null) {
            currFrom.weight += weightApprox.approximate(currFrom.adjNode, false);
            currTo.weight += weightApprox.approximate(currTo.adjNode, true);
        }

        if (!traversalMode.isEdgeBased()) {
            bestWeightMapTo.put(to, currTo);
            if (currFrom != null) {
                bestWeightMapOther = bestWeightMapFrom;
                updateBestPath(GHUtility.getEdge(graph, currFrom.adjNode, to), currFrom, to);
            }
        } else if (currFrom != null && currFrom.adjNode == to) {
            // special case of identical start and end
            bestPath.sptEntry = currFrom;
            bestPath.edgeTo = currTo;
            finishedFrom = true;
            finishedTo = true;
        }
    }

    @Override
    protected Path createAndInitPath() {
        bestPath = new PathBidirRef(graph, weighting);
        return bestPath;
    }

    @Override
    protected Path extractPath() {
        if (finished())
            return bestPath.extract();

        return bestPath;
    }

    @Override
    protected double getCurrentFromWeight() {
        return currFrom.weight;
    }

    @Override
    protected double getCurrentToWeight() {
        return currTo.weight;
    }

    @Override
    protected boolean finished() {
        if (finishedFrom || finishedTo)
            return true;

        // using 'weight' is important and correct here e.g. approximation can get negative and smaller than 'weightOfVisitedPath'
        return currFrom.weight + currTo.weight >= bestPath.getWeight();
    }

    @Override
    boolean fillEdgesFrom() {
        if (prioQueueOpenSetFrom.isEmpty())
            return false;

        currFrom = prioQueueOpenSetFrom.poll();
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, prioQueueOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
        visitedCountFrom++;
        return true;
    }

    @Override
    boolean fillEdgesTo() {
        if (prioQueueOpenSetTo.isEmpty())
            return false;

        currTo = prioQueueOpenSetTo.poll();
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, prioQueueOpenSetTo, bestWeightMapTo, inEdgeExplorer, true);
        visitedCountTo++;
        return true;
    }

    private void fillEdges(AStarEntry currEdge, PriorityQueue<AStarEntry> prioQueueOpenSet,
                           TIntObjectMap<AStarEntry> bestWeightMap, EdgeExplorer explorer, boolean reverse) {

        int currNode = currEdge.adjNode;
        EdgeIterator iter = explorer.setBaseNode(currNode);
        while (iter.next()) {
            if (!accept(iter, currEdge.edge))
                continue;

            int neighborNode = iter.getAdjNode();
            int traversalId = traversalMode.createTraversalId(iter, reverse);
            // TODO performance: check if the node is already existent in the opposite direction
            // then we could avoid the approximation as we already know the exact complete path!
            double alreadyVisitedWeight = weighting.calcWeight(iter, reverse, currEdge.edge)
                    + currEdge.getWeightOfVisitedPath();
            if (Double.isInfinite(alreadyVisitedWeight))
                continue;

            AStarEntry ase = bestWeightMap.get(traversalId);
            if (ase == null || ase.getWeightOfVisitedPath() > alreadyVisitedWeight) {
                double currWeightToGoal = weightApprox.approximate(neighborNode, reverse);
                double estimationFullWeight = alreadyVisitedWeight + currWeightToGoal;
                if (ase == null) {
                    ase = new AStarEntry(iter.getEdge(), neighborNode, estimationFullWeight, alreadyVisitedWeight);
                    bestWeightMap.put(traversalId, ase);
                } else {
                    assert (ase.weight > 0.999999 * estimationFullWeight) : "Inconsistent distance estimate "
                            + ase.weight + " vs " + estimationFullWeight + " (" + ase.weight / estimationFullWeight + "), and:"
                            + ase.getWeightOfVisitedPath() + " vs " + alreadyVisitedWeight + " (" + ase.getWeightOfVisitedPath() / alreadyVisitedWeight + ")";
                    prioQueueOpenSet.remove(ase);
                    ase.edge = iter.getEdge();
                    ase.weight = estimationFullWeight;
                    ase.weightOfVisitedPath = alreadyVisitedWeight;
                }

                ase.parent = currEdge;
                prioQueueOpenSet.add(ase);
                updateBestPath(iter, ase, traversalId);
            }
        }
    }

    public void updateBestPath(EdgeIteratorState edgeState, AStarEntry entryCurrent, int currLoc) {
        AStarEntry entryOther = bestWeightMapOther.get(currLoc);
        if (entryOther == null)
            return;

        boolean reverse = bestWeightMapFrom == bestWeightMapOther;
        // update μ
        double newWeight = entryCurrent.weightOfVisitedPath + entryOther.weightOfVisitedPath;
        if (traversalMode.isEdgeBased()) {
            if (entryOther.edge != entryCurrent.edge)
                throw new IllegalStateException("cannot happen for edge based execution of " + getName());

            // see DijkstraBidirectionRef
            if (entryOther.adjNode != entryCurrent.adjNode) {
                entryCurrent = (AStar.AStarEntry) entryCurrent.parent;
                newWeight -= weighting.calcWeight(edgeState, reverse, EdgeIterator.NO_EDGE);
            } else if (!traversalMode.hasUTurnSupport())
                // we detected a u-turn at meeting point, skip if not supported
                return;
        }

        if (newWeight < bestPath.getWeight()) {
            bestPath.setSwitchToFrom(reverse);
            bestPath.sptEntry = entryCurrent;
            bestPath.edgeTo = entryOther;
            bestPath.setWeight(newWeight);
        }
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.ASTAR_BI;
    }
}
