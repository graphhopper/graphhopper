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
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Parameters;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.PriorityQueue;

/**
 * Implements a single source shortest path algorithm
 * http://en.wikipedia.org/wiki/Dijkstra's_algorithm
 * <p>
 *
 * @author Peter Karich
 */
public class Dijkstra extends AbstractTimeDependentRoutingAlgorithm {
    protected TIntObjectMap<SPTEntry> fromMap;
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
        fromHeap = new PriorityQueue<SPTEntry>(size);
        fromMap = new TIntObjectHashMap<SPTEntry>(size);
    }

    @Override
    public Path calcPath(int from, int to, int earliestDepartureTime) {
        checkAlreadyRun();
        this.to = to;
        currEdge = createSPTEntry(from, earliestDepartureTime);
        if (!traversalMode.isEdgeBased()) {
            fromMap.put(from, currEdge);
        }
        runAlgo();
        return extractPath(earliestDepartureTime);
    }

    @Override
    public Path calcPath(int from, int to) {
        return calcPath(from, to, 0);
    }

    protected void runAlgo() {
        EdgeExplorer explorer = outEdgeExplorer;
        while (true) {
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int startNode = currEdge.adjNode;
            EdgeIterator iter = explorer.setBaseNode(startNode);
            while (iter.next()) {
                if (!accept(iter, currEdge.edge))
                    continue;

                int traversalId = traversalMode.createTraversalId(iter, false);
                double tmpWeight;
                if (weighting instanceof TimeDependentWeighting) {
                    tmpWeight = ((TimeDependentWeighting) weighting).calcWeight(iter, false, currEdge.edge, currEdge.weight) + currEdge.weight;
                } else {
                    tmpWeight = weighting.calcWeight(iter, false, currEdge.edge) + currEdge.weight;
                }
                if (Double.isInfinite(tmpWeight))
                    continue;

                SPTEntry nEdge = fromMap.get(traversalId);
                if (nEdge == null) {
                    nEdge = new SPTEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight);
                    nEdge.parent = currEdge;
                    fromMap.put(traversalId, nEdge);
                    fromHeap.add(nEdge);
                } else if (nEdge.weight > tmpWeight) {
                    fromHeap.remove(nEdge);
                    nEdge.edge = iter.getEdge();
                    nEdge.weight = tmpWeight;
                    nEdge.parent = currEdge;
                    fromHeap.add(nEdge);
                } else
                    continue;

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
    protected boolean finished() {
        return currEdge.adjNode == to;
    }

    @Override
    protected Path extractPath() {
        if (currEdge == null || !finished())
            return createEmptyPath();

        return new Path(graph, weighting).setWeight(currEdge.weight).setSPTEntry(currEdge).extract();
    }

    @Override
    protected Path extractPath(int earliestDepartureTime) {
        if (currEdge == null || !finished())
            return createEmptyPath();

        return new Path(graph, weighting).setWeight(currEdge.weight).setSPTEntry(currEdge).extract();
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.DIJKSTRA;
    }
}
