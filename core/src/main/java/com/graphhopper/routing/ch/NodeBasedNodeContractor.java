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

import com.carrotsearch.hppc.IntContainer;
import com.graphhopper.storage.CHStorageBuilder;
import com.graphhopper.util.PMap;
import com.graphhopper.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.graphhopper.routing.ch.CHParameters.*;
import static com.graphhopper.util.Helper.nf;

class NodeBasedNodeContractor implements NodeContractor {
    private final CHPreparationGraph prepareGraph;
    private final Params params = new Params();
    // todo: maybe use a set to prevent duplicates instead?
    private List<Shortcut> shortcuts = new ArrayList<>();
    private CHStorageBuilder chBuilder;
    private PrepareGraphEdgeExplorer inEdgeExplorer;
    private PrepareGraphEdgeExplorer outEdgeExplorer;
    private PrepareGraphEdgeExplorer existingShortcutExplorer;
    private NodeBasedWitnessPathSearcher witnessPathSearcher;
    private int addedShortcutsCount;
    private long dijkstraCount;
    private final StopWatch dijkstraSW = new StopWatch();
    // meanDegree is the number of edges / number of nodes ratio of the graph, not really the average degree, because
    // each edge can exist in both directions
    private double meanDegree;
    // temporary counters used for priority calculation
    private int originalEdgesCount;
    private int shortcutsCount;

    NodeBasedNodeContractor(CHPreparationGraph prepareGraph, CHStorageBuilder chBuilder, PMap pMap) {
        this.prepareGraph = prepareGraph;
        extractParams(pMap);
        this.chBuilder = chBuilder;
    }

    private void extractParams(PMap pMap) {
        params.edgeDifferenceWeight = pMap.getFloat(EDGE_DIFFERENCE_WEIGHT, params.edgeDifferenceWeight);
        params.originalEdgesCountWeight = pMap.getFloat(ORIGINAL_EDGE_COUNT_WEIGHT, params.originalEdgesCountWeight);
        params.maxPollFactorHeuristic = pMap.getDouble(MAX_POLL_FACTOR_HEURISTIC_NODE, params.maxPollFactorHeuristic);
        params.maxPollFactorContraction = pMap.getDouble(MAX_POLL_FACTOR_CONTRACTION_NODE, params.maxPollFactorContraction);
    }

    @Override
    public void initFromGraph() {
        inEdgeExplorer = prepareGraph.createInEdgeExplorer();
        outEdgeExplorer = prepareGraph.createOutEdgeExplorer();
        existingShortcutExplorer = prepareGraph.createOutEdgeExplorer();
        witnessPathSearcher = new NodeBasedWitnessPathSearcher(prepareGraph);
        meanDegree = prepareGraph.getOriginalEdges() * 1.0 / prepareGraph.getNodes();
    }

    @Override
    public void close() {
        prepareGraph.close();
        shortcuts = null;
        chBuilder = null;
        inEdgeExplorer = null;
        outEdgeExplorer = null;
        existingShortcutExplorer = null;
        witnessPathSearcher = null;
    }

    /**
     * Warning: the calculated priority must NOT depend on priority(v) and therefore findAndHandleShortcuts should also not
     * depend on the priority(v). Otherwise updating the priority before contracting in contractNodes() could lead to
     * a slowish or even endless loop.
     */
    @Override
    public float calculatePriority(int node) {
        // # huge influence: the bigger the less shortcuts gets created and the faster is the preparation
        //
        // every adjNode has an 'original edge' number associated. initially it is r=1
        // when a new shortcut is introduced then r of the associated edges is summed up:
        // r(u,w)=r(u,v)+r(v,w) now we can define
        // originalEdgesCount = σ(v) := sum_{ (u,w) ∈ shortcuts(v) } of r(u, w)
        shortcutsCount = 0;
        originalEdgesCount = 0;
        findAndHandleShortcuts(node, this::countShortcuts, (int) (meanDegree * params.maxPollFactorHeuristic));

        // from shortcuts we can compute the edgeDifference
        // # low influence: with it the shortcut creation is slightly faster
        //
        // |shortcuts(v)| − |{(u, v) | v uncontracted}| − |{(v, w) | v uncontracted}|
        // meanDegree is used instead of outDegree+inDegree as if one adjNode is in both directions
        // only one bucket memory is used. Additionally one shortcut could also stand for two directions.
        int edgeDifference = shortcutsCount - prepareGraph.getDegree(node);

        // according to the paper do a simple linear combination of the properties to get the priority.
        return params.edgeDifferenceWeight * edgeDifference +
                params.originalEdgesCountWeight * originalEdgesCount;
        // todo: maybe use contracted-neighbors heuristic (contract nodes with lots of contracted neighbors later) as in GH 1.0 again?
        //       maybe use hierarchy-depths heuristic as in edge-based?
    }

    @Override
    public IntContainer contractNode(int node) {
        long degree = findAndHandleShortcuts(node, this::addOrUpdateShortcut, (int) (meanDegree * params.maxPollFactorContraction));
        insertShortcuts(node);
        // put weight factor on meanDegree instead of taking the average => meanDegree is more stable
        meanDegree = (meanDegree * 2 + degree) / 3;
        return prepareGraph.disconnect(node);
    }

    /**
     * Calls the shortcut handler for all edges and shortcuts adjacent to the given node. After this method is called
     * these edges and shortcuts will be removed from the prepare graph, so this method offers the last chance to deal
     * with them.
     */
    private void insertShortcuts(int node) {
        shortcuts.clear();
        insertOutShortcuts(node);
        insertInShortcuts(node);
        int origEdges = prepareGraph.getOriginalEdges();
        for (Shortcut sc : shortcuts) {
            int shortcut = chBuilder.addShortcutNodeBased(sc.from, sc.to, sc.flags, sc.weight, sc.skippedEdge1, sc.skippedEdge2);
            if (sc.flags == PrepareEncoder.getScFwdDir()) {
                prepareGraph.setShortcutForPrepareEdge(sc.prepareEdgeFwd, origEdges + shortcut);
            } else if (sc.flags == PrepareEncoder.getScBwdDir()) {
                prepareGraph.setShortcutForPrepareEdge(sc.prepareEdgeBwd, origEdges + shortcut);
            } else {
                prepareGraph.setShortcutForPrepareEdge(sc.prepareEdgeFwd, origEdges + shortcut);
                prepareGraph.setShortcutForPrepareEdge(sc.prepareEdgeBwd, origEdges + shortcut);
            }
        }
        addedShortcutsCount += shortcuts.size();
    }

    private void insertOutShortcuts(int node) {
        PrepareGraphEdgeIterator iter = outEdgeExplorer.setBaseNode(node);
        while (iter.next()) {
            if (!iter.isShortcut())
                continue;
            shortcuts.add(new Shortcut(iter.getPrepareEdge(), -1, node, iter.getAdjNode(), iter.getSkipped1(),
                    iter.getSkipped2(), PrepareEncoder.getScFwdDir(), iter.getWeight()));
        }
    }

    private void insertInShortcuts(int node) {
        PrepareGraphEdgeIterator iter = inEdgeExplorer.setBaseNode(node);
        while (iter.next()) {
            if (!iter.isShortcut())
                continue;

            int skippedEdge1 = iter.getSkipped2();
            int skippedEdge2 = iter.getSkipped1();
            // we check if this shortcut already exists (with the same weight) for the other direction and if so we can use
            // it for both ways instead of adding another one
            boolean bidir = false;
            for (Shortcut sc : shortcuts) {
                if (sc.to == iter.getAdjNode()
                        && Double.doubleToLongBits(sc.weight) == Double.doubleToLongBits(iter.getWeight())
                        // todo: can we not just compare skippedEdges?
                        && prepareGraph.getShortcutForPrepareEdge(sc.skippedEdge1) == prepareGraph.getShortcutForPrepareEdge(skippedEdge1)
                        && prepareGraph.getShortcutForPrepareEdge(sc.skippedEdge2) == prepareGraph.getShortcutForPrepareEdge(skippedEdge2)
                        && sc.flags == PrepareEncoder.getScFwdDir()) {
                    sc.flags = PrepareEncoder.getScDirMask();
                    sc.prepareEdgeBwd = iter.getPrepareEdge();
                    bidir = true;
                    break;
                }
            }
            if (!bidir) {
                shortcuts.add(new Shortcut(-1, iter.getPrepareEdge(), node, iter.getAdjNode(), skippedEdge1, skippedEdge2, PrepareEncoder.getScBwdDir(), iter.getWeight()));
            }
        }
    }

    @Override
    public void finishContraction() {
        // during contraction the skip1/2 edges of shortcuts refer to the prepare edge-ids *not* the final shortcut
        // ids (because they are not known before the insertion) -> we need to re-map these ids here
        chBuilder.replaceSkippedEdges(prepareGraph::getShortcutForPrepareEdge);
    }

    @Override
    public String getStatisticsString() {
        return String.format(Locale.ROOT, "meanDegree: %.2f, dijkstras: %10s, mem: %10s",
                meanDegree, nf(dijkstraCount), witnessPathSearcher.getMemoryUsageAsString());
    }

    /**
     * Searches for shortcuts and calls the given handler on each shortcut that is found. The graph is not directly
     * changed by this method.
     * Returns the 'degree' of the given node (disregarding edges from/to already contracted nodes).
     * Note that here the degree is not the total number of adjacent edges, but only the number of incoming edges
     */
    private long findAndHandleShortcuts(int node, PrepareShortcutHandler handler, int maxVisitedNodes) {
        long degree = 0;
        PrepareGraphEdgeIterator incomingEdges = inEdgeExplorer.setBaseNode(node);
        // collect outgoing nodes (goal-nodes) only once
        while (incomingEdges.next()) {
            int fromNode = incomingEdges.getAdjNode();
            // there should be no loops
            if (fromNode == node) throw new AssertionError();

            final double incomingEdgeWeight = incomingEdges.getWeight();
            // this check is important to prevent calling calcMillis on inaccessible edges and also allows early exit
            if (Double.isInfinite(incomingEdgeWeight)) {
                continue;
            }
            // collect outgoing nodes (goal-nodes) only once
            PrepareGraphEdgeIterator outgoingEdges = outEdgeExplorer.setBaseNode(node);
            witnessPathSearcher.init(fromNode, node);
            degree++;
            while (outgoingEdges.next()) {
                int toNode = outgoingEdges.getAdjNode();
                // no need to search for witnesses going from a node back to itself
                if (fromNode == toNode)
                    continue;

                // Limit weight as ferries or forbidden edges can increase local search too much.
                // If we decrease the correct weight we only explore less and introduce more shortcuts.
                // I.e. no change to accuracy is made.
                double existingDirectWeight = incomingEdgeWeight + outgoingEdges.getWeight();
                if (Double.isInfinite(existingDirectWeight))
                    continue;

                dijkstraSW.start();
                dijkstraCount++;
                double maxWeight = witnessPathSearcher.findUpperBound(toNode, existingDirectWeight, maxVisitedNodes);
                dijkstraSW.stop();

                if (maxWeight <= existingDirectWeight)
                    // FOUND witness path, so do not add shortcut
                    continue;

                handler.handleShortcut(fromNode, toNode, existingDirectWeight,
                        outgoingEdges.getPrepareEdge(), outgoingEdges.getOrigEdgeCount(),
                        incomingEdges.getPrepareEdge(), incomingEdges.getOrigEdgeCount());
            }
        }
        return degree;
    }

    private void countShortcuts(int fromNode, int toNode, double existingDirectWeight,
                                int outgoingEdge, int outOrigEdgeCount,
                                int incomingEdge, int inOrigEdgeCount) {
        shortcutsCount++;
        originalEdgesCount += inOrigEdgeCount + outOrigEdgeCount;
    }

    private void addOrUpdateShortcut(int fromNode, int toNode, double weight,
                                     int outgoingEdge, int outOrigEdgeCount,
                                     int incomingEdge, int inOrigEdgeCount) {
        boolean exists = false;
        PrepareGraphEdgeIterator iter = existingShortcutExplorer.setBaseNode(fromNode);
        while (iter.next()) {
            // do not update base edges!
            if (iter.getAdjNode() != toNode || !iter.isShortcut()) {
                continue;
            }
            exists = true;
            if (weight < iter.getWeight()) {
                iter.setWeight(weight);
                iter.setSkippedEdges(incomingEdge, outgoingEdge);
                iter.setOrigEdgeCount(inOrigEdgeCount + outOrigEdgeCount);
            }
        }
        if (!exists)
            prepareGraph.addShortcut(fromNode, toNode, -1, -1, incomingEdge, outgoingEdge, weight, inOrigEdgeCount + outOrigEdgeCount);
    }

    @Override
    public long getAddedShortcutsCount() {
        return addedShortcutsCount;
    }

    @Override
    public float getDijkstraSeconds() {
        return dijkstraSW.getCurrentSeconds();
    }

    @FunctionalInterface
    private interface PrepareShortcutHandler {
        void handleShortcut(int fromNode, int toNode, double existingDirectWeight,
                            int outgoingEdge, int outOrigEdgeCount,
                            int incomingEdge, int inOrigEdgeCount);
    }

    public static class Params {
        // default values were optimized for Unterfranken
        private float edgeDifferenceWeight = 10;
        private float originalEdgesCountWeight = 1;
        // these values seemed to work best for planet (fast prep without compromising too much for the query time)
        // higher values can further decrease the number of shortcuts and improve the query time, but normally at the
        // cost of a longer preparation (see #2514)
        private double maxPollFactorHeuristic = 5;
        private double maxPollFactorContraction = 200;
    }

    private static class Shortcut {
        int prepareEdgeFwd;
        int prepareEdgeBwd;
        int from;
        int to;
        int skippedEdge1;
        int skippedEdge2;
        double weight;
        int flags;

        public Shortcut(int prepareEdgeFwd, int prepareEdgeBwd, int from, int to, int skippedEdge1, int skippedEdge2, int flags, double weight) {
            this.prepareEdgeFwd = prepareEdgeFwd;
            this.prepareEdgeBwd = prepareEdgeBwd;
            this.from = from;
            this.to = to;
            this.skippedEdge1 = skippedEdge1;
            this.skippedEdge2 = skippedEdge2;
            this.flags = flags;
            this.weight = weight;
        }

        @Override
        public String toString() {
            String str;
            if (flags == PrepareEncoder.getScDirMask())
                str = from + "<->";
            else
                str = from + "->";

            return str + to + ", weight:" + weight + " (" + skippedEdge1 + "," + skippedEdge2 + ")";
        }
    }
}
