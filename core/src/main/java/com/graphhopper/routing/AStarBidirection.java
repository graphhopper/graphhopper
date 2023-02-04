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
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BalancedWeightApproximator;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.core.util.Parameters;

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
public class AStarBidirection extends AbstractNonCHBidirAlgo {
    private BalancedWeightApproximator weightApprox;
    double stoppingCriterionOffset;

    public AStarBidirection(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);
        BeelineWeightApproximator defaultApprox = new BeelineWeightApproximator(nodeAccess, weighting);
        defaultApprox.setDistanceCalc(DistancePlaneProjection.DIST_PLANE);
        setApproximation(defaultApprox);
    }

    @Override
    void init(int from, double fromWeight, int to, double toWeight) {
        weightApprox.setFromTo(from, to);
        stoppingCriterionOffset = weightApprox.approximate(to, true) + weightApprox.getSlack();
        super.init(from, fromWeight, to, toWeight);
    }

    @Override
    protected boolean finished() {
        if (finishedFrom || finishedTo)
            return true;

        return currFrom.weight + currTo.weight >= bestWeight + stoppingCriterionOffset;
    }

    @Override
    protected SPTEntry createStartEntry(int node, double weight, boolean reverse) {
        double heapWeight = weight + weightApprox.approximate(node, reverse);
        return new AStarEntry(EdgeIterator.NO_EDGE, node, heapWeight, weight);
    }

    @Override
    protected SPTEntry createEntry(EdgeIteratorState edge, double weight, SPTEntry parent, boolean reverse) {
        int neighborNode = edge.getAdjNode();
        double heapWeight = weight + weightApprox.approximate(neighborNode, reverse);
        return new AStarEntry(edge.getEdge(), neighborNode, heapWeight, weight, parent);
    }

    @Override
    protected double calcWeight(EdgeIteratorState iter, SPTEntry currEdge, boolean reverse) {
        // TODO performance: check if the node is already existent in the opposite direction
        // then we could avoid the approximation as we already know the exact complete path!
        return super.calcWeight(iter, currEdge, reverse);
    }

    public WeightApproximator getApproximation() {
        return weightApprox.getApproximation();
    }

    public AStarBidirection setApproximation(WeightApproximator approx) {
        weightApprox = new BalancedWeightApproximator(approx);
        return this;
    }

    @Override
    void setToDataStructures(AbstractBidirAlgo other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.ASTAR_BI + "|" + weightApprox;
    }
}
