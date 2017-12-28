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
package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.util.CHEdgeExplorer;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.BitSet;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Dummy implementation that probably needs to be replaced by something more efficient / use similar approach
 * as in {@link DijkstraOneToMany}.
 */
public class WitnessPathFinder {
    private IntObjectMap<CHEntry> chEntries;
    private BitSet settledEntries;
    private PriorityQueue<CHEntry> priorityQueue;
    private final CHGraph graph;
    private final Weighting weighting;
    private final TraversalMode traversalMode;
    private CHEdgeExplorer outEdgeExplorer;

    public WitnessPathFinder(CHGraph graph, Weighting weighting, TraversalMode traversalMode, List<CHEntry> initialEntries, int fromNode) {
        if (traversalMode != TraversalMode.EDGE_BASED_2DIR) {
            throw new IllegalArgumentException("Traversal mode " + traversalMode + "not supported");
        }
        this.graph = graph;
        this.weighting = weighting;
        this.traversalMode = traversalMode;
        outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(weighting.getFlagEncoder(), false, true));
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
        initEntries(initialEntries);
        priorityQueue.addAll(initialEntries);
    }

    private void initEntries(List<CHEntry> initialEntries) {
        for (CHEntry chEntry : initialEntries) {
            int traversalId = getEdgeKey(chEntry.incEdge, chEntry.adjNode);
            chEntries.put(traversalId, chEntry);
        }
    }

    public CHEntry getFoundEntry(int edge, int adjNode) {
        int edgeKey = getEdgeKey(edge, adjNode);
        CHEntry entry = chEntries.get(edgeKey);
        return entry != null ? entry : new CHEntry(edge, edge, adjNode, Double.POSITIVE_INFINITY);
    }

    public void findTarget(int targetEdge, int targetNode) {
        // todo: clean-up & optimize
        int edgeKey = getEdgeKey(targetEdge, targetNode);
        if (settledEntries.get(edgeKey)) {
            return;
        }

        while (!priorityQueue.isEmpty()) {
            CHEntry currEdge = priorityQueue.poll();
            if (currEdge.incEdge == targetEdge && currEdge.adjNode == targetNode) {
                // put the entry back for future searches
                priorityQueue.add(currEdge);
                break;
            }

            CHEdgeIterator iter = outEdgeExplorer.setBaseNode(currEdge.adjNode);
            while (iter.next()) {
                if ((!traversalMode.hasUTurnSupport() && iter.getLastOrigEdge() == currEdge.incEdge) ||
                        graph.getLevel(iter.getAdjNode()) < graph.getLevel(iter.getBaseNode()))
                    continue;

                int edgeId = iter.getLastOrigEdge();
                EdgeIteratorState iterState = graph.getEdgeIteratorState(edgeId, iter.getAdjNode());
                int traversalId = traversalMode.createTraversalId(iterState, false);
                final int origEdgeId = iter.getFirstOrigEdge();
                if (!traversalMode.hasUTurnSupport() && origEdgeId == currEdge.incEdge) {
                    continue;
                }
                double weight = weighting.calcWeight(iter, false, currEdge.incEdge) + currEdge.weight;
                if (Double.isInfinite(weight))
                    continue;

                CHEntry entry = chEntries.get(traversalId);
                if (entry == null) {
                    entry = createEntry(iter, currEdge, weight);
                    chEntries.put(traversalId, entry);
                    priorityQueue.add(entry);
                } else if (entry.weight > weight) {
                    priorityQueue.remove(entry);
                    updateEntry(entry, iter, weight, currEdge);
                    priorityQueue.add(entry);
                }
            }
            settledEntries.set(getEdgeKey(currEdge.incEdge, currEdge.adjNode));
        }
    }

    private int getEdgeKey(int edge, int adjNode) {
        // todo: this is similar to some code in EdgeBasedNodeContractor and should be cleaned up, see comments there
        CHEdgeIteratorState eis = graph.getEdgeIteratorState(edge, adjNode);
        return GHUtility.createEdgeKey(eis.getBaseNode(), eis.getAdjNode(), eis.getEdge(), false);
    }

    private CHEntry createEntry(CHEdgeIterator iter, CHEntry parent, double weight) {
        CHEntry entry = new CHEntry(iter.getEdge(), iter.getLastOrigEdge(), iter.getAdjNode(), weight);
        entry.parent = parent;
        return entry;
    }

    private void updateEntry(CHEntry entry, CHEdgeIterator iter, double weight, CHEntry parent) {
        entry.edge = iter.getEdge();
        entry.incEdge = iter.getLastOrigEdge();
        entry.weight = weight;
        entry.parent = parent;
    }

    private void initCollections(int size) {
        priorityQueue = new PriorityQueue<>(size);
        chEntries = new GHIntObjectHashMap<>(size);
        settledEntries = new GHBitSetImpl(size);
    }

}
