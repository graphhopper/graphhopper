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

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.LongHashSet;
import com.carrotsearch.hppc.LongSet;
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

import static com.graphhopper.util.Helper.nf;
import static java.lang.System.nanoTime;

public class EdgeBasedNodeContractor extends AbstractNodeContractor {
    // todo: modify code such that logging does not alter performance 
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeBasedNodeContractor.class);
    public static boolean aggressiveSearch = true;
    public static boolean arrayBasedWitnessPathFinder = true;
    public static int edgeDifferenceWeight = 8;
    public static int originalEdgeDifferenceWeight = 4;
    public static int hierarchyDepthWeight = 1;
    private final TurnWeighting turnWeighting;
    private final TraversalMode traversalMode;
    private final SimpleSearch simpleSearch = new SimpleSearch();
    private final ShortcutHandler addingShortcutHandler = new AddingShortcutHandler();
    private final ShortcutHandler countingShortcutHandler = new CountingShortcutHandler();
    private ShortcutHandler activeShortcutHandler;
    private int[] hierarchyDepths;
    private WitnessPathFinder witnessPathFinder;
    private CHEdgeExplorer scExplorer;
    private CHEdgeExplorer allCHExplorer;
    private EdgeExplorer fromNodeOrigInEdgeExplorer;
    private EdgeExplorer toNodeOrigOutEdgeExplorer;
    private EdgeExplorer toNodeOrigInEdgeExplorer;
    private EdgeExplorer loopAvoidanceInEdgeExplorer;
    private EdgeExplorer loopAvoidanceOutEdgeExplorer;
    private WitnessSearchStrategy witnessSearchStrategy;
    private int maxLevel;
    private int numEdges;
    private int numPrevEdges;
    private int numOrigEdges;
    private int numPrevOrigEdges;
    private FlagEncoder encoder;

    public EdgeBasedNodeContractor(Directory dir, GraphHopperStorage ghStorage, CHGraph prepareGraph, TurnWeighting turnWeighting, TraversalMode traversalMode) {
        super(dir, ghStorage, prepareGraph, turnWeighting);
        this.turnWeighting = turnWeighting;
        this.encoder = turnWeighting.getFlagEncoder();
        this.traversalMode = traversalMode;
        this.witnessSearchStrategy = new TurnReplacementSearch();
    }

    @Override
    public void initFromGraph() {
        super.initFromGraph();
        maxLevel = prepareGraph.getNodes() + 1;
        witnessPathFinder = arrayBasedWitnessPathFinder ?
                new ArrayBasedWitnessPathFinder(prepareGraph, turnWeighting, traversalMode, maxLevel) :
                new MapBasedWitnessPathFinder(prepareGraph, turnWeighting, traversalMode, maxLevel);
        DefaultEdgeFilter inEdgeFilter = new DefaultEdgeFilter(encoder, true, false);
        DefaultEdgeFilter outEdgeFilter = new DefaultEdgeFilter(encoder, false, true);
        inEdgeExplorer = prepareGraph.createEdgeExplorer(inEdgeFilter);
        outEdgeExplorer = prepareGraph.createEdgeExplorer(outEdgeFilter);
        scExplorer = prepareGraph.createEdgeExplorer(outEdgeFilter);
        allCHExplorer = prepareGraph.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, true));
        fromNodeOrigInEdgeExplorer = ghStorage.createEdgeExplorer(inEdgeFilter);
        toNodeOrigOutEdgeExplorer = ghStorage.createEdgeExplorer(outEdgeFilter);
        toNodeOrigInEdgeExplorer = ghStorage.createEdgeExplorer(inEdgeFilter);
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
        activeShortcutHandler = countingShortcutHandler;
        long start = nanoTime();
        findAndHandleShortcuts(node);
        stats().calcTime += (nanoTime() - start);
        CHEdgeIterator iter = allCHExplorer.setBaseNode(node);
        while (iter.next()) {
            if (isContracted(iter.getAdjNode()))
                continue;
            if (iter.isForward(encoder)) {
                numPrevEdges++;
            }
            if (iter.isBackward(encoder)) {
                numPrevEdges++;
            }
            if (!iter.isShortcut()) {
                if (iter.isForward(encoder)) {
                    numPrevOrigEdges++;
                }
                if (iter.isBackward(encoder)) {
                    numPrevOrigEdges++;
                }
            } else {
                numPrevOrigEdges += getOrigEdgeCount(iter.getEdge());
            }
        }
        // todo: optimize
        // the more shortcuts need to be introduced the later we want to contract this node
        // the more edges will be removed when contracting this node the earlier we want to contract the node
        // right now we use edge differences instead of quotients
        return edgeDifferenceWeight * (numEdges - numPrevEdges) +
                originalEdgeDifferenceWeight * (numOrigEdges - numPrevOrigEdges) +
                hierarchyDepthWeight * hierarchyDepths[node];
    }

    @Override
    public long contractNode(int node) {
        activeShortcutHandler = addingShortcutHandler;
        long start = nanoTime();
        long result = findAndHandleShortcuts(node);
        CHEdgeIterator iter = allCHExplorer.setBaseNode(node);
        while (iter.next()) {
            if (isContracted(iter.getAdjNode()) || iter.getAdjNode() == node)
                continue;
            hierarchyDepths[iter.getAdjNode()] = Math.max(hierarchyDepths[iter.getAdjNode()], hierarchyDepths[node] + 1);
        }
        stats().calcTime += nanoTime() - start;
        return result;
    }

    private int findAndHandleShortcuts(int node) {
        if (aggressiveSearch) {
            return findAndHandleShortcutsAggressive(node);
        } else {
            return findAndHandleShortcutsClassic(node);
        }
    }

    private int findAndHandleShortcutsAggressive(int node) {
        LOGGER.debug("Finding shortcuts for node {}, required shortcuts will be {}ed", node, activeShortcutHandler.getAction());
        stats().nodes++;
        resetEdgeCounters();
        LongSet witnessedPairs = new LongHashSet(16);
        int degree = runQuickWitnessSearch(node, witnessedPairs);
        runExhaustiveWitnessSearch(node, witnessedPairs);
        return degree;
    }

    private int runQuickWitnessSearch(int node, LongSet witnessedPairs) {
        int degree = 0;
        EdgeIterator incomingEdges = inEdgeExplorer.setBaseNode(node);
        while (incomingEdges.next()) {
            int fromNode = incomingEdges.getAdjNode();
            if (isContracted(fromNode))
                continue;

            degree++;

            if (fromNode == node) {
                continue;
            }

            IntObjectMap<WitnessSearchEntry> initialEntries = simpleSearch.getInitialEntries(fromNode, incomingEdges);
            witnessPathFinder.setInitialEntries(initialEntries);
            if (initialEntries.isEmpty()) {
                continue;
            }

            EdgeIterator outgoingEdges = outEdgeExplorer.setBaseNode(node);
            while (outgoingEdges.next()) {
                int toNode = outgoingEdges.getAdjNode();
                if (isContracted(toNode) || toNode == node) {
                    continue;
                }
                int targetEdge = outgoingEdges.getLastOrigEdge();
                witnessPathFinder.findTarget(targetEdge, toNode);

                CHEntry originalPath = witnessPathFinder.getFoundEntry(targetEdge, toNode);
                if (originalPath == null) {
                    continue;
                }
                if (!simpleSearch.shortcutRequired(node, toNode, incomingEdges, outgoingEdges, witnessPathFinder, originalPath)) {
                    // todo:
                    // what if there are two incoming edges of same weight that 'witness' each other, but both use the
                    // node to be contracted ? do we potentially miss a required shortcut ?
                    witnessedPairs.add(twoIntsInLong(incomingEdges.getEdge(), outgoingEdges.getEdge()));
                }
            }
        }
        return degree;
    }

    private void runExhaustiveWitnessSearch(int node, LongSet witnessedPairs) {
        EdgeIterator incomingEdges = inEdgeExplorer.setBaseNode(node);
        while (incomingEdges.next()) {
            int fromNode = incomingEdges.getAdjNode();
            if (isContracted(fromNode) || fromNode == node) {
                continue;
            }

            EdgeIterator origInIter = fromNodeOrigInEdgeExplorer.setBaseNode(fromNode);
            while (origInIter.next()) {
                IntObjectMap<WitnessSearchEntry> initialEntries = getInitialEntriesAggressive(fromNode, node, incomingEdges, origInIter);
                if (initialEntries.isEmpty()) {
                    continue;
                }
                witnessPathFinder.setInitialEntries(initialEntries);

                EdgeIterator outgoingEdges = outEdgeExplorer.setBaseNode(node);
                while (outgoingEdges.next()) {
                    int toNode = outgoingEdges.getAdjNode();
                    if (isContracted(toNode) || toNode == node) {
                        continue;
                    }
                    stats().shortcutsChecked++;
                    if (witnessedPairs.contains(twoIntsInLong(incomingEdges.getEdge(), outgoingEdges.getEdge()))) {
                        continue;
                    }
                    int targetEdge = outgoingEdges.getLastOrigEdge();
                    witnessPathFinder.findTarget(targetEdge, toNode);
                    CHEntry originalPath = witnessPathFinder.getFoundEntry(targetEdge, toNode);
                    if (originalPath == null) {
                        continue;
                    }
                    if (!bestPathIsValidAndRequiresNode(originalPath, node, incomingEdges, outgoingEdges)) {
                        continue;
                    }

                    EdgeIterator toNodeOrigOutIter = toNodeOrigOutEdgeExplorer.setBaseNode(toNode);
                    while (toNodeOrigOutIter.next()) {
                        int origOutIterFirstOrigEdge = toNodeOrigOutIter.getFirstOrigEdge();
                        if (illegalUTurn(origOutIterFirstOrigEdge, targetEdge)) {
                            continue;
                        }
                        double origPathOutTurnWeight = turnWeighting.calcTurnWeight(targetEdge, toNode, origOutIterFirstOrigEdge);
                        if (Double.isInfinite(origPathOutTurnWeight)) {
                            continue;
                        }
                        boolean witnessFound = false;
                        EdgeIterator inIter = toNodeOrigInEdgeExplorer.setBaseNode(toNode);
                        while (inIter.next()) {
                            int origInIterLastOrigEdge = inIter.getLastOrigEdge();
                            if (origInIterLastOrigEdge == targetEdge) {
                                continue;
                            }
                            if (illegalUTurn(origOutIterFirstOrigEdge, origInIterLastOrigEdge)) {
                                continue;
                            }
                            // we need to protect against duplicate outgoing edges that sometimes occur in osm data
                            if (inIter.getAdjNode() == node) {
                                continue;
                            }
                            CHEntry potentialWitness = witnessPathFinder.getFoundEntryNoParents(origInIterLastOrigEdge, toNode);
                            if (potentialWitness == null || Double.isInfinite(potentialWitness.weight)) {
                                continue;
                            }
                            double witnessWeight = potentialWitness.weight + turnWeighting.calcTurnWeight(origInIterLastOrigEdge, toNode, origOutIterFirstOrigEdge);
                            final double tolerance = 1.e-12;
                            if (witnessWeight - tolerance < originalPath.weight + origPathOutTurnWeight) {
                                witnessFound = true;
                                break;
                            }
                        }
                        if (!witnessFound) {
                            double initialTurnCost = getTurnCost(origInIter.getLastOrigEdge(), fromNode, incomingEdges.getFirstOrigEdge());
                            originalPath.weight -= initialTurnCost;
                            handleShortcuts(originalPath);
                            break;
                        }
                    }
                }
            }
        }
    }

    private IntObjectMap<WitnessSearchEntry> getInitialEntriesAggressive(int fromNode, int node, EdgeIteratorState origPath, EdgeIteratorState origSourceEdge) {
        IntObjectMap<WitnessSearchEntry> initialEntries = new IntObjectHashMap<>();
        int numOnOrigPath = 0;
        EdgeIterator outIter = outEdgeExplorer.setBaseNode(fromNode);
        while (outIter.next()) {
            if (isContracted(outIter.getAdjNode())) {
                continue;
            }
            if (illegalUTurn(outIter.getFirstOrigEdge(), origSourceEdge.getLastOrigEdge())) {
                continue;
            }
            double weight = turnWeighting.calcWeight(outIter, false, origSourceEdge.getLastOrigEdge());
            if (Double.isInfinite(weight)) {
                continue;
            }
            WitnessSearchEntry entry = new WitnessSearchEntry(outIter.getEdge(), outIter.getLastOrigEdge(), outIter.getAdjNode(), weight);
            entry.parent = new WitnessSearchEntry(EdgeIterator.NO_EDGE, outIter.getFirstOrigEdge(), fromNode, 0);
            entry.onOrigPath = (outIter.getEdge() == origPath.getEdge());
            numOnOrigPath += insertOrUpdateInitialEntry(initialEntries, entry);
        }
        return numOnOrigPath > 0 ? initialEntries : new IntObjectHashMap<WitnessSearchEntry>();
    }

    private int findAndHandleShortcutsClassic(int node) {
        // todo: for osm data where there are only a very few turn restrictions (no left turn etc.) the graph
        // contraction should be much faster if we exploit that there are no turn costs on most nodes
        LOGGER.debug("Finding shortcuts for node {}, required shortcuts will be {}ed", node, activeShortcutHandler.getAction());
        stats().nodes++;
        resetEdgeCounters();
        int degree = 0;
        EdgeIterator incomingEdges = inEdgeExplorer.setBaseNode(node);
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
            IntObjectMap<WitnessSearchEntry> initialEntries = witnessSearchStrategy.getInitialEntries(fromNode, incomingEdges);
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

                CHEntry originalPath = witnessPathFinder.getFoundEntry(targetEdge, toNode);
                if (originalPath == null) {
                    continue;
                }
                LOGGER.trace("Witness path search to outgoing edge yielded {}", originalPath);
                if (witnessSearchStrategy.shortcutRequired(node, toNode, incomingEdges, outgoingEdges, witnessPathFinder, originalPath)) {
                    handleShortcuts(originalPath);
                }
            }
        }
        return degree;
    }

    @Override
    public String getPrepareAlgoMemoryUsage() {
        // todo: this method is currently misused to print some statistics for performance analysis
        String result = String.format("stats(calc): %s, stats(contract): %s, %s",
                countingShortcutHandler.getStats(), addingShortcutHandler.getStats(), witnessPathFinder.getStatusString());
        countingShortcutHandler.resetStats();
        addingShortcutHandler.resetStats();
        witnessPathFinder.resetStats();
        return result;
    }

    private void handleShortcuts(CHEntry chEntry) {
        LOGGER.trace("Adding shortcuts for target entry {}", chEntry);
        CHEntry root = chEntry.getParent();
        while (root.parent.edge != EdgeIterator.NO_EDGE) {
            root = root.getParent();
        }
        if (root.parent.adjNode == chEntry.adjNode &&
                //here we misuse root.parent.incEdge as first orig edge of the potential shortcut
                !loopShortcutNecessary(
                        chEntry.adjNode, root.getParent().incEdge, chEntry.incEdge, chEntry.weight)) {
            stats().loopsAvoided++;
            return;
        }
        // todo: note that we do not count loop-helper shortcuts here, but there are not that many usually
        stats().shortcutsNeeded++;
        activeShortcutHandler.handleShortcut(root, chEntry);
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
        LOGGER.debug("Adding shortcut from {} to {}, weight: {}, firstOrigEdge: {}, lastOrigEdge: {}",
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
        if (illegalUTurn(outEdge, inEdge)) {
            return Double.POSITIVE_INFINITY;
        }
        double turnCost = turnWeighting.calcTurnWeight(inEdge, node, outEdge);
        if (turnCost == 0 && inEdge == outEdge) {
            return turnWeighting.getDefaultUTurnCost();
        }
        return turnCost;
    }

    private boolean isContracted(int node) {
        return prepareGraph.getLevel(node) != maxLevel;
    }

    private void resetEdgeCounters() {
        numEdges = 0;
        numPrevEdges = 0;
        numOrigEdges = 0;
        numPrevOrigEdges = 0;
    }

    /**
     * Checks if the path leading to the given shortest path entry consists only of the incoming edge, the outgoing edge
     * and an arbitrary number of loops at the node.
     */
    private static boolean bestPathIsValidAndRequiresNode(
            CHEntry chEntry, int node, EdgeIteratorState incomingEdge, EdgeIteratorState outgoingEdge) {
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

    /**
     * @return the difference of possible shortcuts induced by the update/insert
     */
    private int insertOrUpdateInitialEntry(IntObjectMap<WitnessSearchEntry> initialEntries, WitnessSearchEntry entry) {
        int edgeKey = getEdgeKey(entry.incEdge, entry.adjNode);
        WitnessSearchEntry currEntry = initialEntries.get(edgeKey);
        if (currEntry == null) {
            LOGGER.trace("Adding/Updating initial entry {}", entry);
            initialEntries.put(edgeKey, entry);
            if (entry.onOrigPath) {
                return 1;
            }
        } else {
            // there may be entries with the same adjNode and last original edge, but we only need the one with
            // the lowest weight
            if (entry.weight < currEntry.weight) {
                int difference = 0;
                if (currEntry.onOrigPath) {
                    difference--;
                }
                if (entry.onOrigPath) {
                    difference++;
                }
                initialEntries.put(edgeKey, entry);
                LOGGER.trace("Adding/Updating initial entry {}", entry);
                return difference;
            }
        }
        return 0;
    }

    private boolean illegalUTurn(int inEdge, int outEdge) {
        return !traversalMode.hasUTurnSupport() && outEdge == inEdge;
    }

    private int getEdgeKey(int edge, int adjNode) {
        // todo: this is similar to some code in DijkstraBidirectionEdgeCHNoSOD and should be cleaned up, see comments there
        CHEdgeIteratorState eis = prepareGraph.getEdgeIteratorState(edge, adjNode);
        return GHUtility.createEdgeKey(eis.getBaseNode(), eis.getAdjNode(), eis.getEdge(), false);
    }

    private Stats stats() {
        return activeShortcutHandler.getStats();
    }

    private interface ShortcutHandler {
        void handleShortcut(CHEntry edgeFrom, CHEntry edgeTo);

        Stats getStats();

        void resetStats();

        String getAction();
    }

    private class AddingShortcutHandler implements ShortcutHandler {
        private Stats stats = new Stats();

        @Override
        public void handleShortcut(CHEntry edgeFrom, CHEntry edgeTo) {
            addShortcut(edgeFrom, edgeTo);
        }

        @Override
        public Stats getStats() {
            return stats;
        }

        @Override
        public void resetStats() {
            stats = new Stats();
        }

        @Override
        public String getAction() {
            return "add";
        }
    }

    private class CountingShortcutHandler implements ShortcutHandler {
        private Stats stats = new Stats();

        @Override
        public void handleShortcut(CHEntry edgeFrom, CHEntry edgeTo) {
            numEdges++;
            numOrigEdges += getOrigEdgeCount(edgeFrom.edge) + getOrigEdgeCount(edgeTo.edge);
        }

        @Override
        public Stats getStats() {
            return stats;
        }

        @Override
        public void resetStats() {
            stats = new Stats();
        }

        @Override
        public String getAction() {
            return "count";
        }
    }

    private interface WitnessSearchStrategy {
        IntObjectMap<WitnessSearchEntry> getInitialEntries(int fromNode, EdgeIteratorState incomingEdge);

        boolean shortcutRequired(int node, int toNode, EdgeIteratorState incomingEdges,
                                 EdgeIteratorState outgoingEdges, WitnessPathFinder witnessPathFinder, CHEntry entry);
    }

    /**
     * Modified version of the turn-replacement algorithm described in
     * 'Efficient Routing in Road Networks with Turn Costs' by R. Geisberger and C. Vetter.
     * This strategy is most efficient in deciding which shortcuts will not be required, but also needs to run the most
     * checks.
     * <p>
     * The basic idea is to not check for witnesses for all in/out edge pairs of the from/to nodes, but instead assume
     * the worst case with regards to turn costs at the from/to nodes and check if there is a witness path going from
     * the from- to the to-node that may use different first/last original edges than the original path. The difference
     * in turn costs is addressed by using the worst case turn costs and proving that the resulting path has still
     * lower weight than the original path.
     */
    private class TurnReplacementSearch implements WitnessSearchStrategy {
        @Override
        public IntObjectMap<WitnessSearchEntry> getInitialEntries(int fromNode, EdgeIteratorState incomingEdge) {
            final int firstOrigEdge = incomingEdge.getFirstOrigEdge();
            IntObjectMap<WitnessSearchEntry> initialEntries = new IntObjectHashMap<>();
            int numOnOrigPath = 0;
            EdgeIterator outIter = outEdgeExplorer.setBaseNode(fromNode);
            while (outIter.next()) {
                if (isContracted(outIter.getAdjNode()))
                    continue;
                double outTurnReplacementDifference = calcOutTurnReplacementDifference(fromNode, firstOrigEdge, outIter.getFirstOrigEdge());
                if (outTurnReplacementDifference == Double.POSITIVE_INFINITY) {
                    // we do not need an initial entry for this out-edge because it will never yield a witness
                    continue;
                } else if (outTurnReplacementDifference == Double.NEGATIVE_INFINITY) {
                    // we cannot reach the original path from any in-edge 
                    // -> we do not need to find a witness
                    return new IntObjectHashMap<>();
                }
                double weight = outTurnReplacementDifference + turnWeighting.calcWeight(outIter, false, EdgeIterator.NO_EDGE);
                WitnessSearchEntry entry = new WitnessSearchEntry(outIter.getEdge(), outIter.getLastOrigEdge(), outIter.getAdjNode(), weight);
                entry.parent = new WitnessSearchEntry(EdgeIterator.NO_EDGE, outIter.getFirstOrigEdge(), fromNode, 0);
                if (outIter.getEdge() == incomingEdge.getEdge()) {
                    entry.onOrigPath = true;
                    // we want to give witness paths the precedence in case the path weights would be equal
//                    entry.weight += 1.e-12;
                    // todo: apparently this can lead to strong deviations from dijkstra for example like this:
                    // bremen:
//                    Using seed 1470048333179
//                    error: found different points for query from 53.1663,8.6648 to 53.0915,8.8717, route weight: 1331.03 vs. 1196.64 (diff = 134.3840)
//                    error: found different points for query from 53.0691,8.8913 to 53.1019,8.8923, route weight: 547.67 vs. 547.70 (diff = -0.0349)
//                    error: found different points for query from 53.0794,8.7360 to 53.0579,8.9676, route weight: 1837.07 vs. 1599.81 (diff = 237.2532)
                    // --> disable for now, but do not really understand what is wrong about this
                }
                numOnOrigPath += insertOrUpdateInitialEntry(initialEntries, entry);
            }
            return numOnOrigPath > 0 ? initialEntries : new IntObjectHashMap<WitnessSearchEntry>();
        }

        /**
         * out turn replacement difference:
         * otrd(baseOutEdge, altOutEdge) := max[inEdge](turnCost(inEdge, altOutEdge) - turnCost(inEdge, baseOutEdge))
         *
         * @param u           node at which turn replacement difference is calculated
         * @param baseOutEdge baseline outgoing original edge, first edge of the original path
         * @param altOutEdge  alternative outgoing original edge, candidate for an initial entry
         * @return out turn replacement difference, negative infinity if the given baseOutEdge cannot be reached from
         * any inEdge (infinite turncost), positive infinity if there is an in-edge from which we can reach the
         * baseOutEdge (finite turncost) but not the altOutEdge (infinite alt-turncost)
         */
        private double calcOutTurnReplacementDifference(int u, int baseOutEdge, int altOutEdge) {
            double outTurnReplacementDifference = Double.NEGATIVE_INFINITY;
            EdgeIterator inEdge = fromNodeOrigInEdgeExplorer.setBaseNode(u);
            while (inEdge.next()) {
                double turnCost = getTurnCost(inEdge.getEdge(), u, baseOutEdge);
                if (Double.isInfinite(turnCost)) {
                    // we cannot reach the original path from this in-edge -> we can act as if it does not exist
                    // for example this is often the case if the base-out-edge is bidirectional.
                    // if there is no in-edge with finite turncost at all, we already know that we do not need any
                    // shortcuts
                    continue;
                }
                double alternativeTurnCost = getTurnCost(inEdge.getEdge(), u, altOutEdge);
                if (Double.isInfinite(alternativeTurnCost)) {
                    // there is at least one in-edge from which we cannot reach the given alt-out-edge, i.e. the turn 
                    // replacement difference will be infinite.

                    // note: this almost always happens because typically every out-edge is also an in-edge
                    // and u-turns are usually forbidden. this is why the turn replacement algorithm fails to find 
                    // a witness if a different witness path would be required for different in-edges

                    // note2: if altOutEdge = baseOutEdge we do not end up here because of the previous check and we
                    // always get an initial entry for the original path (as it should be). 
                    return Double.POSITIVE_INFINITY;
                }
                outTurnReplacementDifference = Math.max(outTurnReplacementDifference, alternativeTurnCost - turnCost);
            }
            return outTurnReplacementDifference;
        }

        @Override
        public boolean shortcutRequired(int node, int toNode, EdgeIteratorState incomingEdge,
                                        EdgeIteratorState outgoingEdge, WitnessPathFinder witnessPathFinder, CHEntry originalPath) {
            return bestPathIsValidAndRequiresNode(originalPath, node, incomingEdge, outgoingEdge)
                    && !alternativeWitnessExistsOrNotNeeded(node, outgoingEdge, toNode, witnessPathFinder, originalPath);
        }

        /**
         * Checks for witness paths for a given original path
         * <p>
         * This is a replacement for the incoming turn replacement difference calculation described in the above mentioned
         * paper. The latter does not allow finding a witness if different witness paths are required for different
         * outgoing edges and thus prevents finding some shortcuts, especially because most edges in road networks
         * are bidirectional,
         * see: EdgeBasedNodeContractorTest#testContractNode_noUnnecessaryShortcut_differentWitnessesForDifferentOutEdges
         * <p>
         * todo: the same should be possible by running a second search backwards from the target node, this time using
         * the worst case cost at the target node and then checking each edge at the from node separately
         */
        private boolean alternativeWitnessExistsOrNotNeeded(int node,
                                                            EdgeIteratorState outgoingEdge, int toNode, WitnessPathFinder witnessPathFinder, CHEntry originalPath) {
            EdgeIterator origOutIter = toNodeOrigOutEdgeExplorer.setBaseNode(toNode);
            final int originalPathLastOrigEdge = outgoingEdge.getLastOrigEdge();
            while (origOutIter.next()) {
                final int origOutIterFirstOrigEdge = origOutIter.getFirstOrigEdge();
                final double turnCost = getTurnCost(originalPathLastOrigEdge, toNode, origOutIterFirstOrigEdge);
                if (Double.isInfinite(turnCost)) {
                    // this outgoing edge is not accessible from the original path and we do not need to find a 
                    // witness for it
                    continue;
                }
                boolean foundWitness = false;
                EdgeIterator origInIter = toNodeOrigInEdgeExplorer.setBaseNode(toNode);
                while (origInIter.next()) {
                    final int origInIterLastOrigEdge = origInIter.getLastOrigEdge();
                    // the original path or any duplicate outgoing edges cannot serve as a witness
                    if (origInIterLastOrigEdge == originalPathLastOrigEdge || origInIter.getAdjNode() == node) {
                        continue;
                    }
                    CHEntry potentialWitness = witnessPathFinder.getFoundEntryNoParents(origInIterLastOrigEdge, toNode);
                    if (potentialWitness == null) {
                        // we did not find any witness path leading to this edge -> no witness
                        continue;
                    }
                    double alternativeTurnCost = getTurnCost(origInIterLastOrigEdge, toNode, origOutIterFirstOrigEdge);
                    final double tolerance = 1.e-12;
                    if (potentialWitness.weight + alternativeTurnCost - tolerance < originalPath.weight + turnCost) {
                        foundWitness = true;
                    }
                }
                if (!foundWitness) {
                    return false;
                }
            }
            // we have checked that for each outgoing original edge that is accessible from the original path there
            // is a witness --> we do not need a shortcut
            return true;
        }
    }

    /**
     * Simple local search algorithm as described in 'Efficient Routing in Road Networks with Turn Costs' by R. Geisberger
     * and C. Vetter. This strategy is simpler than {@link TurnReplacementSearch}, but introduces shortcuts that could
     * be avoided with the latter.
     */
    private class SimpleSearch implements WitnessSearchStrategy {
        @Override
        public IntObjectMap<WitnessSearchEntry> getInitialEntries(int fromNode, EdgeIteratorState incomingEdge) {
            IntObjectMap<WitnessSearchEntry> initialEntries = new IntObjectHashMap<>();
            int numOnOrigPath = 0;
            EdgeIterator outIter = outEdgeExplorer.setBaseNode(fromNode);
            while (outIter.next()) {
                if (outIter.getFirstOrigEdge() != incomingEdge.getFirstOrigEdge())
                    continue;
                if (isContracted(outIter.getAdjNode()))
                    continue;
                double weight = turnWeighting.calcWeight(outIter, false, EdgeIterator.NO_EDGE);
                WitnessSearchEntry entry = new WitnessSearchEntry(outIter.getEdge(), outIter.getLastOrigEdge(), outIter.getAdjNode(), weight);
                entry.parent = new WitnessSearchEntry(EdgeIterator.NO_EDGE, incomingEdge.getFirstOrigEdge(), fromNode, 0);
                entry.onOrigPath = (outIter.getEdge() == incomingEdge.getEdge());
                numOnOrigPath += insertOrUpdateInitialEntry(initialEntries, entry);
            }
            return numOnOrigPath > 0 ? initialEntries : new IntObjectHashMap<WitnessSearchEntry>();
        }

        @Override
        public boolean shortcutRequired(int node, int toNode, EdgeIteratorState incomingEdges, EdgeIteratorState outgoingEdges, WitnessPathFinder witnessPathFinder, CHEntry entry) {
            return bestPathIsValidAndRequiresNode(entry, node, incomingEdges, outgoingEdges);
        }
    }

    public static long twoIntsInLong(int p, int q) {
        return (((long) p << 32) | (q & 0xFFFFFFFFL));
    }

    private static class Stats {
        int nodes;
        long shortcutsChecked;
        long shortcutsNeeded;
        long loopsAvoided;
        long calcTime;

        @Override
        public String toString() {
            return String.format("runtime: %7.2f, nodes: %10s, scChecked: %10s, scNeeded: %10s, scNotNeeded: %10s, loopsAvoided: %10s",
                    calcTime * 1.e-9, nf(nodes), nf(shortcutsChecked), nf(shortcutsNeeded),
                    nf(shortcutsChecked - shortcutsNeeded), nf(loopsAvoided));
        }
    }
}
