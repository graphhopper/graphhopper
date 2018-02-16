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

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EdgeBasedNodeContractor extends AbstractNodeContractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeBasedNodeContractor.class);
    // todo: modify code such that logging does not alter performance 
    private final TurnWeighting turnWeighting;
    private final TraversalMode traversalMode;
    private int[] hierarchyDepths;
    private WitnessPathFinder witnessPathFinder;
    private CHEdgeExplorer toNodeInEdgeExplorer;
    private CHEdgeExplorer scExplorer;
    private CHEdgeExplorer allCHExplorer;
    private EdgeExplorer fromNodeOrigInEdgeExplorer;
    private EdgeExplorer toNodeOrigOutEdgeExplorer;
    private EdgeExplorer loopAvoidanceInEdgeExplorer;
    private EdgeExplorer loopAvoidanceOutEdgeExplorer;
    private WitnessSearchStrategy witnessSearchStrategy;
    //todo: replace with different handler implementations
    private boolean dryMode;
    private int numEdges;
    private int numPrevEdges;
    private int numOrigEdges;
    private int numPrevOrigEdges;
    private Stats calcPrioStats = new Stats();
    private Stats contractStats = new Stats();

    public EdgeBasedNodeContractor(Directory dir, GraphHopperStorage ghStorage, CHGraph prepareGraph, TurnWeighting turnWeighting, TraversalMode traversalMode) {
        super(dir, ghStorage, prepareGraph, turnWeighting);
        this.turnWeighting = turnWeighting;
        this.traversalMode = traversalMode;
        this.witnessSearchStrategy = new TurnReplacementSearch();
    }

    @Override
    public void initFromGraph() {
        super.initFromGraph();
        witnessPathFinder = new WitnessPathFinder(prepareGraph, turnWeighting, traversalMode);
        FlagEncoder prepareFlagEncoder = turnWeighting.getFlagEncoder();
        DefaultEdgeFilter inEdgeFilter = new DefaultEdgeFilter(prepareFlagEncoder, true, false);
        DefaultEdgeFilter outEdgeFilter = new DefaultEdgeFilter(prepareFlagEncoder, false, true);
        inEdgeExplorer = prepareGraph.createEdgeExplorer(inEdgeFilter);
        outEdgeExplorer = prepareGraph.createEdgeExplorer(outEdgeFilter);
        scExplorer = prepareGraph.createEdgeExplorer(outEdgeFilter);
        allCHExplorer = prepareGraph.createEdgeExplorer(new DefaultEdgeFilter(prepareFlagEncoder, true, true));
        toNodeInEdgeExplorer = prepareGraph.createEdgeExplorer(inEdgeFilter);
        fromNodeOrigInEdgeExplorer = ghStorage.createEdgeExplorer(inEdgeFilter);
        toNodeOrigOutEdgeExplorer = ghStorage.createEdgeExplorer(outEdgeFilter);
        loopAvoidanceInEdgeExplorer = ghStorage.createEdgeExplorer(inEdgeFilter);
        loopAvoidanceOutEdgeExplorer = ghStorage.createEdgeExplorer(outEdgeFilter);
        hierarchyDepths = new int[prepareGraph.getNodes()];
    }

    @Override
    public void close() {
        // todo: not sure if we need this yet
    }

    @Override
    public void setMaxVisitedNodes(int maxVisitedNodes) {
        // todo: limiting local searches is a bit more complicated in the edge-based case, because the local searches
        // are also used to find the shortcut
    }

    @Override
    public int calculatePriority(int node) {
        dryMode = true;
        long start = System.nanoTime();
        findShortcuts(node);
        stats().calcTime += (System.nanoTime() - start);
        CHEdgeIterator iter = allCHExplorer.setBaseNode(node);
        while (iter.next()) {
            if (isContracted(iter.getAdjNode()))
                continue;
            numPrevEdges++;
            numPrevOrigEdges += getOrigEdgeCount(iter.getEdge());
        }
        // todo: optimize
        // the more shortcuts need to be introduced the later we want to contract this node
        // the more edges will be removed when contracting this node the earlier we want to contract the node
        // right now we use edge differences instead of quotients
        return 8 * (numEdges - numPrevEdges) + 4 * (numOrigEdges - numPrevOrigEdges) + hierarchyDepths[node];
    }

    @Override
    public long contractNode(int node) {
        dryMode = false;
        long start = System.nanoTime();
        long result = findShortcuts(node);
        CHEdgeIterator iter = allCHExplorer.setBaseNode(node);
        while (iter.next()) {
            if (isContracted(iter.getAdjNode()) || iter.getAdjNode() == node)
                continue;
            hierarchyDepths[iter.getAdjNode()] = Math.max(hierarchyDepths[iter.getAdjNode()], hierarchyDepths[node] + 1);
        }
        stats().calcTime += System.nanoTime() - start;
        return result;
    }

    private int findShortcuts(int node) {
        // todo: for osm data where there are only a very few turn restrictions (no left turn etc.) the graph
        // contraction should be much faster if we exploit that there are no turn costs on most nodes
        LOGGER.debug("Finding shortcuts for node {}, required shortcuts will be {}", node, dryMode ? "counted" : "added");
        stats().nodes++;
        CHEdgeIterator incomingEdges = inEdgeExplorer.setBaseNode(node);
        numEdges = 0;
        numPrevEdges = 0;
        numOrigEdges = 0;
        numPrevOrigEdges = 0;
        int degree = 0;
        while (incomingEdges.next()) {
            int fromNode = incomingEdges.getAdjNode();
            if (isContracted(fromNode))
                continue;

            degree++;

            if (fromNode == node) {
                continue;
            }

            // todo: note that we rely on shortcuts always having forward direction only, if we change this we need a
            // more sophisticated way to figure out what the 'first' and 'last' original edges are
            List<WitnessSearchEntry> initialEntries = witnessSearchStrategy.getInitialEntries(fromNode, incomingEdges);
            if (initialEntries.isEmpty()) {
                LOGGER.trace("No initial entries for incoming edge {}", incomingEdges);
                continue;
            }
            witnessPathFinder.setInitialEntries(initialEntries);
            CHEdgeIterator outgoingEdges = outEdgeExplorer.setBaseNode(node);
            while (outgoingEdges.next()) {
                int toNode = outgoingEdges.getAdjNode();
                if (isContracted(toNode) || toNode == node)
                    continue;

                stats().shortcutsChecked++;
                int targetEdge = outgoingEdges.getLastOrigEdge();

                dijkstraSW.start();
                dijkstraCount++;
                witnessPathFinder.findTarget(targetEdge, toNode);
                dijkstraSW.stop();

                CHEntry entry = witnessPathFinder.getFoundEntry(targetEdge, toNode);
                LOGGER.trace("Witness path search to outgoing edge yielded entry {}", entry);
                if (witnessSearchStrategy.shortcutRequired(node, toNode, incomingEdges, outgoingEdges, witnessPathFinder, entry)) {
                    // todo: note that we do not count loop-helper shortcuts here, but there are not that many usually
                    stats().shortcutsNeeded++;
                    addShortcuts(entry);
                } else {
                    stats().numWitnessesFound++;
                }
            }
        }
        return degree;
    }

    @Override
    public String getPrepareAlgoMemoryUsage() {
        // todo: this method is currently misused to print some statistics for performance analysis
        String result = String.format("stats(calc): %s, stats(contract): %s", calcPrioStats, contractStats);
        calcPrioStats = new Stats();
        contractStats = new Stats();
        return result;
    }

    private void addShortcuts(CHEntry chEntry) {
        LOGGER.trace("Adding shortcuts for target entry {}", chEntry);
        CHEntry root = chEntry.getParent();
        while (root.parent.edge != EdgeIterator.NO_EDGE) {
            root = root.getParent();
        }
        if (root.parent.adjNode != chEntry.adjNode ||
                // here we misuse root.parent.incEdge as first orig edge of the potential shortcut
                loopShortcutNecessary(
                        chEntry.adjNode, root.getParent().incEdge, chEntry.incEdge, chEntry.weight)) {
            handleShortcut(root, chEntry);
        }
    }

    private void handleShortcut(CHEntry edgeFrom, CHEntry edgeTo) {
        if (dryMode) {
            numEdges++;
            numOrigEdges += getOrigEdgeCount(edgeFrom.edge) + getOrigEdgeCount(edgeTo.edge);
        } else {
            addShortcut(edgeFrom, edgeTo);
        }
    }

    private boolean loopShortcutNecessary(int node, int firstOrigEdge, int lastOrigEdge, double loopWeight) {
        EdgeIterator inIter = loopAvoidanceInEdgeExplorer.setBaseNode(node);
        while (inIter.next()) {
            EdgeIterator outIter = loopAvoidanceOutEdgeExplorer.setBaseNode(node);
            double inTurnCost = getTurnCost(inIter.getEdge(), node, firstOrigEdge);
            while (outIter.next()) {
                double totalLoopCost = inTurnCost + loopWeight +
                        getTurnCost(lastOrigEdge, node, outIter.getEdge());
                double directTurnCost = getTurnCost(inIter.getEdge(), node, outIter.getEdge());
                if (totalLoopCost < directTurnCost) {
                    return true;
                }
            }
        }
        LOGGER.trace("Loop avoidance -> no shortcut");
        return false;
    }

    private CHEntry addShortcut(CHEntry edgeFrom, CHEntry edgeTo) {
        if (edgeTo.parent.edge != edgeFrom.edge) {
            CHEntry prev = addShortcut(edgeFrom, edgeTo.getParent());
            return doAddShortcut(prev, edgeTo);
        } else {
            return doAddShortcut(edgeFrom, edgeTo);
        }
    }

    private CHEntry doAddShortcut(CHEntry edgeFrom, CHEntry edgeTo) {
        int from = edgeFrom.parent.adjNode;
        int adjNode = edgeTo.adjNode;

        final CHEdgeIterator iter = scExplorer.setBaseNode(from);
        while (iter.next()) {
            if (!iter.isShortcut()
                    || !(iter.getFirstOrigEdge() == edgeFrom.getParent().incEdge)
                    || !(iter.getLastOrigEdge() == edgeTo.incEdge)) {
                continue;
            }
            final double existingWeight = turnWeighting.calcWeight(iter, false, EdgeIterator.NO_EDGE);
            if (existingWeight <= edgeTo.weight) {
                // this shortcut already exists with lower weight --> do nothing
                CHEntry entry = new CHEntry(iter.getEdge(), iter.getLastOrigEdge(), adjNode, existingWeight);
                entry.parent = edgeFrom.parent;
                return entry;
            } else {
                // update weight
                iter.setSkippedEdges(edgeFrom.edge, edgeTo.edge);
                iter.setWeight(edgeTo.weight);
                CHEntry entry = new CHEntry(iter.getEdge(), iter.getLastOrigEdge(), adjNode, edgeTo.weight);
                entry.parent = edgeFrom.parent;
                return entry;
            }
        }

        // this shortcut is new --> add it
        LOGGER.trace("Adding shortcut from {} to {}, weight: {}, firstOrigEdge: {}, lastOrigEdge: {}",
                from, adjNode, edgeTo.weight, edgeFrom.getParent().incEdge, edgeTo.incEdge);
        CHEdgeIteratorState shortcut = prepareGraph.shortcut(from, adjNode);
        long direction = PrepareEncoder.getScFwdDir();
        // we need to set flags first because they overwrite weight etc
        shortcut.setFlags(direction);
        shortcut.setSkippedEdges(edgeFrom.edge, edgeTo.edge)
                // this is a bit of a hack, we misuse incEdge of the root entry to store the first orig edge
                .setOuterOrigEdges(edgeFrom.getParent().incEdge, edgeTo.incEdge)
                .setWeight(edgeTo.weight);
        final int origEdgeCount = getOrigEdgeCount(edgeFrom.edge) + getOrigEdgeCount(edgeTo.edge);
        setOrigEdgeCount(shortcut.getEdge(), origEdgeCount);
        addedShortcutsCount++;
        CHEntry entry = new CHEntry(
                shortcut.getEdge(), shortcut.getEdge(), edgeTo.adjNode, edgeTo.weight);
        entry.parent = edgeFrom.parent;
        return entry;
    }

    private double getTurnCost(int inEdge, int node, int outEdge) {
        if (!traversalMode.hasUTurnSupport() && inEdge == outEdge) {
            return Double.POSITIVE_INFINITY;
        }
        double turnCost = turnWeighting.calcTurnWeight(inEdge, node, outEdge);
        if (turnCost == 0 && inEdge == outEdge) {
            return turnWeighting.getDefaultUTurnCost();
        }
        return turnCost;
    }

    private boolean isContracted(int node) {
        int level = prepareGraph.getLevel(node);
        return level < maxLevel;
    }

    /**
     * Checks if the path leading to the given shortest path entry consists only of the incoming edge, the outgoing edge
     * and an arbitrary number of loops at the node.
     */
    private static boolean bestPathIsValidAndRequiresNode(
            CHEntry chEntry, int node, CHEdgeIteratorState incomingEdge, CHEdgeIteratorState outgoingEdge) {
        if (Double.isInfinite(chEntry.weight)) {
            LOGGER.trace("Target edge could not be reached even via node to be contracted -> no shortcut");
            return false;
        }

        if (chEntry.edge != outgoingEdge.getEdge()) {
            LOGGER.trace("Found a witness path using alternative target edge -> no shortcut");
            return false;
        }

        // skip all edges as long as they represent loops at the given node
        CHEntry parent = chEntry.getParent();
        int loops = -1;
        while (parent != null && parent.adjNode == node) {
            loops++;
            chEntry = parent;
            parent = chEntry.getParent();
        }

        if (loops < 1 && incomingEdge.getLastOrigEdge() == outgoingEdge.getFirstOrigEdge()) {
            LOGGER.trace("U-turn at node to be contracted -> no shortcut");
            return false;
        }

        boolean bestPathUsesNodeToBeContracted = chEntry.edge == incomingEdge.getEdge() && parent != null && parent.edge == EdgeIterator.NO_EDGE;
        if (!bestPathUsesNodeToBeContracted) {
            LOGGER.trace("Found a witness path -> no shortcut");
        }
        return bestPathUsesNodeToBeContracted;
    }

    private Stats stats() {
        return dryMode ? calcPrioStats : contractStats;
    }

    private interface WitnessSearchStrategy {
        List<WitnessSearchEntry> getInitialEntries(int fromNode, CHEdgeIteratorState incomingEdge);

        boolean shortcutRequired(int node, int toNode, CHEdgeIteratorState incomingEdges,
                                 CHEdgeIteratorState outgoingEdges, WitnessPathFinder witnessPathFinder, CHEntry entry);
    }

    /**
     * Turn-replacement algorithm described in 'Efficient Routing in Road Networks with Turn Costs' by R. Geisberger
     * and C. Vetter. This strategy is most efficient in deciding which shortcuts will not be required, but also needs
     * to run the most checks.
     */
    private class TurnReplacementSearch implements WitnessSearchStrategy {
        @Override
        public List<WitnessSearchEntry> getInitialEntries(int fromNode, CHEdgeIteratorState incomingEdge) {
            // todo: simplify & optimize
            final int firstOrigEdge = incomingEdge.getFirstOrigEdge();
            List<WitnessSearchEntry> initialEntries = new ArrayList<>();
            CHEdgeIterator outIter = outEdgeExplorer.setBaseNode(fromNode);
            while (outIter.next()) {
                if (isContracted(outIter.getAdjNode()))
                    continue;
                double outTurnReplacementDifference = Double.NEGATIVE_INFINITY;
                boolean incomingEdgeAccessible = false;
                boolean entryNeeded = false;
                EdgeIterator inIter = fromNodeOrigInEdgeExplorer.setBaseNode(fromNode);
                while (inIter.next()) {
                    double turnCost = getTurnCost(inIter.getEdge(), fromNode, firstOrigEdge);
                    if (!Double.isInfinite(turnCost)) {
                        incomingEdgeAccessible = true;
                    } else {
                        continue;
                    }
                    double alternativeTurnCost = getTurnCost(inIter.getEdge(), fromNode, outIter.getFirstOrigEdge());
                    if (!Double.isInfinite(alternativeTurnCost)) {
                        entryNeeded = true;
                    } else {
                        entryNeeded = false;
                        break;
                    }
                    outTurnReplacementDifference = Math.max(outTurnReplacementDifference, alternativeTurnCost - turnCost);
                }
                if (!incomingEdgeAccessible) {
                    // our incoming edge can not be reached with finite turn costs from any original edge -> we need no shortcut
                    return Collections.emptyList();
                }

                if (!entryNeeded) {
                    continue;
                }

                double weight = outTurnReplacementDifference + turnWeighting.calcWeight(outIter, false, EdgeIterator.NO_EDGE);
                WitnessSearchEntry entry = new WitnessSearchEntry(outIter.getEdge(), outIter.getLastOrigEdge(), outIter.getAdjNode(), weight);
                entry.parent = new WitnessSearchEntry(EdgeIterator.NO_EDGE, outIter.getFirstOrigEdge(), fromNode, 0);
                if (outIter.getEdge() == incomingEdge.getEdge()) {
                    entry.possibleShortcut = true;
                }
                LOGGER.trace("Adding initial entry {}", entry);
                initialEntries.add(entry);
            }
            return initialEntries;
        }

        @Override
        public boolean shortcutRequired(int node, int toNode, CHEdgeIteratorState incomingEdges, CHEdgeIteratorState outgoingEdges, WitnessPathFinder witnessPathFinder, CHEntry entry) {
            return bestPathIsValidAndRequiresNode(entry, node, incomingEdges, outgoingEdges)
                    && !alternativeWitnessExists(outgoingEdges, toNode, witnessPathFinder, entry);
        }

        private boolean alternativeWitnessExists(
                CHEdgeIteratorState outgoingEdge, int toNode, WitnessPathFinder witnessPathFinder, CHEntry chEntry) {
            boolean foundWitness = false;
            CHEdgeIterator inIter = toNodeInEdgeExplorer.setBaseNode(toNode);
            while (!foundWitness && inIter.next()) {
                if (isContracted(inIter.getAdjNode())) {
                    continue;
                }
                final int inIterLast = inIter.getLastOrigEdge();
                final int outgoingEdgeLast = outgoingEdge.getLastOrigEdge();
                double inTurnReplacementDifference = Double.NEGATIVE_INFINITY;
                boolean outgoingEdgeAccessible = false;
                EdgeIterator origOutIter = toNodeOrigOutEdgeExplorer.setBaseNode(toNode);
                while (origOutIter.next()) {
                    final double alternativeTurnCost = getTurnCost(inIterLast, toNode, origOutIter.getEdge());
                    final double turnCost = getTurnCost(outgoingEdgeLast, toNode, origOutIter.getEdge());
                    if (Double.isInfinite(alternativeTurnCost) && !Double.isInfinite(turnCost)) {
                        inTurnReplacementDifference = Double.POSITIVE_INFINITY;
                        outgoingEdgeAccessible = true;
                        break;
                    } else if (Double.isInfinite(alternativeTurnCost) && Double.isInfinite(turnCost)) {
                        continue;
                    }
                    outgoingEdgeAccessible = true;
                    inTurnReplacementDifference = Math.max(inTurnReplacementDifference, alternativeTurnCost - turnCost);
                }
                if (!outgoingEdgeAccessible) {
                    // our outgoing edge is not connected to any original edges with finite turn costs -> we need no shortcut
                    return true;
                }
                CHEntry altCHEntry = witnessPathFinder.getFoundEntry(inIterLast, toNode);
                if (altCHEntry.incEdge != chEntry.incEdge && inTurnReplacementDifference + altCHEntry.weight <= chEntry.weight) {
                    foundWitness = true;
                }
            }
            if (foundWitness) {
                LOGGER.trace("Found witness path -> no shortcut");
            }
            return foundWitness;
        }
    }

    /**
     * Simple local search algorithm as described in 'Efficient Routing in Road Networks with Turn Costs' by R. Geisberger
     * and C. Vetter. This strategy is simpler than {@link TurnReplacementSearch}, but introduces shortcuts that could
     * be avoided with the latter.
     */
    private class SimpleSearch implements WitnessSearchStrategy {
        @Override
        public List<WitnessSearchEntry> getInitialEntries(int fromNode, CHEdgeIteratorState incomingEdge) {
            List<WitnessSearchEntry> initialEntries = new ArrayList<>();
            CHEdgeIterator outIter = outEdgeExplorer.setBaseNode(fromNode);
            while (outIter.next()) {
                if (outIter.getFirstOrigEdge() != incomingEdge.getFirstOrigEdge())
                    continue;
                if (isContracted(outIter.getAdjNode()))
                    continue;
                double weight = turnWeighting.calcWeight(outIter, false, EdgeIterator.NO_EDGE);
                WitnessSearchEntry entry = new WitnessSearchEntry(outIter.getEdge(), outIter.getLastOrigEdge(), outIter.getAdjNode(), weight);
                entry.parent = new WitnessSearchEntry(EdgeIterator.NO_EDGE, incomingEdge.getFirstOrigEdge(), fromNode, 0);
                if (outIter.getEdge() == incomingEdge.getEdge()) {
                    entry.possibleShortcut = true;
                }
                initialEntries.add(entry);
            }
            return initialEntries;
        }

        @Override
        public boolean shortcutRequired(int node, int toNode, CHEdgeIteratorState incomingEdges, CHEdgeIteratorState outgoingEdges, WitnessPathFinder witnessPathFinder, CHEntry entry) {
            return bestPathIsValidAndRequiresNode(entry, node, incomingEdges, outgoingEdges);
        }
    }

    /**
     * Never finds any witnesses and will always lead to a shortcut as long as the two edges under question were
     * connected with finite weight before the node contraction.
     */
    private class TrivialSearch implements WitnessSearchStrategy {
        @Override
        public List<WitnessSearchEntry> getInitialEntries(int fromNode, CHEdgeIteratorState incomingEdge) {
            List<WitnessSearchEntry> initialEntries = new ArrayList<>();
            double weight = turnWeighting.calcWeight(incomingEdge, false, EdgeIterator.NO_EDGE);
            WitnessSearchEntry entry = new WitnessSearchEntry(incomingEdge.getEdge(), incomingEdge.getLastOrigEdge(),
                    incomingEdge.getBaseNode(), weight);
            entry.parent = new WitnessSearchEntry(EdgeIterator.NO_EDGE, incomingEdge.getFirstOrigEdge(), fromNode, 0);
            entry.possibleShortcut = true;
            initialEntries.add(entry);
            return initialEntries;
        }

        @Override
        public boolean shortcutRequired(int node, int toNode, CHEdgeIteratorState incomingEdges,
                                        CHEdgeIteratorState outgoingEdges, WitnessPathFinder witnessPathFinder, CHEntry entry) {
            return bestPathIsValidAndRequiresNode(entry, node, incomingEdges, outgoingEdges);
        }
    }

    /**
     * Never leads to a shortcut, using this strategy will lead to queries equivalent to normal Dijkstra.
     */
    private class NoSearch implements WitnessSearchStrategy {
        @Override
        public List<WitnessSearchEntry> getInitialEntries(int fromNode, CHEdgeIteratorState incomingEdge) {
            return Collections.emptyList();
        }

        @Override
        public boolean shortcutRequired(int node, int toNode, CHEdgeIteratorState incomingEdges,
                                        CHEdgeIteratorState outgoingEdges, WitnessPathFinder witnessPathFinder, CHEntry entry) {
            return false;
        }
    }

    private static class Stats {
        int nodes;
        long shortcutsChecked;
        long shortcutsNeeded;
        long numWitnessesFound;
        long calcTime;

        @Override
        public String toString() {
            return String.format("runtime: %7.2f, nodes: %10s, scChecked: %10s, scNeeded: %10s, witnessesFound: %10s",
                    calcTime * 1.e-9, Helper.nf(nodes), Helper.nf(shortcutsChecked), Helper.nf(shortcutsNeeded), Helper.nf(numWitnessesFound));
        }
    }
}
