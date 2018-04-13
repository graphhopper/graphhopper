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

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.AStar.AStarEntry;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.ConsistentWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

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
public class AStarBidirection extends GenericDijkstraBidirection<AStarEntry> implements RecalculationHook {
    private ConsistentWeightApproximator weightApprox;
    private IntHashSet ignoreExplorationFrom = new IntHashSet();
    private IntHashSet ignoreExplorationTo = new IntHashSet();

    public AStarBidirection(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);
        BeelineWeightApproximator defaultApprox = new BeelineWeightApproximator(nodeAccess, weighting);
        defaultApprox.setDistanceCalc(Helper.DIST_PLANE);
        setApproximation(defaultApprox);
    }

    /**
     * @param approx if true it enables approximate distance calculation from lat,lon values
     */
    public AStarBidirection setApproximation(WeightApproximator approx) {
        weightApprox = new ConsistentWeightApproximator(approx);
        return this;
    }

    public WeightApproximator getApproximation() {
        return weightApprox.getApproximation();
    }

    @Override
    protected AStarEntry createStartEntry(int node, double weight, boolean reverse) {
        throw new IllegalStateException("use AStarEdge constructor directly");
    }

    @Override
    public void initFrom(int from, double weight) {
        currFrom = new AStarEntry(EdgeIterator.NO_EDGE, from, weight, weight);
        weightApprox.setFrom(from);
        pqOpenSetFrom.add(currFrom);

        if (currTo != null) {
            currFrom.weight += weightApprox.approximate(currFrom.adjNode, false);
            currTo.weight += weightApprox.approximate(currTo.adjNode, true);
        }

        if (!traversalMode.isEdgeBased()) {
            bestWeightMapFrom.put(from, currFrom);
            if (currTo != null) {
                bestWeightMapOther = bestWeightMapTo;
                updateBestPath(GHUtility.getEdge(graph, from, currTo.adjNode), currTo, from, false);
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
        weightApprox.setTo(to);
        pqOpenSetTo.add(currTo);

        if (currFrom != null) {
            currFrom.weight += weightApprox.approximate(currFrom.adjNode, false);
            currTo.weight += weightApprox.approximate(currTo.adjNode, true);
        }

        if (!traversalMode.isEdgeBased()) {
            bestWeightMapTo.put(to, currTo);
            if (currFrom != null) {
                bestWeightMapOther = bestWeightMapFrom;
                updateBestPath(GHUtility.getEdge(graph, currFrom.adjNode, to), currFrom, to, true);
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
    protected boolean finished() {
        // using 'weight' is important and correct here e.g. approximation can get negative and smaller than 'weightOfVisitedPath'
        return super.finished();
    }

    @Override
    boolean fillEdgesFrom() {
        if (pqOpenSetFrom.isEmpty())
            return false;

        currFrom = pqOpenSetFrom.poll();
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, pqOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
        visitedCountFrom++;
        return true;
    }

    @Override
    boolean fillEdgesTo() {
        if (pqOpenSetTo.isEmpty())
            return false;

        currTo = pqOpenSetTo.poll();
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, pqOpenSetTo, bestWeightMapTo, inEdgeExplorer, true);
        visitedCountTo++;
        return true;
    }

    private void fillEdges(AStarEntry currEdge, PriorityQueue<AStarEntry> prioQueue,
                           IntObjectMap<AStarEntry> bestWeightMap, EdgeExplorer explorer, boolean reverse) {
        EdgeIterator iter = explorer.setBaseNode(currEdge.adjNode);
        while (iter.next()) {
            if (!accept(iter, currEdge.edge))
                continue;

            int traversalId = traversalMode.createTraversalId(iter, reverse);
            if (!acceptTraversalId(traversalId, reverse)) {
                continue;
            }

            // TODO performance: check if the node is already existent in the opposite direction
            // then we could avoid the approximation as we already know the exact complete path!
            double weight = weighting.calcWeight(iter, reverse, currEdge.edge)
                    + currEdge.getWeightOfVisitedPath();
            if (Double.isInfinite(weight))
                continue;
            AStarEntry entry = bestWeightMap.get(traversalId);
            if (entry == null) {
                double currWeightToGoal = weightApprox.approximate(iter.getAdjNode(), reverse);
                double estimationFullWeight = weight + currWeightToGoal;
                entry = new AStarEntry(iter.getEdge(), iter.getAdjNode(), estimationFullWeight, weight);
                entry.parent = currEdge;
                bestWeightMap.put(traversalId, entry);
                prioQueue.add(entry);
            } else if (entry.getWeightOfVisitedPath() > weight) {
//                assert (ase.weight > 0.999999 * estimationFullWeight) : "Inconsistent distance estimate "
//                        + ase.weight + " vs " + estimationFullWeight + " (" + ase.weight / estimationFullWeight + "), and:"
//                        + ase.getWeightOfVisitedPath() + " vs " + alreadyVisitedWeight + " (" + ase.getWeightOfVisitedPath() / alreadyVisitedWeight + ")";
                prioQueue.remove(entry);
                entry.edge = iter.getEdge();
                double currWeightToGoal = weightApprox.approximate(iter.getAdjNode(), reverse);
                entry.weight = weight + currWeightToGoal;
                entry.weightOfVisitedPath = weight;
                entry.parent = currEdge;
                prioQueue.add(entry);
            } else
                continue;

            if (updateBestPath)
                updateBestPath(iter, entry, traversalId, reverse);
        }
    }

    protected boolean acceptTraversalId(int traversalId, boolean reverse) {
        // todo: ignoreExplorationFrom/To always stays empty (?!)
        IntHashSet ignoreExploration = reverse ? ignoreExplorationTo : ignoreExplorationFrom;
        return !ignoreExploration.contains(traversalId);

    }

    @Override
    public void updateBestPath(EdgeIteratorState edgeState, AStarEntry entryCurrent, int traversalId, boolean reverse) {
        AStarEntry entryOther = bestWeightMapOther.get(traversalId);
        if (entryOther == null)
            return;

        // update μ
        double newWeight = entryCurrent.getWeightOfVisitedPath() + entryOther.getWeightOfVisitedPath();
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
            bestPath.setSPTEntry(entryCurrent);
            bestPath.setSPTEntryTo(entryOther);
            bestPath.setWeight(newWeight);
        }
    }

    void setFromDataStructures(AStarBidirection astar) {
        pqOpenSetFrom = astar.pqOpenSetFrom;
        bestWeightMapFrom = astar.bestWeightMapFrom;
        finishedFrom = astar.finishedFrom;
        currFrom = astar.currFrom;
        visitedCountFrom = astar.visitedCountFrom;
        ignoreExplorationFrom = astar.ignoreExplorationFrom;
        weightApprox.setFrom(astar.currFrom.adjNode);
        // outEdgeExplorer
    }

    void setToDataStructures(AStarBidirection astar) {
        pqOpenSetTo = astar.pqOpenSetTo;
        bestWeightMapTo = astar.bestWeightMapTo;
        finishedTo = astar.finishedTo;
        currTo = astar.currTo;
        visitedCountTo = astar.visitedCountTo;
        ignoreExplorationTo = astar.ignoreExplorationTo;
        weightApprox.setTo(astar.currTo.adjNode);
        // inEdgeExplorer
    }

    @Override
    public void afterHeuristicChange(boolean forward, boolean backward) {
        if (forward) {

            // update PQ due to heuristic change (i.e. weight changed)
            if (!pqOpenSetFrom.isEmpty()) {
                // copy into temporary array to avoid pointer change of PQ
                AStarEntry[] entries = pqOpenSetFrom.toArray(new AStarEntry[pqOpenSetFrom.size()]);
                pqOpenSetFrom.clear();
                for (AStarEntry value : entries) {
                    value.weight = value.weightOfVisitedPath + weightApprox.approximate(value.adjNode, false);
                    // does not work for edge based
                    // ignoreExplorationFrom.add(value.adjNode);

                    pqOpenSetFrom.add(value);
                }
            }
        }

        if (backward) {
            if (!pqOpenSetTo.isEmpty()) {
                AStarEntry[] entries = pqOpenSetTo.toArray(new AStarEntry[pqOpenSetTo.size()]);
                pqOpenSetTo.clear();
                for (AStarEntry value : entries) {
                    value.weight = value.weightOfVisitedPath + weightApprox.approximate(value.adjNode, true);
                    // ignoreExplorationTo.add(value.adjNode);

                    pqOpenSetTo.add(value);
                }
            }
        }
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.ASTAR_BI + "|" + weightApprox;
    }
}
