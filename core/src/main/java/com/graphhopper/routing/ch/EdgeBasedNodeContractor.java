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

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntSet;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.graphhopper.routing.ch.CHParameters.*;
import static com.graphhopper.util.Helper.nf;

/**
 * This class is used to calculate the priority of or contract a given node in edge-based Contraction Hierarchies as it
 * is required to support turn-costs. This implementation follows the 'aggressive' variant described in
 * 'Efficient Routing in Road Networks with Turn Costs' by R. Geisberger and C. Vetter. Here, we do not store the center
 * node for each shortcut, but introduce helper shortcuts when a loop shortcut is encountered.
 * <p>
 * This class is mostly concerned with triggering the required local searches and introducing the necessary shortcuts
 * or calculating the node priority, while the actual searches for witness paths are delegated to
 * {@link WitnessPathSearcher}.
 *
 * @author easbar
 */
class EdgeBasedNodeContractor extends AbstractNodeContractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeBasedNodeContractor.class);
    private final TurnWeighting turnWeighting;
    private final FlagEncoder encoder;
    private final ShortcutHandler addingShortcutHandler = new AddingShortcutHandler();
    private final ShortcutHandler countingShortcutHandler = new CountingShortcutHandler();
    private final Params params = new Params();
    private final PMap pMap;
    private ShortcutHandler activeShortcutHandler;
    private final StopWatch dijkstraSW = new StopWatch();
    private final SearchStrategy activeStrategy = new AggressiveStrategy();
    private int[] hierarchyDepths;
    private WitnessPathSearcher witnessPathSearcher;
    private CHEdgeExplorer existingShortcutExplorer;
    private CHEdgeExplorer allEdgeExplorer;
    private EdgeExplorer sourceNodeOrigInEdgeExplorer;
    private EdgeExplorer targetNodeOrigOutEdgeExplorer;
    private EdgeExplorer loopAvoidanceInEdgeExplorer;
    private EdgeExplorer loopAvoidanceOutEdgeExplorer;

    // counts the total number of added shortcuts
    private int addedShortcutsCount;

    // edge counts used to calculate priority
    private int numShortcuts;
    private int numPrevEdges;
    private int numOrigEdges;
    private int numPrevOrigEdges;

    // counters used for performance analysis
    private int numPolledEdges;

    public EdgeBasedNodeContractor(CHGraph prepareGraph,
                                   TurnWeighting turnWeighting, PMap pMap) {
        super(prepareGraph, turnWeighting);
        this.turnWeighting = turnWeighting;
        this.encoder = turnWeighting.getFlagEncoder();
        this.pMap = pMap;
        extractParams(pMap);
    }

    private void extractParams(PMap pMap) {
        params.edgeQuotientWeight = pMap.getFloat(EDGE_QUOTIENT_WEIGHT, params.edgeQuotientWeight);
        params.originalEdgeQuotientWeight = pMap.getFloat(ORIGINAL_EDGE_QUOTIENT_WEIGHT, params.originalEdgeQuotientWeight);
        params.hierarchyDepthWeight = pMap.getFloat(HIERARCHY_DEPTH_WEIGHT, params.hierarchyDepthWeight);
    }

    @Override
    public void initFromGraph() {
        super.initFromGraph();
        witnessPathSearcher = new WitnessPathSearcher(prepareGraph, turnWeighting, pMap);
        DefaultEdgeFilter inEdgeFilter = DefaultEdgeFilter.inEdges(encoder);
        DefaultEdgeFilter outEdgeFilter = DefaultEdgeFilter.outEdges(encoder);
        DefaultEdgeFilter allEdgeFilter = DefaultEdgeFilter.allEdges(encoder);
        inEdgeExplorer = prepareGraph.createEdgeExplorer(inEdgeFilter);
        outEdgeExplorer = prepareGraph.createEdgeExplorer(outEdgeFilter);
        allEdgeExplorer = prepareGraph.createEdgeExplorer(allEdgeFilter);
        existingShortcutExplorer = prepareGraph.createEdgeExplorer(outEdgeFilter);
        sourceNodeOrigInEdgeExplorer = prepareGraph.createOriginalEdgeExplorer(inEdgeFilter);
        targetNodeOrigOutEdgeExplorer = prepareGraph.createOriginalEdgeExplorer(outEdgeFilter);
        loopAvoidanceInEdgeExplorer = prepareGraph.createOriginalEdgeExplorer(inEdgeFilter);
        loopAvoidanceOutEdgeExplorer = prepareGraph.createOriginalEdgeExplorer(outEdgeFilter);
        hierarchyDepths = new int[prepareGraph.getNodes()];
    }

    @Override
    public void prepareContraction() {
        // not needed 
    }

    @Override
    public float calculatePriority(int node) {
        activeShortcutHandler = countingShortcutHandler;
        stats().stopWatch.start();
        findAndHandleShortcuts(node);
        stats().stopWatch.stop();
        countPreviousEdges(node);
        // the higher the priority the later (!) this node will be contracted
        float edgeQuotient = numShortcuts / (float) numPrevEdges;
        float origEdgeQuotient = numOrigEdges / (float) numPrevOrigEdges;
        int hierarchyDepth = hierarchyDepths[node];
        float priority = params.edgeQuotientWeight * edgeQuotient +
                params.originalEdgeQuotientWeight * origEdgeQuotient +
                params.hierarchyDepthWeight * hierarchyDepth;
        LOGGER.trace("node: %d, eq: %d / %d = %f, oeq: %d / %d = %f, depth: %d --> %f\n",
                node,
                numShortcuts, numPrevEdges, edgeQuotient,
                numOrigEdges, numPrevOrigEdges, origEdgeQuotient,
                hierarchyDepth, priority);
        return priority;
    }

    @Override
    public void contractNode(int node) {
        activeShortcutHandler = addingShortcutHandler;
        stats().stopWatch.start();
        findAndHandleShortcuts(node);
        updateHierarchyDepthsOfNeighbors(node);
        stats().stopWatch.stop();
    }

    @Override
    public long getAddedShortcutsCount() {
        return addedShortcutsCount;
    }

    @Override
    public long getDijkstraCount() {
        return witnessPathSearcher.getTotalNumSearches();
    }

    @Override
    public float getDijkstraSeconds() {
        return dijkstraSW.getCurrentSeconds();
    }

    @Override
    public String getStatisticsString() {
        String result =
                "sc-handler-count: " + countingShortcutHandler.getStats() + ", " +
                        "sc-handler-contract: " + addingShortcutHandler.getStats() + ", " +
                        activeStrategy.getStatisticsString();
        activeStrategy.resetStats();
        return result;
    }

    public int getNumPolledEdges() {
        return numPolledEdges;
    }

    @Override
    boolean isEdgeBased() {
        return true;
    }

    private void findAndHandleShortcuts(int node) {
        numPolledEdges = 0;
        activeStrategy.findAndHandleShortcuts(node);
    }

    private void countPreviousEdges(int node) {
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        CHEdgeIterator iter = allEdgeExplorer.setBaseNode(node);
        while (iter.next()) {
            if (isContracted(iter.getAdjNode()))
                continue;
            if (iter.get(accessEnc)) {
                numPrevEdges++;
            }
            if (iter.getReverse(accessEnc)) {
                numPrevEdges++;
            }
            if (!iter.isShortcut()) {
                if (iter.get(accessEnc)) {
                    numPrevOrigEdges++;
                }
                if (iter.getReverse(accessEnc)) {
                    numPrevOrigEdges++;
                }
            } else {
                numPrevOrigEdges += getOrigEdgeCount(iter.getEdge());
            }
        }
    }

    private void updateHierarchyDepthsOfNeighbors(int node) {
        CHEdgeIterator iter = allEdgeExplorer.setBaseNode(node);
        while (iter.next()) {
            if (isContracted(iter.getAdjNode()) || iter.getAdjNode() == node)
                continue;
            hierarchyDepths[iter.getAdjNode()] = Math.max(hierarchyDepths[iter.getAdjNode()], hierarchyDepths[node] + 1);
        }
    }

    private void handleShortcuts(CHEntry chEntry, CHEntry root) {
        LOGGER.trace("Adding shortcuts for target entry {}", chEntry);
        if (root.parent.adjNode == chEntry.adjNode &&
                //here we misuse root.parent.incEdge as first orig edge of the potential shortcut
                !loopShortcutNecessary(
                        chEntry.adjNode, root.getParent().incEdge, chEntry.incEdge, chEntry.weight)) {
            stats().loopsAvoided++;
            return;
        }
        activeShortcutHandler.handleShortcut(root, chEntry);
    }

    /**
     * A given potential loop shortcut is only necessary if there is at least one pair of original in- & out-edges for
     * which taking the loop is cheaper than doing the direct turn. However this is almost always the case, because
     * doing a u-turn at any of the incoming edges is forbidden, i.e. he costs of the direct turn will be infinite.
     */
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

        final CHEdgeIterator iter = existingShortcutExplorer.setBaseNode(from);
        while (iter.next()) {
            if (!isSameShortcut(iter, adjNode, edgeFrom.getParent().incEdge, edgeTo.incEdge)) {
                // this is some other (shortcut) edge, we do not care
                continue;
            }
            final double existingWeight = turnWeighting.calcWeight(iter, false, EdgeIterator.NO_EDGE);
            if (existingWeight <= edgeTo.weight) {
                // our shortcut already exists with lower weight --> do nothing
                CHEntry entry = new CHEntry(iter.getEdge(), iter.getOrigEdgeLast(), adjNode, existingWeight);
                entry.parent = edgeFrom.parent;
                return entry;
            } else {
                // update weight
                iter.setSkippedEdges(edgeFrom.edge, edgeTo.edge);
                iter.setWeight(edgeTo.weight);
                CHEntry entry = new CHEntry(iter.getEdge(), iter.getOrigEdgeLast(), adjNode, edgeTo.weight);
                entry.parent = edgeFrom.parent;
                return entry;
            }
        }

        // our shortcut is new --> add it
        // this is a bit of a hack, we misuse incEdge of edgeFrom's parent to store the first orig edge
        int origFirst = edgeFrom.getParent().incEdge;
        LOGGER.trace("Adding shortcut from {} to {}, weight: {}, firstOrigEdge: {}, lastOrigEdge: {}",
                from, adjNode, edgeTo.weight, edgeFrom.getParent().incEdge, edgeTo.incEdge);
        // todo: so far we are not using the distance in edge based CH
        double distance = 0.0;
        int accessFlags = PrepareEncoder.getScFwdDir();
        int shortcutId = prepareGraph.shortcutEdgeBased(from, adjNode, accessFlags, edgeTo.weight, distance, edgeFrom.edge, edgeTo.edge, origFirst, edgeTo.incEdge);
        final int origEdgeCount = getOrigEdgeCount(edgeFrom.edge) + getOrigEdgeCount(edgeTo.edge);
        setOrigEdgeCount(shortcutId, origEdgeCount);
        addedShortcutsCount++;
        CHEntry entry = new CHEntry(shortcutId, shortcutId, edgeTo.adjNode, edgeTo.weight);
        entry.parent = edgeFrom.parent;
        return entry;
    }

    private boolean isSameShortcut(CHEdgeIteratorState iter, int adjNode, int firstOrigEdge, int lastOrigEdge) {
        return iter.isShortcut()
                && (iter.getAdjNode() == adjNode)
                && (iter.getOrigEdgeFirst() == firstOrigEdge)
                && (iter.getOrigEdgeLast() == lastOrigEdge);
    }

    private double getTurnCost(int inEdge, int node, int outEdge) {
        if (illegalUTurn(outEdge, inEdge)) {
            return Double.POSITIVE_INFINITY;
        }
        return turnWeighting.calcTurnWeight(inEdge, node, outEdge);
    }

    private void resetEdgeCounters() {
        numShortcuts = 0;
        numPrevEdges = 0;
        numOrigEdges = 0;
        numPrevOrigEdges = 0;
    }

    private boolean illegalUTurn(int inEdge, int outEdge) {
        return outEdge == inEdge;
    }

    private Stats stats() {
        return activeShortcutHandler.getStats();
    }

    private interface ShortcutHandler {

        void handleShortcut(CHEntry edgeFrom, CHEntry edgeTo);

        Stats getStats();

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
        public String getAction() {
            return "add";
        }
    }

    private class CountingShortcutHandler implements ShortcutHandler {
        private Stats stats = new Stats();

        @Override
        public void handleShortcut(CHEntry edgeFrom, CHEntry edgeTo) {
            int fromNode = edgeFrom.parent.adjNode;
            int toNode = edgeTo.adjNode;
            int firstOrigEdge = edgeFrom.getParent().incEdge;
            int lastOrigEdge = edgeTo.incEdge;

            // check if this shortcut already exists
            final CHEdgeIterator iter = existingShortcutExplorer.setBaseNode(fromNode);
            while (iter.next()) {
                if (isSameShortcut(iter, toNode, firstOrigEdge, lastOrigEdge)) {
                    // this shortcut exists already, maybe its weight will be updated but we should not count it as
                    // a new edge
                    return;
                }
            }

            // this shortcut is new --> increase counts
            numShortcuts++;
            numOrigEdges += getOrigEdgeCount(edgeFrom.edge) + getOrigEdgeCount(edgeTo.edge);
        }

        @Override
        public Stats getStats() {
            return stats;
        }

        @Override
        public String getAction() {
            return "count";
        }
    }

    public static class Params {
        // todo: optimize
        private float edgeQuotientWeight = 1;
        private float originalEdgeQuotientWeight = 3;
        private float hierarchyDepthWeight = 2;
    }

    private static class Stats {
        int nodes;
        long loopsAvoided;
        StopWatch stopWatch = new StopWatch();

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "time: %7.2fs, nodes-handled: %10s, loopsAvoided: %10s",
                    stopWatch.getCurrentSeconds(), nf(nodes), nf(loopsAvoided));
        }
    }

    private interface SearchStrategy {
        void findAndHandleShortcuts(int node);

        String getStatisticsString();

        void resetStats();

    }

    private class AggressiveStrategy implements SearchStrategy {
        @Override
        public String getStatisticsString() {
            return witnessPathSearcher.getStatisticsString();
        }

        @Override
        public void resetStats() {
            witnessPathSearcher.resetStats();
        }

        @Override
        public void findAndHandleShortcuts(int node) {
            LOGGER.trace("Finding shortcuts (aggressive) for node {}, required shortcuts will be {}ed", node, activeShortcutHandler.getAction());
            stats().nodes++;
            resetEdgeCounters();
            Set<AddedShortcut> addedShortcuts = new HashSet<>();

            // first we need to identify the possible source nodes from which we can reach the center node
            // todo: optimize collection size
            IntSet sourceNodes = new IntHashSet(100);
            EdgeIterator incomingEdges = inEdgeExplorer.setBaseNode(node);
            while (incomingEdges.next()) {
                int sourceNode = incomingEdges.getAdjNode();
                if (isContracted(sourceNode) || sourceNode == node) {
                    continue;
                }
                boolean isNewSourceNode = sourceNodes.add(sourceNode);
                if (!isNewSourceNode) {
                    continue;
                }
                // for each source node we need to look at every incoming original edge and find the initial entries
                EdgeIterator origInIter = sourceNodeOrigInEdgeExplorer.setBaseNode(sourceNode);
                while (origInIter.next()) {
                    int numInitialEntries = witnessPathSearcher.initSearch(node, sourceNode, origInIter.getOrigEdgeLast());
                    if (numInitialEntries < 1) {
                        continue;
                    }

                    // now we need to identify all target nodes that can be reached from the center node
                    // todo: optimize collection size
                    IntSet toNodes = new IntHashSet(100);
                    EdgeIterator outgoingEdges = outEdgeExplorer.setBaseNode(node);
                    while (outgoingEdges.next()) {
                        int targetNode = outgoingEdges.getAdjNode();
                        if (isContracted(targetNode) || targetNode == node) {
                            continue;
                        }
                        boolean isNewTargetNode = toNodes.add(targetNode);
                        if (!isNewTargetNode) {
                            continue;
                        }
                        // for each target edge outgoing from a target node we need to check if reaching it requires
                        // a 'bridge-path'
                        EdgeIterator targetEdgeIter = targetNodeOrigOutEdgeExplorer.setBaseNode(targetNode);
                        while (targetEdgeIter.next()) {
                            int targetEdge = targetEdgeIter.getOrigEdgeFirst();
                            dijkstraSW.start();
                            CHEntry entry = witnessPathSearcher.runSearch(targetNode, targetEdge);
                            dijkstraSW.stop();
                            if (entry == null || Double.isInfinite(entry.weight)) {
                                continue;
                            }
                            CHEntry root = entry.getParent();
                            while (root.parent.edge != EdgeIterator.NO_EDGE) {
                                root = root.getParent();
                            }
                            // todo: removing this 'optimization' improves contraction time significantly, but introduces 
                            // more shortcuts (makes slower queries). why is this so ? any 'duplicate' shortcuts should be detected at time of
                            // insertion !??
                            AddedShortcut addedShortcut = new AddedShortcut(sourceNode, root.getParent().incEdge, targetNode, entry.incEdge);
                            if (addedShortcuts.contains(addedShortcut)) {
                                continue;
                            }
                            // root parent weight was misused to store initial turn cost here
                            double initialTurnCost = root.getParent().weight;
                            entry.weight -= initialTurnCost;
                            handleShortcuts(entry, root);
                            addedShortcuts.add(addedShortcut);
                        }
                    }
                    numPolledEdges += witnessPathSearcher.getNumPolledEdges();
                }
            }
        }
    }

    private static class AddedShortcut {
        int startNode;
        int startEdge;
        int endNode;
        int targetEdge;

        public AddedShortcut(int startNode, int startEdge, int endNode, int targetEdge) {
            this.startNode = startNode;
            this.startEdge = startEdge;
            this.endNode = endNode;
            this.targetEdge = targetEdge;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AddedShortcut that = (AddedShortcut) o;
            return startNode == that.startNode &&
                    startEdge == that.startEdge &&
                    endNode == that.endNode &&
                    targetEdge == that.targetEdge;
        }

        @Override
        public int hashCode() {
            return Objects.hash(startNode, startEdge, endNode, targetEdge);
        }
    }

}
