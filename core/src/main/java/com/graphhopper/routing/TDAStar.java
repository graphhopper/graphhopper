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

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

/**
 * Implements time-dependent A* algorithm
 * <p>
 *
 * @author Peter Karich
 * @author Michael Zilske
 * @author Andrzej Oles
 */
public class TDAStar extends AStar {
    private boolean reverse = false;

    public TDAStar(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);

        if (!weighting.isTimeDependent())
            throw new RuntimeException("A time-dependent routing algorithm requires a time-dependent weighting.");
    }

    @Override
    public Path calcPath(int from, int to, long at) {
        checkAlreadyRun();
        int source = reverse ? to : from;
        int target = reverse ? from : to;
        this.to = target;
        weightApprox.setTo(target);
        double weightToGoal = weightApprox.approximate(source);
        currEdge = new AStarEntry(EdgeIterator.NO_EDGE, source, 0 + weightToGoal, 0);
        currEdge.time = at;
        if (!traversalMode.isEdgeBased()) {
            fromMap.put(source, currEdge);
        }
        return runAlgo();
    }

    private Path runAlgo() {
        double currWeightToGoal, estimationFullWeight;
        while (true) {
            visitedNodes++;
            if (isMaxVisitedNodesExceeded())
                return createEmptyPath();

            if (finished())
                break;

            int currNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            while (iter.next()) {
                if (!accept(iter, currEdge.edge))
                    continue;

                double tmpWeight = GHUtility.calcWeightWithTurnWeightWithAccess(weighting, iter, reverse, currEdge.edge, currEdge.time) + currEdge.weightOfVisitedPath;
                if (Double.isInfinite(tmpWeight))
                    continue;

                int traversalId = traversalMode.createTraversalId(iter, reverse);
                AStarEntry ase = fromMap.get(traversalId);
                if (ase == null || ase.weightOfVisitedPath > tmpWeight) {
                    int neighborNode = iter.getAdjNode();
                    currWeightToGoal = weightApprox.approximate(neighborNode);
                    estimationFullWeight = tmpWeight + currWeightToGoal;
                    if (ase == null) {
                        ase = new AStarEntry(iter.getEdge(), neighborNode, estimationFullWeight, tmpWeight);
                        fromMap.put(traversalId, ase);
                    } else {
                        fromHeap.remove(ase);
                        ase.edge = iter.getEdge();
                        ase.weight = estimationFullWeight;
                        ase.weightOfVisitedPath = tmpWeight;
                    }
                    ase.time = currEdge.time + (reverse ? -1 : 1) * weighting.calcEdgeMillis(iter, reverse, currEdge.time);
                    ase.parent = currEdge;
                    fromHeap.add(ase);

                    updateBestPath(iter, ase, traversalId);
                }
            }

            if (fromHeap.isEmpty())
                return createEmptyPath();

            currEdge = fromHeap.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");
        }

        return extractPath();
    }

    @Override
    protected Path extractPath() {
        if (currEdge == null || !finished())
            return createEmptyPath();

        return TDPathExtractor.extractPath(graph, weighting, currEdge, reverse);
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.TD_ASTAR + "|" + weightApprox;
    }

    public void reverse() {
        reverse = !reverse;
        weightApprox = weightApprox.reverse();
    }
}
