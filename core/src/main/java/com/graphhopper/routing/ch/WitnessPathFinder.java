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
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.BitSet;
import java.util.PriorityQueue;

public class WitnessPathFinder {
    public static int maxOrigEdgesPerInitialEntry = 5;
    private final CHGraph graph;
    private final Weighting weighting;
    private final TraversalMode traversalMode;
    private final int maxLevel;
    private final EdgeExplorer outEdgeExplorer;
    private IntObjectMap<WitnessSearchEntry> chEntries;
    private BitSet settledEntries;
    private PriorityQueue<WitnessSearchEntry> priorityQueue;
    private int maxOrigEdgesSettled;
    private int numOrigEdgesSettled;
    private int numPossibleShortcuts;
    private boolean targetDiscoveredByShortcut;

    public WitnessPathFinder(CHGraph graph, Weighting weighting, TraversalMode traversalMode, int maxLevel) {
        if (traversalMode != TraversalMode.EDGE_BASED_2DIR) {
            throw new IllegalArgumentException("Traversal mode " + traversalMode + "not supported");
        }
        this.graph = graph;
        this.weighting = weighting;
        this.traversalMode = traversalMode;
        this.maxLevel = maxLevel;
        this.outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(weighting.getFlagEncoder(), false, true));
        initCollections();
    }

    public void setInitialEntries(IntObjectMap<WitnessSearchEntry> initialEntries) {
        reset();
        initEntries(initialEntries);
        maxOrigEdgesSettled = initialEntries.size() * maxOrigEdgesPerInitialEntry;
    }

    public CHEntry getFoundEntry(int origEdge, int adjNode) {
        int edgeKey = getEdgeKey(origEdge, adjNode);
        return chEntries.get(edgeKey);
    }

    public CHEntry getFoundEntryNoParents(int edge, int adjNode) {
        return getFoundEntry(edge, adjNode);
    }

    public void findTarget(int targetEdge, int targetNode) {
        targetDiscoveredByShortcut = false;
        int targetKey = getEdgeKey(targetEdge, targetNode);
        if (settledEntries.get(targetKey)) {
            return;
        }

        while (!priorityQueue.isEmpty()) {
            WitnessSearchEntry currEdge = priorityQueue.peek();
            if (currEdge.incEdge == targetEdge && currEdge.adjNode == targetNode) {
                break;
            }

            currEdge = priorityQueue.poll();

            if (currEdge.possibleShortcut) {
                numPossibleShortcuts--;
            }

            if (numOrigEdgesSettled > maxOrigEdgesSettled && !currEdge.possibleShortcut) {
                continue;
            }

            EdgeIterator iter = outEdgeExplorer.setBaseNode(currEdge.adjNode);
            while (iter.next()) {
                if ((!traversalMode.hasUTurnSupport() && iter.getFirstOrigEdge() == currEdge.incEdge) ||
                        isContracted(iter.getAdjNode())) {
                    continue;
                }
                double weight = weighting.calcWeight(iter, false, currEdge.incEdge) + currEdge.weight;
                if (Double.isInfinite(weight)) {
                    continue;
                }

                boolean possibleShortcut = currEdge.possibleShortcut && iter.getBaseNode() == iter.getAdjNode();
                int traversalId = getEdgeKey(iter.getLastOrigEdge(), iter.getAdjNode());
                WitnessSearchEntry entry = chEntries.get(traversalId);
                if (entry == null) {
                    entry = createEntry(iter, currEdge, weight, possibleShortcut);
                    updateTargetDiscoveredByShortcutFlag(targetEdge, targetNode, currEdge, iter);
                    chEntries.put(traversalId, entry);
                    priorityQueue.add(entry);
                } else if (entry.weight > weight) {
                    priorityQueue.remove(entry);
                    updateEntry(entry, iter, weight, currEdge, possibleShortcut);
                    updateTargetDiscoveredByShortcutFlag(targetEdge, targetNode, currEdge, iter);
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

    private void initEntries(IntObjectMap<WitnessSearchEntry> initialEntries) {
        for (IntObjectCursor<WitnessSearchEntry> e : initialEntries) {
            if (e.value.possibleShortcut) {
                numPossibleShortcuts++;
            }
            chEntries.put(e.key, e.value);
            priorityQueue.add(e.value);
        }
        if (numPossibleShortcuts != 1) {
            throw new IllegalStateException("There should be exactly one initial entry with possibleShortcut = true, but given: " + numPossibleShortcuts);
        }
    }

    private WitnessSearchEntry createEntry(EdgeIteratorState iter, CHEntry parent, double weight, boolean possibleShortcut) {
        WitnessSearchEntry entry = new WitnessSearchEntry(iter.getEdge(), iter.getLastOrigEdge(), iter.getAdjNode(), weight);
        entry.parent = parent;
        if (possibleShortcut) {
            entry.possibleShortcut = true;
            numPossibleShortcuts++;
        }
        return entry;
    }

    private void updateEntry(WitnessSearchEntry entry, EdgeIteratorState iter, double weight, CHEntry parent, boolean possibleShortcut) {
        entry.edge = iter.getEdge();
        entry.incEdge = iter.getLastOrigEdge();
        entry.weight = weight;
        entry.parent = parent;
        if (possibleShortcut) {
            if (!entry.possibleShortcut) {
                numPossibleShortcuts++;
            }
            entry.possibleShortcut = true;
        }
    }

    private void updateTargetDiscoveredByShortcutFlag(int targetEdge, int targetNode, WitnessSearchEntry currEdge, EdgeIteratorState iter) {
        if (currEdge.possibleShortcut && iter.getLastOrigEdge() == targetEdge && iter.getAdjNode() == targetNode) {
            targetDiscoveredByShortcut = true;
        }
    }

    private void reset() {
        maxOrigEdgesSettled = Integer.MAX_VALUE;
        numOrigEdgesSettled = 0;
        numPossibleShortcuts = 0;
        initCollections();
    }

    private void initCollections() {
        // todo: tune initial collection sizes
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        priorityQueue = new PriorityQueue<>(size);
        chEntries = new GHIntObjectHashMap<>(size);
        settledEntries = new GHBitSetImpl(size);
    }

    private int getEdgeKey(int edge, int adjNode) {
        // todo: this is similar to some code in DijkstraBidirectionEdgeCHNoSOD and should be cleaned up, see comments there
        EdgeIteratorState eis = graph.getEdgeIteratorState(edge, adjNode);
        return GHUtility.createEdgeKey(eis.getBaseNode(), eis.getAdjNode(), eis.getEdge(), false);
    }

    private boolean isContracted(int node) {
        return graph.getLevel(node) != maxLevel;
    }
}
