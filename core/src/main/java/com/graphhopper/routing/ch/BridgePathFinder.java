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

import com.carrotsearch.hppc.HashOrderMixing;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.IntObjectScatterMap;

import java.util.PriorityQueue;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.GHUtility.getEdgeFromEdgeKey;

public class BridgePathFinder {
    private final CHPreparationGraph graph;
    private final PrepareGraphEdgeExplorer outExplorer;
    private final PrepareGraphOrigEdgeExplorer origOutExplorer;
    private final PriorityQueue<PrepareCHEntry> queue;
    private final IntObjectMap<PrepareCHEntry> map;

    public BridgePathFinder(CHPreparationGraph graph) {
        this.graph = graph;
        outExplorer = graph.createOutEdgeExplorer();
        origOutExplorer = graph.createOutOrigEdgeExplorer();
        queue = new PriorityQueue<>();
        map = new IntObjectScatterMap<>();
    }

    public IntObjectMap<BridePathEntry> find(int startInEdgeKey, int startNode, int centerNode) {
        queue.clear();
        map.clear();
        IntObjectMap<BridePathEntry> result = new IntObjectHashMap<>(16, 0.5, HashOrderMixing.constant(123));
        PrepareCHEntry startEntry = new PrepareCHEntry(NO_EDGE, startInEdgeKey, startInEdgeKey, startNode, 0, 0);
        startEntry.firstEdgeKey = startInEdgeKey;
        map.put(startInEdgeKey, startEntry);
        queue.add(startEntry);
        while (!queue.isEmpty()) {
            PrepareCHEntry currEntry = queue.poll();
            PrepareGraphEdgeIterator iter = outExplorer.setBaseNode(currEntry.adjNode);
            while (iter.next()) {
                if (iter.getAdjNode() == centerNode) {
                    double weight = currEntry.weight + graph.getTurnWeight(getEdgeFromEdgeKey(currEntry.incEdgeKey),
                            currEntry.adjNode, getEdgeFromEdgeKey(iter.getOrigEdgeKeyFirst())) + iter.getWeight();
                    if (Double.isInfinite(weight))
                        continue;
                    PrepareCHEntry entry = map.get(iter.getOrigEdgeKeyLast());
                    if (entry == null) {
                        entry = new PrepareCHEntry(iter.getPrepareEdge(), iter.getOrigEdgeKeyFirst(), iter.getOrigEdgeKeyLast(), iter.getAdjNode(), weight, currEntry.origEdges + iter.getOrigEdgeCount());
                        entry.parent = currEntry;
                        map.put(iter.getOrigEdgeKeyLast(), entry);
                        queue.add(entry);
                    } else if (weight < entry.weight) {
                        queue.remove(entry);
                        entry.prepareEdge = iter.getPrepareEdge();
                        entry.origEdges = currEntry.origEdges + iter.getOrigEdgeCount();
                        entry.firstEdgeKey = iter.getOrigEdgeKeyFirst();
                        entry.weight = weight;
                        entry.parent = currEntry;
                        queue.add(entry);
                    }
                } else if (currEntry.adjNode == centerNode) {
                    PrepareGraphOrigEdgeIterator origOutIter = origOutExplorer.setBaseNode(iter.getAdjNode());
                    while (origOutIter.next()) {
                        double weight = currEntry.weight + graph.getTurnWeight(
                                getEdgeFromEdgeKey(currEntry.incEdgeKey), currEntry.adjNode, getEdgeFromEdgeKey(iter.getOrigEdgeKeyFirst()))
                                + iter.getWeight();
                        double totalWeight = weight + graph.getTurnWeight(
                                getEdgeFromEdgeKey(iter.getOrigEdgeKeyLast()), iter.getAdjNode(), getEdgeFromEdgeKey(origOutIter.getOrigEdgeKeyFirst()));
                        if (Double.isInfinite(totalWeight))
                            continue;
                        BridePathEntry resEntry = result.get(origOutIter.getOrigEdgeKeyFirst());
                        if (resEntry == null) {
                            PrepareCHEntry chEntry = new PrepareCHEntry(iter.getPrepareEdge(), iter.getOrigEdgeKeyFirst(), iter.getOrigEdgeKeyLast(), iter.getAdjNode(), weight, currEntry.origEdges + iter.getOrigEdgeCount());
                            chEntry.parent = currEntry;
                            resEntry = new BridePathEntry(totalWeight, chEntry);
                            result.put(origOutIter.getOrigEdgeKeyFirst(), resEntry);
                        } else if (totalWeight < resEntry.weight) {
                            resEntry.weight = totalWeight;
                            resEntry.chEntry.prepareEdge = iter.getPrepareEdge();
                            resEntry.chEntry.firstEdgeKey = iter.getOrigEdgeKeyFirst();
                            resEntry.chEntry.origEdges = currEntry.origEdges + iter.getOrigEdgeCount();
                            resEntry.chEntry.incEdgeKey = iter.getOrigEdgeKeyLast();
                            resEntry.chEntry.weight = weight;
                            resEntry.chEntry.parent = currEntry;
                        }
                    }
                }
            }
        }
        return result;
    }

    public static class BridePathEntry {
        double weight;
        PrepareCHEntry chEntry;

        public BridePathEntry(double weight, PrepareCHEntry chEntry) {
            this.weight = weight;
            this.chEntry = chEntry;
        }

        @Override
        public String toString() {
            return "weight: " + weight + ", chEntry: " + chEntry;
        }
    }
}
