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
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EdgeBasedNodeContractor implements NodeContractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeBasedNodeContractor.class);
    // todo: modify code such that logging does not alter performance 
    private final GraphHopperStorage ghStorage;
    private final CHGraph prepareGraph;
    private final TurnWeighting turnWeighting;
    private final TraversalMode traversalMode;
    private CHEdgeExplorer inEdgeExplorer;
    private CHEdgeExplorer toNodeInEdgeExplorer;
    private CHEdgeExplorer outEdgeExplorer;
    private EdgeExplorer fromNodeOrigInEdgeExplorer;
    private EdgeExplorer toNodeOrigOutEdgeExplorer;
    private EdgeExplorer loopAvoidanceInEdgeExplorer;
    private EdgeExplorer loopAvoidanceOutEdgeExplorer;
    private int maxLevel;
    private int addedShortcuts;
    private int dijkstraCount;
    private StopWatch dijkstraSW = new StopWatch();
    private boolean dryMode;
    private int shortcutCount;


    public EdgeBasedNodeContractor(GraphHopperStorage ghStorage, CHGraph prepareGraph, TurnWeighting turnWeighting, TraversalMode traversalMode) {
        this.ghStorage = ghStorage;
        this.prepareGraph = prepareGraph;
        this.turnWeighting = turnWeighting;
        this.traversalMode = traversalMode;
    }

    @Override
    public void initFromGraph() {
        // todo: do we really need this method ? the problem is that ghStorage/prepareGraph can potentially be modified
        // between the constructor call and contractNode,calcShortcutCount etc. ...
        maxLevel = prepareGraph.getNodes() + 1;
        FlagEncoder prepareFlagEncoder = turnWeighting.getFlagEncoder();
        DefaultEdgeFilter inEdgeFilter = new DefaultEdgeFilter(prepareFlagEncoder, true, false);
        DefaultEdgeFilter outEdgeFilter = new DefaultEdgeFilter(prepareFlagEncoder, false, true);
        inEdgeExplorer = prepareGraph.createEdgeExplorer(inEdgeFilter);
        outEdgeExplorer = prepareGraph.createEdgeExplorer(outEdgeFilter);
        toNodeInEdgeExplorer = prepareGraph.createEdgeExplorer(inEdgeFilter);
        fromNodeOrigInEdgeExplorer = ghStorage.createEdgeExplorer(inEdgeFilter);
        toNodeOrigOutEdgeExplorer = ghStorage.createEdgeExplorer(outEdgeFilter);
        loopAvoidanceInEdgeExplorer = ghStorage.createEdgeExplorer(inEdgeFilter);
        loopAvoidanceOutEdgeExplorer = ghStorage.createEdgeExplorer(outEdgeFilter);
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
        findShortcuts(node);
        // the more shortcuts need to be introduced the later we want to contract this node
        return shortcutCount; 
    }

    @Override
    public long contractNode(int node) {
        dryMode = false;
        return findShortcuts(node);
    }

    private long findShortcuts(int node) {
        LOGGER.debug("Contracting node {}", node);
        CHEdgeIterator incomingEdges = inEdgeExplorer.setBaseNode(node);
        shortcutCount = 0;
        while (incomingEdges.next()) {
            int fromNode = incomingEdges.getAdjNode();
            if (isContracted(fromNode) || fromNode == node)
                continue;

            // todo: note that we rely on shortcuts always having forward direction only, if we change this we need a
            // more sophisticated way to figure out what the 'first' and 'last' original edges are
            List<CHEntry> initialEntries = getInitialEntriesForWitnessPaths(fromNode, incomingEdges.getFirstOrigEdge());
            if (initialEntries.isEmpty()) {
                LOGGER.trace("No initial entries for incoming edge {}", incomingEdges);
                continue;
            }
            WitnessPathFinder witnessPathFinder = new WitnessPathFinder(prepareGraph, turnWeighting, traversalMode,
                    initialEntries, fromNode);
            CHEdgeIterator outgoingEdges = outEdgeExplorer.setBaseNode(node);
            while (outgoingEdges.next()) {
                int toNode = outgoingEdges.getAdjNode();
                if (isContracted(toNode) || toNode == node)
                    continue;

                int targetEdge = outgoingEdges.getLastOrigEdge();

                dijkstraSW.start();
                dijkstraCount++;
                witnessPathFinder.findTarget(targetEdge, toNode);
                dijkstraSW.stop();

                CHEntry entry = witnessPathFinder.getFoundEntry(targetEdge, toNode);
                LOGGER.trace("Witness path search to outgoing edge yielded entry {}", entry);
                if (bestPathIsValidAndRequiresNode(entry, node, incomingEdges, outgoingEdges) &&
                        !alternativeWitnessExists(outgoingEdges, toNode, witnessPathFinder, entry)) {
                    addShortcuts(entry);
                }
            }
        }
        // todo: why do we need the degree again ?
        return 0;
    }

    @Override
    public int getAddedShortcutsCount() {
        return addedShortcuts;
    }

    @Override
    public String getPrepareAlgoMemoryUsage() {
        return "todo";
    }

    @Override
    public long getDijkstraCount() {
        return dijkstraCount;
    }

    @Override
    public void resetDijkstraTime() {
        dijkstraSW = new StopWatch();
    }

    @Override
    public float getDijkstraSeconds() {
        return dijkstraSW.getSeconds();
    }

    private List<CHEntry> getInitialEntriesForWitnessPaths(int fromNode, int firstOrigEdge) {
        // todo: simplify & optimize
        List<CHEntry> initialEntries = new ArrayList<>();
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
            CHEntry entry = new CHEntry(outIter.getEdge(), outIter.getLastOrigEdge(), outIter.getAdjNode(), weight);
            entry.parent = new CHEntry(EdgeIterator.NO_EDGE, outIter.getFirstOrigEdge(), fromNode, 0);
            LOGGER.trace("Adding initial entry {}", entry);
            initialEntries.add(entry);
        }
        return initialEntries;
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

    private void addShortcuts(CHEntry chEntry) {
        LOGGER.trace("Adding shortcuts for target entry {}", chEntry);
        // todo: we will also need a way to only count the number of needed shortcuts here without doing anything
        CHEntry root = chEntry.getParent();
        while (root.parent.edge != EdgeIterator.NO_EDGE) {
            root = root.getParent();
        }
        if (root.parent.adjNode == chEntry.adjNode &&
                // here we misuse root.parent.incEdge as first orig edge of the potential shortcut
                !loopShortcutNecessary(
                        chEntry.adjNode, root.getParent().incEdge, chEntry.incEdge, chEntry.weight)) {
            return;
        } else {
            handleShortcut(root, chEntry);
        }
    }

    private void handleShortcut(CHEntry edgeFrom, CHEntry edgeTo) {
        if (dryMode) {
            shortcutCount++;
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
        long direction = PrepareEncoder.getScFwdDir();
        LOGGER.trace("Adding shortcut from {} to {}, weight: {}, firstOrigEdge: {}, lastOrigEdge: {}",
                from, adjNode, edgeTo.weight, edgeFrom.getParent().incEdge, edgeTo.incEdge);
        CHEdgeIteratorState shortcut = prepareGraph.shortcut(from, adjNode);
        // we need to set flags first because they overwrite weight etc
        shortcut.setFlags(direction);
        shortcut.setSkippedEdges(edgeFrom.edge, edgeTo.edge)
                // this is a bit of a hack, we misuse incEdge of the root entry to store the first orig edge
                .setOuterOrigEdges(edgeFrom.getParent().incEdge, edgeTo.incEdge)
                .setWeight(edgeTo.weight);
        addedShortcuts++;
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

    private boolean isContracted(int fromNode) {
        return prepareGraph.getLevel(fromNode) < maxLevel;
    }
}
