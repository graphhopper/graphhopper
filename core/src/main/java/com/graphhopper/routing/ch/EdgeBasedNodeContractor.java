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
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PMap;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
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
 * {@link EdgeBasedWitnessPathSearcher}.
 *
 * @author easbar
 */
class EdgeBasedNodeContractor extends AbstractNodeContractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeBasedNodeContractor.class);
    private final Params params = new Params();
    private final PMap pMap;
    private final StopWatch dijkstraSW = new StopWatch();
    private final IntSet sourceNodes = new IntHashSet(10);
    private final IntSet toNodes = new IntHashSet(10);
    private final Stats addingStats = new Stats();
    private final Stats countingStats = new Stats();
    private Stats activeStats;
    private int[] hierarchyDepths;
    private EdgeBasedWitnessPathSearcher witnessPathSearcher;
    private PrepareCHEdgeExplorer existingShortcutExplorer;
    private PrepareCHEdgeExplorer allEdgeExplorer;
    private PrepareCHEdgeExplorer sourceNodeOrigInEdgeExplorer;
    private PrepareCHEdgeExplorer targetNodeOrigOutEdgeExplorer;

    // counts the total number of added shortcuts
    private int addedShortcutsCount;

    // edge counts used to calculate priority
    private int numShortcuts;
    private int numPrevEdges;
    private int numOrigEdges;
    private int numPrevOrigEdges;
    private int numAllEdges;

    // counters used for performance analysis
    private int numPolledEdges;

    public EdgeBasedNodeContractor(PrepareCHGraph prepareGraph, PMap pMap) {
        super(prepareGraph);
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
        witnessPathSearcher = new EdgeBasedWitnessPathSearcher(prepareGraph, pMap);
        inEdgeExplorer = prepareGraph.createInEdgeExplorer();
        outEdgeExplorer = prepareGraph.createOutEdgeExplorer();
        allEdgeExplorer = prepareGraph.createAllEdgeExplorer();
        existingShortcutExplorer = prepareGraph.createOutEdgeExplorer();
        sourceNodeOrigInEdgeExplorer = prepareGraph.createOriginalInEdgeExplorer();
        targetNodeOrigOutEdgeExplorer = prepareGraph.createOriginalOutEdgeExplorer();
        hierarchyDepths = new int[prepareGraph.getNodes()];
    }

    @Override
    public void prepareContraction() {
        // not needed 
    }

    @Override
    public float calculatePriority(int node) {
        activeStats = countingStats;
        resetEdgeCounters();
        countPreviousEdges(node);
        if (numAllEdges == 0)
            // this node is isolated, maybe it belongs to a removed subnetwork, in any case we can quickly contract it
            // no shortcuts will be introduced
            return Float.NEGATIVE_INFINITY;
        stats().stopWatch.start();
        findAndHandleShortcuts(node, this::countShortcuts);
        stats().stopWatch.stop();
        // the higher the priority the later (!) this node will be contracted
        float edgeQuotient = numShortcuts / (float) numPrevEdges;
        float origEdgeQuotient = numOrigEdges / (float) numPrevOrigEdges;
        int hierarchyDepth = hierarchyDepths[node];
        float priority = params.edgeQuotientWeight * edgeQuotient +
                params.originalEdgeQuotientWeight * origEdgeQuotient +
                params.hierarchyDepthWeight * hierarchyDepth;
        if (LOGGER.isTraceEnabled())
            LOGGER.trace("node: {}, eq: {} / {} = {}, oeq: {} / {} = {}, depth: {} --> {}",
                    node,
                    numShortcuts, numPrevEdges, edgeQuotient,
                    numOrigEdges, numPrevOrigEdges, origEdgeQuotient,
                    hierarchyDepth, priority);
        return priority;
    }

    @Override
    public void contractNode(int node) {
        activeStats = addingStats;
        stats().stopWatch.start();
        findAndHandleShortcuts(node, this::addShortcut);
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
        String result = "sc-handler-count: " + countingStats + ", sc-handler-contract: " + addingStats + ", " +
                witnessPathSearcher.getStatisticsString();
        witnessPathSearcher.resetStats();
        return result;
    }

    public int getNumPolledEdges() {
        return numPolledEdges;
    }

    private void findAndHandleShortcuts(int node, ShortcutHandler shortcutHandler) {
        numPolledEdges = 0;
        stats().nodes++;
        Set<AddedShortcut> addedShortcuts = new HashSet<>();

        // first we need to identify the possible source nodes from which we can reach the center node
        sourceNodes.clear();
        PrepareCHEdgeIterator incomingEdges = inEdgeExplorer.setBaseNode(node);
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
            PrepareCHEdgeIterator origInIter = sourceNodeOrigInEdgeExplorer.setBaseNode(sourceNode);
            while (origInIter.next()) {
                int numInitialEntries = witnessPathSearcher.initSearch(node, sourceNode, origInIter.getOrigEdgeLast());
                if (numInitialEntries < 1) {
                    continue;
                }

                // now we need to identify all target nodes that can be reached from the center node
                toNodes.clear();
                PrepareCHEdgeIterator outgoingEdges = outEdgeExplorer.setBaseNode(node);
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
                    PrepareCHEdgeIterator targetEdgeIter = targetNodeOrigOutEdgeExplorer.setBaseNode(targetNode);
                    while (targetEdgeIter.next()) {
                        int targetEdge = targetEdgeIter.getOrigEdgeFirst();
                        dijkstraSW.start();
                        CHEntry entry = witnessPathSearcher.runSearch(targetNode, targetEdge);
                        dijkstraSW.stop();
                        if (entry == null || Double.isInfinite(entry.weight)) {
                            continue;
                        }
                        CHEntry root = entry.getParent();
                        while (EdgeIterator.Edge.isValid(root.parent.edge)) {
                            root = root.getParent();
                        }
                        // removing this 'optimization' improves contraction time, but introduces more
                        // shortcuts (makes slower queries). note that 'duplicate' shortcuts get detected at time
                        // of insertion when running with adding shortcut handler, but not when we are only counting.
                        // only running this check while counting does not seem to improve contraction time a lot.
                        AddedShortcut addedShortcut = new AddedShortcut(sourceNode, root.getParent().incEdge, targetNode, entry.incEdge);
                        if (addedShortcuts.contains(addedShortcut)) {
                            continue;
                        }
                        // root parent weight was misused to store initial turn cost here
                        double initialTurnCost = root.getParent().weight;
                        entry.weight -= initialTurnCost;
                        LOGGER.trace("Adding shortcuts for target entry {}", entry);
                        // todo: re-implement loop-avoidance heuristic as it existed in GH 1.0? it did not work the
                        // way it was implemented so it was removed.
                        shortcutHandler.handleShortcut(root, entry);
                        addedShortcuts.add(addedShortcut);
                    }
                }
                numPolledEdges += witnessPathSearcher.getNumPolledEdges();
            }
        }
    }

    private void countPreviousEdges(int node) {
        // todo: this edge counting can probably be simplified, but we might need to re-optimize heuristic parameters then
        PrepareCHEdgeIterator outIter = outEdgeExplorer.setBaseNode(node);
        while (outIter.next()) {
            if (isContracted(outIter.getAdjNode()))
                continue;
            numPrevEdges++;
            if (!outIter.isShortcut()) {
                numPrevOrigEdges++;
            }
        }

        PrepareCHEdgeIterator inIter = inEdgeExplorer.setBaseNode(node);
        while (inIter.next()) {
            if (isContracted(inIter.getAdjNode()))
                continue;
            // do not consider loop edges a second time
            if (inIter.getBaseNode() == inIter.getAdjNode())
                continue;
            numPrevEdges++;
            if (!inIter.isShortcut()) {
                numPrevOrigEdges++;
            }
        }

        PrepareCHEdgeIterator allIter = allEdgeExplorer.setBaseNode(node);
        while (allIter.next()) {
            numAllEdges++;
            if (isContracted(allIter.getAdjNode()))
                continue;
            if (allIter.isShortcut()) {
                numPrevOrigEdges += getOrigEdgeCount(allIter.getEdge());
            }
        }
    }

    private void updateHierarchyDepthsOfNeighbors(int node) {
        PrepareCHEdgeIterator iter = allEdgeExplorer.setBaseNode(node);
        while (iter.next()) {
            if (isContracted(iter.getAdjNode()) || iter.getAdjNode() == node)
                continue;
            hierarchyDepths[iter.getAdjNode()] = Math.max(hierarchyDepths[iter.getAdjNode()], hierarchyDepths[node] + 1);
        }
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

        final PrepareCHEdgeIterator iter = existingShortcutExplorer.setBaseNode(from);
        while (iter.next()) {
            if (!isSameShortcut(iter, adjNode, edgeFrom.getParent().incEdge, edgeTo.incEdge)) {
                // this is some other (shortcut) edge, we do not care
                continue;
            }
            final double existingWeight = iter.getWeight(false);
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
        int accessFlags = PrepareEncoder.getScFwdDir();
        int shortcutId = prepareGraph.shortcutEdgeBased(from, adjNode, accessFlags, edgeTo.weight, edgeFrom.edge, edgeTo.edge, origFirst, edgeTo.incEdge);
        final int origEdgeCount = getOrigEdgeCount(edgeFrom.edge) + getOrigEdgeCount(edgeTo.edge);
        setOrigEdgeCount(shortcutId, origEdgeCount);
        addedShortcutsCount++;
        CHEntry entry = new CHEntry(shortcutId, shortcutId, edgeTo.adjNode, edgeTo.weight);
        entry.parent = edgeFrom.parent;
        return entry;
    }

    private boolean isSameShortcut(PrepareCHEdgeIterator iter, int adjNode, int firstOrigEdge, int lastOrigEdge) {
        return iter.isShortcut()
                && (iter.getAdjNode() == adjNode)
                && (iter.getOrigEdgeFirst() == firstOrigEdge)
                && (iter.getOrigEdgeLast() == lastOrigEdge);
    }

    private void resetEdgeCounters() {
        numShortcuts = 0;
        numPrevEdges = 0;
        numOrigEdges = 0;
        numPrevOrigEdges = 0;
        numAllEdges = 0;
    }

    private Stats stats() {
        return activeStats;
    }

    @FunctionalInterface
    private interface ShortcutHandler {
        void handleShortcut(CHEntry edgeFrom, CHEntry edgeTo);
    }

    private void countShortcuts(CHEntry edgeFrom, CHEntry edgeTo) {
        int fromNode = edgeFrom.parent.adjNode;
        int toNode = edgeTo.adjNode;
        int firstOrigEdge = edgeFrom.getParent().incEdge;
        int lastOrigEdge = edgeTo.incEdge;

        // check if this shortcut already exists
        final PrepareCHEdgeIterator iter = existingShortcutExplorer.setBaseNode(fromNode);
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

    public static class Params {
        // todo: optimize
        private float edgeQuotientWeight = 1;
        private float originalEdgeQuotientWeight = 3;
        private float hierarchyDepthWeight = 2;
    }

    private static class Stats {
        int nodes;
        StopWatch stopWatch = new StopWatch();

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "time: %7.2fs, nodes-handled: %10s", stopWatch.getCurrentSeconds(), nf(nodes));
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
            return 31 * startNode + endNode;
        }
    }

}
