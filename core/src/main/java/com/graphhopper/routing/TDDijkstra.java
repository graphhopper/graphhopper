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
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Parameters;

/**
 * Implements time-dependent Dijkstra algorithm
 * <p>
 *
 * @author Peter Karich
 * @author Michael Zilske
 * @author Andrzej Oles
 */
public class TDDijkstra extends Dijkstra {

    public TDDijkstra(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);

        if (!weighting.isTimeDependent())
            throw new RuntimeException("A time-dependent routing algorithm requires a time-dependent weighting.");
    }

    @Override
    public Path calcPath(int from, int to, long at) {
        checkAlreadyRun();
        int source = reverseDirection ? to : from;
        int target = reverseDirection ? from : to;
        this.to = target;
        currEdge = new SPTEntry(source, 0);
        currEdge.time = at;
        if (!traversalMode.isEdgeBased()) {
            fromMap.put(source, currEdge);
        }
        runAlgo();
        return extractPath();
    }

    @Override
    protected void runAlgo() {
        while (true) {
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int currNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            while (iter.next()) {
                if (!accept(iter, currEdge.edge))
                    continue;

                double tmpWeight = GHUtility.calcWeightWithTurnWeightWithAccess(weighting, iter, reverseDirection, currEdge.edge, currEdge.time) + currEdge.weight;
                if (Double.isInfinite(tmpWeight)) {
                    continue;
                }
                int traversalId = traversalMode.createTraversalId(iter, reverseDirection);

                SPTEntry nEdge = fromMap.get(traversalId);
                if (nEdge == null) {
                    nEdge = new SPTEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight);
                    fromMap.put(traversalId, nEdge);
                } else if (nEdge.weight > tmpWeight) {
                    fromHeap.remove(nEdge);
                    nEdge.edge = iter.getEdge();
                    nEdge.weight = tmpWeight;
                } else
                    continue;

                nEdge.parent = currEdge;
                nEdge.time = (reverseDirection ? -1 : 1) * weighting.calcEdgeMillis(iter, reverseDirection, currEdge.time) + currEdge.time;
                fromHeap.add(nEdge);

                updateBestPath(iter, nEdge, traversalId);
            }

            if (fromHeap.isEmpty())
                break;

            currEdge = fromHeap.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");
        }
    }

    @Override
    protected Path extractPath() {
        if (currEdge == null || !finished())
            return createEmptyPath();

        return TDPathExtractor.extractPath(graph, weighting, currEdge, reverseDirection);
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.TD_DIJKSTRA;
    }

    public void reverse() {
        reverseDirection = !reverseDirection;
    }
}
