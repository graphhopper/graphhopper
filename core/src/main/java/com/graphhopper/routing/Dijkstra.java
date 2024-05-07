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

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Parameters;

import java.util.PriorityQueue;

/**
 * Implements a single source shortest path algorithm
 * http://en.wikipedia.org/wiki/Dijkstra's_algorithm
 * <p>
 *
 * @author Peter Karich
 */
public class Dijkstra extends AbstractRoutingAlgorithm {
    protected IntObjectMap<SPTEntry> fromMap;
    protected PriorityQueue<SPTEntry> fromHeap;
    protected SPTEntry currEdge;
    private int visitedNodes;
    private int to = -1;

    public Dijkstra(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
    }

    protected void initCollections(int size) {
        fromHeap = new PriorityQueue<>(size);
        fromMap = new GHIntObjectHashMap<>(size);
    }

    @Override
    public Path calcPath(int from, int to) {
        checkAlreadyRun();
        setupFinishTime();
        this.to = to;
        SPTEntry startEntry = new SPTEntry(from, 0);
        fromHeap.add(startEntry);
        if (!traversalMode.isEdgeBased())
            fromMap.put(from, currEdge);
        runAlgo();
        return extractPath();
    }

    protected void runAlgo() {
        while (!fromHeap.isEmpty()) {
            currEdge = fromHeap.poll();
            if (currEdge.isDeleted())
                continue;
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished() || isTimeoutExceeded())
                break;

            int currNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            while (iter.next()) {
                if (!accept(iter, currEdge.edge))
                    continue;

                double tmpWeight = GHUtility.calcWeightWithTurnWeight(weighting, iter, false, currEdge.edge) + currEdge.weight;
                if (Double.isInfinite(tmpWeight)) {
                    continue;
                }
                int traversalId = traversalMode.createTraversalId(iter, false);

                SPTEntry nEdge = fromMap.get(traversalId);
                if (nEdge == null) {
                    nEdge = new SPTEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight, currEdge);
                    fromMap.put(traversalId, nEdge);
                    fromHeap.add(nEdge);
                } else if (nEdge.weight > tmpWeight) {
                    nEdge.setDeleted();
                    nEdge = new SPTEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight, currEdge);
                    fromMap.put(traversalId, nEdge);
                    fromHeap.add(nEdge);
                } else
                    continue;

                updateBestPath(iter, nEdge, traversalId);
            }
        }
    }

    protected boolean finished() {
        return currEdge.adjNode == to;
    }

    private Path extractPath() {
        if (currEdge == null || !finished())
            return createEmptyPath();

        return PathExtractor.extractPath(graph, weighting, currEdge);
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry bestSPTEntry, int traversalId) {
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.DIJKSTRA;
    }
}
