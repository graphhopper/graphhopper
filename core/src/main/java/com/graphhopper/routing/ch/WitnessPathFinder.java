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
import com.graphhopper.util.GHUtility;

import java.util.BitSet;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Dummy implementation that probably needs to be replaced by something more efficient / use similar approach
 * as in {@link DijkstraOneToMany}.
 */
public class WitnessPathFinder {
    private IntObjectMap<WitnessSearchEntry> chEntries;
    private BitSet settledEntries;
    private PriorityQueue<WitnessSearchEntry> priorityQueue;
    private final CHGraph graph;
    private final Weighting weighting;
    private final TraversalMode traversalMode;
    private CHEdgeExplorer outEdgeExplorer;
    // todo: find good value here or adjust dynamically, if this is set too low important witnesses wont be found
    // and the number of shortcuts explodes, if it is too high the dijkstra searches take too long
    private int maxOrigEdgesSettled;
    private int numOrigEdgesSettled;
    private int numPossibleShortcuts;

    public WitnessPathFinder(CHGraph graph, Weighting weighting, TraversalMode traversalMode) {
        if (traversalMode != TraversalMode.EDGE_BASED_2DIR) {
            throw new IllegalArgumentException("Traversal mode " + traversalMode + "not supported");
        }
        this.graph = graph;
        this.weighting = weighting;
        this.traversalMode = traversalMode;
        outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(weighting.getFlagEncoder(), false, true));
        reset();
    }

    public void setInitialEntries(List<WitnessSearchEntry> initialEntries) {
        reset();
        initEntries(initialEntries);
        maxOrigEdgesSettled = initialEntries.size() * 5;
    }

    public CHEntry getFoundEntry(int edge, int adjNode) {
        int edgeKey = getEdgeKey(edge, adjNode);
        CHEntry entry = chEntries.get(edgeKey);
        return entry != null ? entry : new CHEntry(edge, edge, adjNode, Double.POSITIVE_INFINITY);
    }

    public void findTarget(int targetEdge, int targetNode) {
        boolean targetDiscoveredByShortcut = false;
        int targetKey = getEdgeKey(targetEdge, targetNode);
        if (settledEntries.get(targetKey)) {
            return;
        }

        while (!priorityQueue.isEmpty()) {
            WitnessSearchEntry currEdge = priorityQueue.poll();
            if (currEdge.incEdge == targetEdge && currEdge.adjNode == targetNode) {
                // put the entry back for future searches
                priorityQueue.add(currEdge);
                break;
            }

            if (currEdge.possibleShortcut) {
                numPossibleShortcuts--;
            }

            if (numOrigEdgesSettled > maxOrigEdgesSettled && !currEdge.possibleShortcut) {
                continue;
            }

            CHEdgeIterator iter = outEdgeExplorer.setBaseNode(currEdge.adjNode);
            while (iter.next()) {
                if ((!traversalMode.hasUTurnSupport() && iter.getFirstOrigEdge() == currEdge.incEdge) ||
                        graph.getLevel(iter.getAdjNode()) < graph.getLevel(iter.getBaseNode())) {
                    continue;
                }
                double weight = weighting.calcWeight(iter, false, currEdge.incEdge) + currEdge.weight;
                if (Double.isInfinite(weight)) {
                    continue;
                }

                int traversalId = getEdgeKey(iter.getLastOrigEdge(), iter.getAdjNode());
                WitnessSearchEntry entry = chEntries.get(traversalId);
                if (entry == null) {
                    entry = createEntry(iter, currEdge, weight);
                    if (currEdge.possibleShortcut && iter.getBaseNode() == iter.getAdjNode()) { 
                        entry.possibleShortcut = true;
                        numPossibleShortcuts++;
                    }
                    if (currEdge.possibleShortcut && iter.getLastOrigEdge() == targetEdge && iter.getAdjNode() == targetNode) {
                        targetDiscoveredByShortcut = true;
                    }
                    chEntries.put(traversalId, entry);
                    priorityQueue.add(entry);
                } else if (entry.weight > weight) {
                    priorityQueue.remove(entry);
                    updateEntry(entry, iter, weight, currEdge);
                    if (currEdge.possibleShortcut && iter.getBaseNode() == currEdge.adjNode && iter.getBaseNode() == iter.getAdjNode()) {
                        if (!entry.possibleShortcut) {
                            numPossibleShortcuts++;
                        }
                        entry.possibleShortcut = true;
                    }
                    priorityQueue.add(entry);
                }
            }
            settledEntries.set(getEdgeKey(currEdge.incEdge, currEdge.adjNode));
            numOrigEdgesSettled++;
            if (numPossibleShortcuts < 1 && !targetDiscoveredByShortcut) {
                break;
            }
        }
    }

    private int getEdgeKey(int edge, int adjNode) {
        // todo: this is similar to some code in EdgeBasedNodeContractor and should be cleaned up, see comments there
        CHEdgeIteratorState eis = graph.getEdgeIteratorState(edge, adjNode);
        return GHUtility.createEdgeKey(eis.getBaseNode(), eis.getAdjNode(), eis.getEdge(), false);
    }

    private void initEntries(List<WitnessSearchEntry> initialEntries) {
        for (WitnessSearchEntry entry : initialEntries) {
            if (entry.possibleShortcut) {
                numPossibleShortcuts++;
            }
            int traversalId = getEdgeKey(entry.incEdge, entry.adjNode);
            chEntries.put(traversalId, entry);
        }
        if (numPossibleShortcuts != 1) {
            throw new IllegalStateException("There should be exactly one initial entry with possibleShortcut = true, but given: " + numPossibleShortcuts);
        }
        priorityQueue.addAll(initialEntries);
        // todo: we do not remove/update duplicates anywhere, this is ok, because we take the initial entries from
        // the priority queue (and not chEntries, which would be wrong) and always get the one with the lowest
        // weight first. this can make problems if we change the algorithm though!
        // right now duplicates are not removed, because it seems costly
        if (priorityQueue.size() != chEntries.size()) {
//            throw new IllegalStateException("There are duplicate initial entries");
        }
    }
    
    private WitnessSearchEntry createEntry(CHEdgeIterator iter, CHEntry parent, double weight) {
        WitnessSearchEntry entry = new WitnessSearchEntry(iter.getEdge(), iter.getLastOrigEdge(), iter.getAdjNode(), weight);
        entry.parent = parent;
        return entry;
    }

    private void updateEntry(CHEntry entry, CHEdgeIterator iter, double weight, CHEntry parent) {
        entry.edge = iter.getEdge();
        entry.incEdge = iter.getLastOrigEdge();
        entry.weight = weight;
        entry.parent = parent;
    }

    private void reset() {
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
        numOrigEdgesSettled = 0;
        numPossibleShortcuts = 0;
    }

    private void initCollections(int size) {
        priorityQueue = new PriorityQueue<>(size);
        chEntries = new GHIntObjectHashMap<>(size);
        settledEntries = new GHBitSetImpl(size);
    }

}
