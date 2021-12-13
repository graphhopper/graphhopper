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

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.storage.CHStorageBuilder;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

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
class EdgeBasedNodeContractor implements NodeContractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeBasedNodeContractor.class);
    private final CHPreparationGraph prepareGraph;
    private PrepareGraphEdgeExplorer inEdgeExplorer;
    private PrepareGraphEdgeExplorer outEdgeExplorer;
    private PrepareGraphEdgeExplorer existingShortcutExplorer;
    private PrepareGraphOrigEdgeExplorer sourceNodeOrigInEdgeExplorer;
    private PrepareGraphOrigEdgeExplorer targetNodeOrigOutEdgeExplorer;
    private CHStorageBuilder chBuilder;
    private final Params params = new Params();
    private final PMap pMap;
    private final StopWatch dijkstraSW = new StopWatch();
    // temporary data used during node contraction
    private final IntSet sourceNodes = new IntHashSet(10);
    private final IntSet targetNodes = new IntHashSet(10);
    private final LongSet addedShortcuts = new LongHashSet();
    private final Stats addingStats = new Stats();
    private final Stats countingStats = new Stats();
    private Stats activeStats;

    private int[] hierarchyDepths;
    private EdgeBasedWitnessPathSearcher witnessPathSearcher;

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

    public EdgeBasedNodeContractor(CHPreparationGraph prepareGraph, CHStorageBuilder chBuilder, PMap pMap) {
        this.prepareGraph = prepareGraph;
        this.chBuilder = chBuilder;
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
        inEdgeExplorer = prepareGraph.createInEdgeExplorer();
        outEdgeExplorer = prepareGraph.createOutEdgeExplorer();
        existingShortcutExplorer = prepareGraph.createOutEdgeExplorer();
        sourceNodeOrigInEdgeExplorer = prepareGraph.createInOrigEdgeExplorer();
        targetNodeOrigOutEdgeExplorer = prepareGraph.createOutOrigEdgeExplorer();
        witnessPathSearcher = new EdgeBasedWitnessPathSearcher(prepareGraph, pMap);
        hierarchyDepths = new int[prepareGraph.getNodes()];
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
        findAndHandlePrepareShortcuts(node, this::countShortcuts);
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
    public IntContainer contractNode(int node) {
        activeStats = addingStats;
        stats().stopWatch.start();
        findAndHandlePrepareShortcuts(node, this::addShortcutsToPrepareGraph);
        insertShortcuts(node);
        IntContainer neighbors = prepareGraph.disconnect(node);
        updateHierarchyDepthsOfNeighbors(node, neighbors);
        stats().stopWatch.stop();
        return neighbors;
    }

    @Override
    public void finishContraction() {
        chBuilder.replaceSkippedEdges(prepareGraph::getShortcutForPrepareEdge);
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

    /**
     * This method performs witness searches between all nodes adjacent to the given node and calls the
     * given handler for all required shortcuts.
     */
    private void findAndHandlePrepareShortcuts(int node, PrepareShortcutHandler shortcutHandler) {
        numPolledEdges = 0;
        stats().nodes++;
        addedShortcuts.clear();

        // first we need to identify the possible source nodes from which we can reach the center node
        sourceNodes.clear();
        PrepareGraphEdgeIterator incomingEdges = inEdgeExplorer.setBaseNode(node);
        while (incomingEdges.next()) {
            int sourceNode = incomingEdges.getAdjNode();
            if (sourceNode == node) {
                continue;
            }
            boolean isNewSourceNode = sourceNodes.add(sourceNode);
            if (!isNewSourceNode) {
                continue;
            }
            // for each source node we need to look at every incoming original edge and find the initial entries
            PrepareGraphOrigEdgeIterator origInIter = sourceNodeOrigInEdgeExplorer.setBaseNode(sourceNode);
            while (origInIter.next()) {
                int numInitialEntries = witnessPathSearcher.initSearch(node, sourceNode, GHUtility.getEdgeFromEdgeKey(origInIter.getOrigEdgeKeyLast()));
                if (numInitialEntries < 1) {
                    continue;
                }

                // now we need to identify all target nodes that can be reached from the center node
                targetNodes.clear();
                PrepareGraphEdgeIterator outgoingEdges = outEdgeExplorer.setBaseNode(node);
                while (outgoingEdges.next()) {
                    int targetNode = outgoingEdges.getAdjNode();
                    if (targetNode == node) {
                        continue;
                    }
                    boolean isNewTargetNode = targetNodes.add(targetNode);
                    if (!isNewTargetNode) {
                        continue;
                    }
                    // for each target edge outgoing from a target node we need to check if reaching it requires
                    // a 'bridge-path'
                    PrepareGraphOrigEdgeIterator targetEdgeIter = targetNodeOrigOutEdgeExplorer.setBaseNode(targetNode);
                    while (targetEdgeIter.next()) {
                        dijkstraSW.start();
                        PrepareCHEntry entry = witnessPathSearcher.runSearch(targetNode, GHUtility.getEdgeFromEdgeKey(targetEdgeIter.getOrigEdgeKeyFirst()));
                        dijkstraSW.stop();
                        if (entry == null || Double.isInfinite(entry.weight)) {
                            continue;
                        }
                        PrepareCHEntry root = entry.getParent();
                        while (EdgeIterator.Edge.isValid(root.parent.prepareEdge)) {
                            root = root.getParent();
                        }
                        // removing this 'optimization' improves contraction time, but introduces more
                        // shortcuts (makes slower queries). note that we are not detecting 'duplicate' shortcuts at a later
                        // stage again, especially when we are just running with the counting handler.
                        long addedShortcutKey = BitUtil.LITTLE.combineIntsToLong(root.getParent().incEdgeKey, entry.incEdgeKey);
                        if (!addedShortcuts.add(addedShortcutKey))
                            continue;
                        // root parent weight was misused to store initial turn cost here
                        double initialTurnCost = root.getParent().weight;
                        entry.weight -= initialTurnCost;
                        LOGGER.trace("Adding shortcuts for target entry {}", entry);
                        // todo: re-implement loop-avoidance heuristic as it existed in GH 1.0? it did not work the
                        // way it was implemented so it was removed.
                        shortcutHandler.handleShortcut(root, entry, incomingEdges.getOrigEdgeCount() + outgoingEdges.getOrigEdgeCount());
                    }
                }
                numPolledEdges += witnessPathSearcher.getNumPolledEdges();
            }
        }
    }

    /**
     * Calls the shortcut handler for all edges and shortcuts adjacent to the given node. After this method is called
     * these edges and shortcuts will be removed from the prepare graph, so this method offers the last chance to deal
     * with them.
     */
    private void insertShortcuts(int node) {
        insertOutShortcuts(node);
        insertInShortcuts(node);
    }

    private void insertOutShortcuts(int node) {
        PrepareGraphEdgeIterator iter = outEdgeExplorer.setBaseNode(node);
        while (iter.next()) {
            if (!iter.isShortcut())
                continue;
            int shortcut = chBuilder.addShortcutEdgeBased(node, iter.getAdjNode(),
                    PrepareEncoder.getScFwdDir(), iter.getWeight(),
                    iter.getSkipped1(), iter.getSkipped2(),
                    GHUtility.getEdgeFromEdgeKey(iter.getOrigEdgeKeyFirst()),
                    GHUtility.getEdgeFromEdgeKey(iter.getOrigEdgeKeyLast()));
            prepareGraph.setShortcutForPrepareEdge(iter.getPrepareEdge(), prepareGraph.getOriginalEdges() + shortcut);
            addedShortcutsCount++;
        }
    }

    private void insertInShortcuts(int node) {
        PrepareGraphEdgeIterator iter = inEdgeExplorer.setBaseNode(node);
        while (iter.next()) {
            if (!iter.isShortcut())
                continue;
            // we added loops already using the outEdgeExplorer
            if (iter.getAdjNode() == node)
                continue;
            int shortcut = chBuilder.addShortcutEdgeBased(node, iter.getAdjNode(),
                    PrepareEncoder.getScBwdDir(), iter.getWeight(),
                    iter.getSkipped1(), iter.getSkipped2(),
                    GHUtility.getEdgeFromEdgeKey(iter.getOrigEdgeKeyFirst()),
                    GHUtility.getEdgeFromEdgeKey(iter.getOrigEdgeKeyLast()));
            prepareGraph.setShortcutForPrepareEdge(iter.getPrepareEdge(), prepareGraph.getOriginalEdges() + shortcut);
            addedShortcutsCount++;
        }
    }

    private void countPreviousEdges(int node) {
        // todo: this edge counting can probably be simplified, but we might need to re-optimize heuristic parameters then
        PrepareGraphEdgeIterator outIter = outEdgeExplorer.setBaseNode(node);
        while (outIter.next()) {
            numAllEdges++;
            numPrevEdges++;
            numPrevOrigEdges += outIter.getOrigEdgeCount();
        }

        PrepareGraphEdgeIterator inIter = inEdgeExplorer.setBaseNode(node);
        while (inIter.next()) {
            numAllEdges++;
            // do not consider loop edges a second time
            if (inIter.getBaseNode() == inIter.getAdjNode())
                continue;
            numPrevEdges++;
            numPrevOrigEdges += inIter.getOrigEdgeCount();
        }
    }

    private void updateHierarchyDepthsOfNeighbors(int node, IntContainer neighbors) {
        int level = hierarchyDepths[node];
        for (IntCursor n : neighbors) {
            if (n.value == node)
                continue;
            hierarchyDepths[n.value] = Math.max(hierarchyDepths[n.value], level + 1);
        }
    }

    private PrepareCHEntry addShortcutsToPrepareGraph(PrepareCHEntry edgeFrom, PrepareCHEntry edgeTo, int origEdgeCount) {
        if (edgeTo.parent.prepareEdge != edgeFrom.prepareEdge) {
            // counting origEdgeCount correctly is tricky with loop shortcuts and the recursion we use here. so we
            // simply ignore this, it probably does not matter that much
            PrepareCHEntry prev = addShortcutsToPrepareGraph(edgeFrom, edgeTo.getParent(), origEdgeCount);
            return doAddShortcut(prev, edgeTo, origEdgeCount);
        } else {
            return doAddShortcut(edgeFrom, edgeTo, origEdgeCount);
        }
    }

    private PrepareCHEntry doAddShortcut(PrepareCHEntry edgeFrom, PrepareCHEntry edgeTo, int origEdgeCount) {
        int from = edgeFrom.parent.adjNode;
        int adjNode = edgeTo.adjNode;

        final PrepareGraphEdgeIterator iter = existingShortcutExplorer.setBaseNode(from);
        while (iter.next()) {
            if (!isSameShortcut(iter, adjNode, edgeFrom.getParent().incEdgeKey, edgeTo.incEdgeKey)) {
                // this is some other (shortcut) edge -> we do not care
                continue;
            }
            final double existingWeight = iter.getWeight();
            if (existingWeight <= edgeTo.weight) {
                // our shortcut already exists with lower weight --> do nothing
                PrepareCHEntry entry = new PrepareCHEntry(iter.getPrepareEdge(), iter.getOrigEdgeKeyLast(), adjNode, existingWeight);
                entry.parent = edgeFrom.parent;
                return entry;
            } else {
                // update weight
                iter.setSkippedEdges(edgeFrom.prepareEdge, edgeTo.prepareEdge);
                iter.setWeight(edgeTo.weight);
                iter.setOrigEdgeCount(origEdgeCount);
                PrepareCHEntry entry = new PrepareCHEntry(iter.getPrepareEdge(), iter.getOrigEdgeKeyLast(), adjNode, edgeTo.weight);
                entry.parent = edgeFrom.parent;
                return entry;
            }
        }

        // our shortcut is new --> add it
        // this is a bit of a hack, we misuse incEdgeKey of edgeFrom's parent to store the first orig edge
        int origFirstKey = edgeFrom.getParent().incEdgeKey;
        LOGGER.trace("Adding shortcut from {} to {}, weight: {}, firstOrigEdgeKey: {}, lastOrigEdgeKey: {}",
                from, adjNode, edgeTo.weight, origFirstKey, edgeTo.incEdgeKey);
        int prepareEdge = prepareGraph.addShortcut(from, adjNode, origFirstKey, edgeTo.incEdgeKey, edgeFrom.prepareEdge, edgeTo.prepareEdge, edgeTo.weight, origEdgeCount);
        // does not matter here
        int incEdgeKey = -1;
        PrepareCHEntry entry = new PrepareCHEntry(prepareEdge, incEdgeKey, edgeTo.adjNode, edgeTo.weight);
        entry.parent = edgeFrom.parent;
        return entry;
    }

    private boolean isSameShortcut(PrepareGraphEdgeIterator iter, int adjNode, int firstOrigEdgeKey, int lastOrigEdgeKey) {
        return iter.isShortcut()
                && (iter.getAdjNode() == adjNode)
                && (iter.getOrigEdgeKeyFirst() == firstOrigEdgeKey)
                && (iter.getOrigEdgeKeyLast() == lastOrigEdgeKey);
    }

    private void resetEdgeCounters() {
        numShortcuts = 0;
        numPrevEdges = 0;
        numOrigEdges = 0;
        numPrevOrigEdges = 0;
        numAllEdges = 0;
    }

    @Override
    public void close() {
        prepareGraph.close();
        inEdgeExplorer = null;
        outEdgeExplorer = null;
        existingShortcutExplorer = null;
        sourceNodeOrigInEdgeExplorer = null;
        targetNodeOrigOutEdgeExplorer = null;
        chBuilder = null;
        witnessPathSearcher.close();
        sourceNodes.release();
        targetNodes.release();
        addedShortcuts.release();
        hierarchyDepths = null;
    }

    private Stats stats() {
        return activeStats;
    }

    @FunctionalInterface
    private interface PrepareShortcutHandler {
        void handleShortcut(PrepareCHEntry edgeFrom, PrepareCHEntry edgeTo, int origEdgeCount);
    }

    private void countShortcuts(PrepareCHEntry edgeFrom, PrepareCHEntry edgeTo, int origEdgeCount) {
        int fromNode = edgeFrom.parent.adjNode;
        int toNode = edgeTo.adjNode;
        int firstOrigEdgeKey = edgeFrom.getParent().incEdgeKey;
        int lastOrigEdgeKey = edgeTo.incEdgeKey;

        // check if this shortcut already exists
        final PrepareGraphEdgeIterator iter = existingShortcutExplorer.setBaseNode(fromNode);
        while (iter.next()) {
            if (isSameShortcut(iter, toNode, firstOrigEdgeKey, lastOrigEdgeKey)) {
                // this shortcut exists already, maybe its weight will be updated but we should not count it as
                // a new edge
                return;
            }
        }

        // this shortcut is new --> increase counts
        numShortcuts++;
        numOrigEdges += origEdgeCount;
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

}
