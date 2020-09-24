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
import com.graphhopper.util.PMap;
import com.graphhopper.util.StopWatch;

import java.util.Locale;

import static com.graphhopper.routing.ch.CHParameters.EDGE_DIFFERENCE_WEIGHT;
import static com.graphhopper.routing.ch.CHParameters.ORIGINAL_EDGE_COUNT_WEIGHT;
import static com.graphhopper.util.Helper.nf;

class NodeBasedNodeContractor implements NodeContractor {
    private final CHPreparationGraph prepareGraph;
    private final Params params = new Params();
    private ShortcutHandler shortcutHandler;
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

    NodeBasedNodeContractor(CHPreparationGraph prepareGraph, ShortcutHandler shortcutHandler, PMap pMap) {
        this.prepareGraph = prepareGraph;
        extractParams(pMap);
        this.shortcutHandler = shortcutHandler;
    }

    private void extractParams(PMap pMap) {
        params.edgeDifferenceWeight = pMap.getFloat(EDGE_DIFFERENCE_WEIGHT, params.edgeDifferenceWeight);
        params.originalEdgesCountWeight = pMap.getFloat(ORIGINAL_EDGE_COUNT_WEIGHT, params.originalEdgesCountWeight);
    }

    @Override
    public void initFromGraph() {
        inEdgeExplorer = prepareGraph.createInEdgeExplorer();
        outEdgeExplorer = prepareGraph.createOutEdgeExplorer();
        existingShortcutExplorer = prepareGraph.createOutEdgeExplorer();
        witnessPathSearcher = new NodeBasedWitnessPathSearcher(prepareGraph);
    }

    @Override
    public void prepareContraction() {
        // todo: initializing meanDegree here instead of in initFromGraph() means that in the first round of calculating
        // node priorities all shortcut searches are cancelled immediately and all possible shortcuts are counted because
        // no witness path can be found. this is not really what we want, but changing it requires re-optimizing the
        // graph contraction parameters, because it affects the node contraction order.
        // when this is done there should be no need for this method any longer.
        meanDegree = prepareGraph.getOriginalEdges() / prepareGraph.getNodes();
    }

    @Override
    public void close() {
        prepareGraph.close();
        shortcutHandler = null;
        inEdgeExplorer = null;
        outEdgeExplorer = null;
        existingShortcutExplorer = null;
        witnessPathSearcher.close();
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
        findAndHandleShortcuts(node, this::countShortcuts);

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
        long degree = findAndHandleShortcuts(node, this::addOrUpdateShortcut);
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
        shortcutHandler.startContractingNode();
        {
            PrepareGraphEdgeIterator iter = outEdgeExplorer.setBaseNode(node);
            while (iter.next()) {
                if (!iter.isShortcut())
                    continue;
                shortcutHandler.addOutShortcut(iter.getPrepareEdge(), node, iter.getAdjNode(), iter.getSkipped1(), iter.getSkipped2(), iter.getWeight());
            }
        }
        {
            PrepareGraphEdgeIterator iter = inEdgeExplorer.setBaseNode(node);
            while (iter.next()) {
                if (!iter.isShortcut())
                    continue;
                shortcutHandler.addInShortcut(iter.getPrepareEdge(), node, iter.getAdjNode(), iter.getSkipped2(), iter.getSkipped1(), iter.getWeight());
            }
        }
        addedShortcutsCount += shortcutHandler.finishContractingNode();
    }

    @Override
    public void finishContraction() {
        shortcutHandler.finishContraction();
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
    private long findAndHandleShortcuts(int node, PrepareShortcutHandler handler) {
        int maxVisitedNodes = getMaxVisitedNodesEstimate();
        long degree = 0;
        PrepareGraphEdgeIterator incomingEdges = inEdgeExplorer.setBaseNode(node);
        // collect outgoing nodes (goal-nodes) only once
        while (incomingEdges.next()) {
            int fromNode = incomingEdges.getAdjNode();
            // do not consider loops at the node that is being contracted
            if (fromNode == node)
                continue;

            final double incomingEdgeWeight = incomingEdges.getWeight();
            // this check is important to prevent calling calcMillis on inaccessible edges and also allows early exit
            if (Double.isInfinite(incomingEdgeWeight)) {
                continue;
            }
            // collect outgoing nodes (goal-nodes) only once
            PrepareGraphEdgeIterator outgoingEdges = outEdgeExplorer.setBaseNode(node);
            // force fresh maps etc as this cannot be determined by from node alone (e.g. same from node but different avoidNode)
            witnessPathSearcher.clear();
            degree++;
            while (outgoingEdges.next()) {
                int toNode = outgoingEdges.getAdjNode();
                // do not consider loops at the node that is being contracted
                if (toNode == node || fromNode == toNode)
                    continue;

                // Limit weight as ferries or forbidden edges can increase local search too much.
                // If we decrease the correct weight we only explore less and introduce more shortcuts.
                // I.e. no change to accuracy is made.
                double existingDirectWeight = incomingEdgeWeight + outgoingEdges.getWeight();
                if (Double.isInfinite(existingDirectWeight))
                    continue;

                witnessPathSearcher.setWeightLimit(existingDirectWeight);
                witnessPathSearcher.setMaxVisitedNodes(maxVisitedNodes);
                witnessPathSearcher.ignoreNode(node);

                dijkstraSW.start();
                dijkstraCount++;
                int endNode = witnessPathSearcher.findEndNode(fromNode, toNode);
                dijkstraSW.stop();

                // compare end node as the limit could force dijkstra to finish earlier
                if (endNode == toNode && witnessPathSearcher.getWeight(endNode) <= existingDirectWeight)
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
    public long getDijkstraCount() {
        return dijkstraCount;
    }

    @Override
    public float getDijkstraSeconds() {
        return dijkstraSW.getCurrentSeconds();
    }

    private int getMaxVisitedNodesEstimate() {
        // todo: we return 0 here if meanDegree is < 1, which is not really what we want, but changing this changes
        // the node contraction order and requires re-optimizing the parameters of the graph contraction
        return (int) meanDegree * 100;
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
    }

    /**
     * This handler is called on every shortcut that this contractor finds necessary to add for the contracted node.
     */
    public interface ShortcutHandler {
        /**
         * Use this hook for any kind of initialization to be done before a node is contracted
         */
        void startContractingNode();

        /**
         * This method is called for every shortcut outgoing from the contracted node and found by the contractor
         */
        void addOutShortcut(int prepareEdge, int node, int adjNode, int skipped1, int skipped2, double weight);

        /**
         * This method is called for every shortcut incoming to the contracted node and found by the contractor.
         * Incoming shortcuts are added only *after* all outgoing ones were add.
         */
        void addInShortcut(int prepareEdge, int node, int adjNode, int skipped1, int skipped2, double weight);

        /**
         * Use this hook for any kind of post-processing after the node is contracted
         *
         * @return the actual number of shortcuts that were added to the graph for this node
         */
        int finishContractingNode();

        /**
         * This method is called at the very end of the graph contraction (after the last node was contracted)
         */
        void finishContraction();
    }
}
