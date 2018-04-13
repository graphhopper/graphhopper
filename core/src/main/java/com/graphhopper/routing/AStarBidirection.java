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
import com.graphhopper.routing.AStar.AStarEntry;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.ConsistentWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

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

    @Override
    void init(int from, double fromWeight, int to, double toWeight) {
        weightApprox.setFrom(from);
        weightApprox.setTo(to);
        super.init(from, fromWeight, to, toWeight);
    }

    @Override
    protected boolean finished() {
        // using 'weight' is important and correct here e.g. approximation can get negative and smaller than 'weightOfVisitedPath'
        return super.finished();
    }

    @Override
    protected AStarEntry createStartEntry(int node, double weight, boolean reverse) {
        double heapWeight = weight + weightApprox.approximate(node, reverse);
        return new AStarEntry(EdgeIterator.NO_EDGE, node, heapWeight, weight);
    }

    @Override
    protected AStarEntry createEntry(EdgeIteratorState iter, double weight, AStarEntry parent, boolean reverse) {
        double heapWeight = weight + weightApprox.approximate(iter.getAdjNode(), reverse);
        AStarEntry entry = new AStarEntry(iter.getEdge(), iter.getAdjNode(), heapWeight, weight);
        entry.parent = parent;
        return entry;
    }

    @Override
    protected void updateEntry(AStarEntry entry, EdgeIteratorState iter, double weight, AStarEntry parent, boolean reverse) {
        entry.edge = iter.getEdge();
        entry.weight = weight + weightApprox.approximate(iter.getAdjNode(), reverse);
        entry.weightOfVisitedPath = weight;
        entry.parent = parent;
    }

    @Override
    protected boolean acceptTraversalId(int traversalId, boolean reverse) {
        // todo: ignoreExplorationFrom/To always stays empty (?!)
        IntHashSet ignoreExploration = reverse ? ignoreExplorationTo : ignoreExplorationFrom;
        return !ignoreExploration.contains(traversalId);
    }

    @Override
    protected double calcWeight(EdgeIteratorState iter, AStarEntry currEdge, boolean reverse) {
        // TODO performance: check if the node is already existent in the opposite direction
        // then we could avoid the approximation as we already know the exact complete path!
        return super.calcWeight(iter, currEdge, reverse);
    }

    @Override
    protected AStarEntry getParent(AStarEntry entry) {
        return entry.getParent();
    }

    public WeightApproximator getApproximation() {
        return weightApprox.getApproximation();
    }

    /**
     * @param approx if true it enables approximate distance calculation from lat,lon values
     */
    public AStarBidirection setApproximation(WeightApproximator approx) {
        weightApprox = new ConsistentWeightApproximator(approx);
        return this;
    }

    void setFromDataStructures(AStarBidirection astar) {
        super.setFromDataStructures(astar);
        ignoreExplorationFrom = astar.ignoreExplorationFrom;
        weightApprox.setFrom(astar.currFrom.adjNode);
    }

    void setToDataStructures(AStarBidirection astar) {
        super.setToDataStructures(astar);
        ignoreExplorationTo = astar.ignoreExplorationTo;
        weightApprox.setTo(astar.currTo.adjNode);
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
