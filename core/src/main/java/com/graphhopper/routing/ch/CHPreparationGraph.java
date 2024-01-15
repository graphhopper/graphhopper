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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntContainer;
import com.carrotsearch.hppc.IntScatterSet;
import com.carrotsearch.hppc.IntSet;
import com.carrotsearch.hppc.sorting.IndirectComparator;
import com.carrotsearch.hppc.sorting.IndirectSort;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.GHUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.graphhopper.util.ArrayUtil.zero;

/**
 * Graph data structure used for CH preparation. It allows caching weights, and edges that are not needed anymore
 * (those adjacent to contracted nodes) can be removed (see {@link #disconnect}.
 *
 * @author easbar
 */
public class CHPreparationGraph {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHPreparationGraph.class);
    private final int nodes;
    private final int edges;
    private final boolean edgeBased;
    private final TurnCostFunction turnCostFunction;
    // each edge/shortcut between nodes a/b is represented as a single object and we maintain two linked lists of such
    // objects for every node (one for outgoing edges and one for incoming edges).
    private PrepareEdge[] prepareEdgesOut;
    private PrepareEdge[] prepareEdgesIn;
    // todo: it should be possible to store the 'skipped node' for each shortcut instead of storing the shortcut for
    //       each prepare edge. but this is a bit tricky for edge-based, because of our bidir shortcuts for node-based,
    //       and because basegraph has multi-edges. the advantage of storing the skipped node is that we could just write
    //       it to one of the skipped edges fields temporarily, so we would not need this array and save memory during
    //       the preparation.
    private IntArrayList shortcutsByPrepareEdges;
    // todo: maybe we can get rid of this
    private int[] degrees;
    private IntSet neighborSet;
    private OrigGraph origGraph;
    private OrigGraph.Builder origGraphBuilder;
    private int nextShortcutId;
    private boolean ready;

    public static CHPreparationGraph nodeBased(int nodes, int edges) {
        return new CHPreparationGraph(nodes, edges, false, (in, via, out) -> 0);
    }

    public static CHPreparationGraph edgeBased(int nodes, int edges, TurnCostFunction turnCostFunction) {
        return new CHPreparationGraph(nodes, edges, true, turnCostFunction);
    }

    /**
     * @param nodes (fixed) number of nodes of the graph
     * @param edges the maximum number of (non-shortcut) edges in this graph. edges-1 is the maximum edge id that may
     *              be used.
     */
    private CHPreparationGraph(int nodes, int edges, boolean edgeBased, TurnCostFunction turnCostFunction) {
        this.turnCostFunction = turnCostFunction;
        this.nodes = nodes;
        this.edges = edges;
        this.edgeBased = edgeBased;
        prepareEdgesOut = new PrepareEdge[nodes];
        prepareEdgesIn = new PrepareEdge[nodes];
        shortcutsByPrepareEdges = new IntArrayList();
        degrees = new int[nodes];
        origGraphBuilder = edgeBased ? new OrigGraph.Builder() : null;
        neighborSet = new IntScatterSet();
        nextShortcutId = edges;
    }

    public static void buildFromGraph(CHPreparationGraph prepareGraph, Graph graph, Weighting weighting) {
        if (graph.getNodes() != prepareGraph.getNodes())
            throw new IllegalArgumentException("Cannot initialize from given graph. The number of nodes does not match: " +
                    graph.getNodes() + " vs. " + prepareGraph.getNodes());
        if (graph.getEdges() != prepareGraph.getOriginalEdges())
            throw new IllegalArgumentException("Cannot initialize from given graph. The number of edges does not match: " +
                    graph.getEdges() + " vs. " + prepareGraph.getOriginalEdges());
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            double weightFwd = weighting.calcEdgeWeight(iter, false);
            double weightBwd = weighting.calcEdgeWeight(iter, true);
            prepareGraph.addEdge(iter.getBaseNode(), iter.getAdjNode(), iter.getEdge(), weightFwd, weightBwd);
        }
        prepareGraph.prepareForContraction();
    }

    public static TurnCostFunction buildTurnCostFunctionFromTurnCostStorage(Graph graph, Weighting weighting) {
        // At some point we used an optimized version where we copied the turn costs to sorted arrays
        // temporarily. This seemed to be around 25% faster according to measurements on the Bavaria
        // map, but for bigger maps the improvement is less, around 10% for planet. See also #2084
        return weighting::calcTurnWeight;
    }

    public int getNodes() {
        return nodes;
    }

    public int getOriginalEdges() {
        return edges;
    }

    public int getDegree(int node) {
        return degrees[node];
    }

    public void addEdge(int from, int to, int edge, double weightFwd, double weightBwd) {
        checkNotReady();
        if (from == to)
            throw new IllegalArgumentException("Loop edges are no longer supported since #2862");
        boolean fwd = Double.isFinite(weightFwd);
        boolean bwd = Double.isFinite(weightBwd);
        if (!fwd && !bwd)
            return;
        PrepareBaseEdge prepareEdge = new PrepareBaseEdge(edge, from, to, (float) weightFwd, (float) weightBwd);
        if (fwd) {
            addOutEdge(from, prepareEdge);
            addInEdge(to, prepareEdge);
        }
        if (bwd && from != to) {
            addOutEdge(to, prepareEdge);
            addInEdge(from, prepareEdge);
        }
        if (edgeBased)
            origGraphBuilder.addEdge(from, to, edge, fwd, bwd);
    }

    public int addShortcut(int from, int to, int origEdgeKeyFirst, int origEdgeKeyLast, int skipped1,
                           int skipped2, double weight, int origEdgeCount) {
        checkReady();
        PrepareEdge prepareEdge = edgeBased
                ? new EdgeBasedPrepareShortcut(nextShortcutId, from, to, origEdgeKeyFirst, origEdgeKeyLast, weight, skipped1, skipped2, origEdgeCount)
                : new PrepareShortcut(nextShortcutId, from, to, weight, skipped1, skipped2, origEdgeCount);
        addOutEdge(from, prepareEdge);
        if (from != to)
            addInEdge(to, prepareEdge);
        if (nextShortcutId > Integer.MAX_VALUE - 10)
            LOGGER.warn("nextShortcutId is very large! " + nextShortcutId);
        if (nextShortcutId == Integer.MAX_VALUE - 1)
            throw new IllegalStateException("Maximum shortcut number exceeded!");
        return nextShortcutId++;
    }

    public void prepareForContraction() {
        checkNotReady();
        origGraph = edgeBased ? origGraphBuilder.build() : null;
        origGraphBuilder = null;
        ready = true;
    }

    public void setShortcutForPrepareEdge(int prepareEdge, int shortcut) {
        int index = prepareEdge - edges;
        if (index >= shortcutsByPrepareEdges.size())
            shortcutsByPrepareEdges.resize(index + 1);
        shortcutsByPrepareEdges.set(index, shortcut);
    }

    public int getShortcutForPrepareEdge(int prepareEdge) {
        if (prepareEdge < edges)
            return prepareEdge;
        int index = prepareEdge - edges;
        return shortcutsByPrepareEdges.get(index);
    }

    public PrepareGraphEdgeExplorer createOutEdgeExplorer() {
        checkReady();
        return new PrepareGraphEdgeExplorerImpl(prepareEdgesOut, false);
    }

    public PrepareGraphEdgeExplorer createInEdgeExplorer() {
        checkReady();
        return new PrepareGraphEdgeExplorerImpl(prepareEdgesIn, true);
    }

    public PrepareGraphOrigEdgeExplorer createOutOrigEdgeExplorer() {
        checkReady();
        if (!edgeBased)
            throw new IllegalStateException("orig out explorer is not available for node-based graph");
        return origGraph.createOutOrigEdgeExplorer();
    }

    public PrepareGraphOrigEdgeExplorer createInOrigEdgeExplorer() {
        checkReady();
        if (!edgeBased)
            throw new IllegalStateException("orig in explorer is not available for node-based graph");
        return origGraph.createInOrigEdgeExplorer();
    }

    public double getTurnWeight(int inEdgeKey, int viaNode, int outEdgeKey) {
        return turnCostFunction.getTurnWeight(GHUtility.getEdgeFromEdgeKey(inEdgeKey), viaNode, GHUtility.getEdgeFromEdgeKey(outEdgeKey));
    }

    public IntContainer disconnect(int node) {
        checkReady();
        // we use this neighbor set to guarantee a deterministic order of the returned
        // node ids
        neighborSet.clear();
        PrepareEdge currOut = prepareEdgesOut[node];
        while (currOut != null) {
            int adjNode = currOut.getNodeB();
            if (adjNode == node)
                adjNode = currOut.getNodeA();
            if (adjNode == node) {
                // this is a loop
                currOut = currOut.getNextOut(node);
                continue;
            }
            removeInEdge(adjNode, currOut);
            neighborSet.add(adjNode);
            currOut = currOut.getNextOut(node);
        }
        PrepareEdge currIn = prepareEdgesIn[node];
        while (currIn != null) {
            int adjNode = currIn.getNodeB();
            if (adjNode == node)
                adjNode = currIn.getNodeA();
            if (adjNode == node) {
                // this is a loop
                currIn = currIn.getNextIn(node);
                continue;
            }
            removeOutEdge(adjNode, currIn);
            neighborSet.add(adjNode);
            currIn = currIn.getNextIn(node);
        }
        prepareEdgesOut[node] = null;
        prepareEdgesIn[node] = null;
        degrees[node] = 0;
        return neighborSet;
    }

    private void removeOutEdge(int node, PrepareEdge prepareEdge) {
        PrepareEdge prevOut = null;
        PrepareEdge currOut = prepareEdgesOut[node];
        while (currOut != null) {
            if (currOut == prepareEdge) {
                if (prevOut == null) {
                    prepareEdgesOut[node] = currOut.getNextOut(node);
                } else {
                    prevOut.setNextOut(node, currOut.getNextOut(node));
                }
                degrees[node]--;
            } else {
                prevOut = currOut;
            }
            currOut = currOut.getNextOut(node);
        }
    }

    private void removeInEdge(int node, PrepareEdge prepareEdge) {
        PrepareEdge prevIn = null;
        PrepareEdge currIn = prepareEdgesIn[node];
        while (currIn != null) {
            if (currIn == prepareEdge) {
                if (prevIn == null) {
                    prepareEdgesIn[node] = currIn.getNextIn(node);
                } else {
                    prevIn.setNextIn(node, currIn.getNextIn(node));
                }
                degrees[node]--;
            } else {
                prevIn = currIn;
            }
            currIn = currIn.getNextIn(node);
        }
    }

    public void close() {
        checkReady();
        prepareEdgesOut = null;
        prepareEdgesIn = null;
        shortcutsByPrepareEdges = null;
        degrees = null;
        neighborSet = null;
        if (edgeBased)
            origGraph = null;
    }

    private void addOutEdge(int node, PrepareEdge prepareEdge) {
        prepareEdge.setNextOut(node, prepareEdgesOut[node]);
        prepareEdgesOut[node] = prepareEdge;
        degrees[node]++;
    }

    private void addInEdge(int node, PrepareEdge prepareEdge) {
        prepareEdge.setNextIn(node, prepareEdgesIn[node]);
        prepareEdgesIn[node] = prepareEdge;
        degrees[node]++;
    }

    private void checkReady() {
        if (!ready)
            throw new IllegalStateException("You need to call prepareForContraction() before calling this method");
    }

    private void checkNotReady() {
        if (ready)
            throw new IllegalStateException("You cannot call this method after calling prepareForContraction()");
    }

    @FunctionalInterface
    public interface TurnCostFunction {
        double getTurnWeight(int inEdge, int viaNode, int outEdge);
    }

    private static class PrepareGraphEdgeExplorerImpl implements PrepareGraphEdgeExplorer, PrepareGraphEdgeIterator {
        private final PrepareEdge[] prepareEdges;
        private final boolean reverse;
        private int node = -1;
        private PrepareEdge currEdge;
        private PrepareEdge nextEdge;

        PrepareGraphEdgeExplorerImpl(PrepareEdge[] prepareEdges, boolean reverse) {
            this.prepareEdges = prepareEdges;
            this.reverse = reverse;
        }

        @Override
        public PrepareGraphEdgeIterator setBaseNode(int node) {
            this.node = node;
            currEdge = null;
            nextEdge = prepareEdges[node];
            return this;
        }

        @Override
        public boolean next() {
            currEdge = nextEdge;
            if (currEdge == null)
                return false;
            nextEdge = reverse ? currEdge.getNextIn(node) : currEdge.getNextOut(node);
            return true;
        }

        @Override
        public int getBaseNode() {
            return node;
        }

        @Override
        public int getAdjNode() {
            return nodeAisBase() ? currEdge.getNodeB() : currEdge.getNodeA();
        }

        @Override
        public int getPrepareEdge() {
            return currEdge.getPrepareEdge();
        }

        @Override
        public boolean isShortcut() {
            return currEdge.isShortcut();
        }

        @Override
        public int getOrigEdgeKeyFirst() {
            return nodeAisBase() ? currEdge.getOrigEdgeKeyFirstAB() : currEdge.getOrigEdgeKeyFirstBA();
        }

        @Override
        public int getOrigEdgeKeyLast() {
            return nodeAisBase() ? currEdge.getOrigEdgeKeyLastAB() : currEdge.getOrigEdgeKeyLastBA();
        }

        @Override
        public int getSkipped1() {
            return currEdge.getSkipped1();
        }

        @Override
        public int getSkipped2() {
            return currEdge.getSkipped2();
        }

        @Override
        public double getWeight() {
            if (nodeAisBase()) {
                return reverse ? currEdge.getWeightBA() : currEdge.getWeightAB();
            } else {
                return reverse ? currEdge.getWeightAB() : currEdge.getWeightBA();
            }
        }

        @Override
        public int getOrigEdgeCount() {
            return currEdge.getOrigEdgeCount();
        }

        @Override
        public void setSkippedEdges(int skipped1, int skipped2) {
            currEdge.setSkipped1(skipped1);
            currEdge.setSkipped2(skipped2);
        }

        @Override
        public void setWeight(double weight) {
            assert Double.isFinite(weight);
            currEdge.setWeight(weight);
        }

        @Override
        public void setOrigEdgeCount(int origEdgeCount) {
            currEdge.setOrigEdgeCount(origEdgeCount);
        }

        @Override
        public String toString() {
            return currEdge == null ? "not_started" : currEdge.toString();
        }

        private boolean nodeAisBase() {
            // in some cases we need to determine which direction of the (bidirectional) edge we want
            return currEdge.getNodeA() == node;
        }
    }

    interface PrepareEdge {
        boolean isShortcut();

        int getPrepareEdge();

        int getNodeA();

        int getNodeB();

        double getWeightAB();

        double getWeightBA();

        int getOrigEdgeKeyFirstAB();

        int getOrigEdgeKeyFirstBA();

        int getOrigEdgeKeyLastAB();

        int getOrigEdgeKeyLastBA();

        int getSkipped1();

        int getSkipped2();

        int getOrigEdgeCount();

        void setSkipped1(int skipped1);

        void setSkipped2(int skipped2);

        void setWeight(double weight);

        void setOrigEdgeCount(int origEdgeCount);

        PrepareEdge getNextOut(int base);

        void setNextOut(int base, PrepareEdge prepareEdge);

        PrepareEdge getNextIn(int base);

        void setNextIn(int base, PrepareEdge prepareEdge);

    }

    public static class PrepareBaseEdge implements PrepareEdge {
        private final int prepareEdge;
        private final int nodeA;
        private final int nodeB;
        private final float weightAB;
        private final float weightBA;
        private PrepareEdge nextOutA;
        private PrepareEdge nextOutB;
        private PrepareEdge nextInA;
        private PrepareEdge nextInB;

        public PrepareBaseEdge(int prepareEdge, int nodeA, int nodeB, float weightAB, float weightBA) {
            this.prepareEdge = prepareEdge;
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.weightAB = weightAB;
            this.weightBA = weightBA;
        }

        @Override
        public boolean isShortcut() {
            return false;
        }

        @Override
        public int getPrepareEdge() {
            return prepareEdge;
        }

        @Override
        public int getNodeA() {
            return nodeA;
        }

        @Override
        public int getNodeB() {
            return nodeB;
        }

        @Override
        public double getWeightAB() {
            return weightAB;
        }

        @Override
        public double getWeightBA() {
            return weightBA;
        }

        @Override
        public int getOrigEdgeKeyFirstAB() {
            return GHUtility.createEdgeKey(prepareEdge, false);
        }

        @Override
        public int getOrigEdgeKeyFirstBA() {
            return GHUtility.createEdgeKey(prepareEdge, true);
        }

        @Override
        public int getOrigEdgeKeyLastAB() {
            return getOrigEdgeKeyFirstAB();
        }

        @Override
        public int getOrigEdgeKeyLastBA() {
            return getOrigEdgeKeyFirstBA();
        }

        @Override
        public int getSkipped1() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getSkipped2() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getOrigEdgeCount() {
            return 1;
        }

        @Override
        public void setSkipped1(int skipped1) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSkipped2(int skipped2) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setWeight(double weight) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setOrigEdgeCount(int origEdgeCount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrepareEdge getNextOut(int base) {
            if (base == nodeA)
                return nextOutA;
            else if (base == nodeB)
                return nextOutB;
            else
                throw new IllegalStateException("Cannot get next out edge as the given base " + base + " is not adjacent to the current edge");
        }

        @Override
        public void setNextOut(int base, PrepareEdge prepareEdge) {
            if (base == nodeA)
                nextOutA = prepareEdge;
            else if (base == nodeB)
                nextOutB = prepareEdge;
            else
                throw new IllegalStateException("Cannot set next out edge as the given base " + base + " is not adjacent to the current edge");
        }

        @Override
        public PrepareEdge getNextIn(int base) {
            if (base == nodeA)
                return nextInA;
            else if (base == nodeB)
                return nextInB;
            else
                throw new IllegalStateException("Cannot get next in edge as the given base " + base + " is not adjacent to the current edge");
        }

        @Override
        public void setNextIn(int base, PrepareEdge prepareEdge) {
            if (base == nodeA)
                nextInA = prepareEdge;
            else if (base == nodeB)
                nextInB = prepareEdge;
            else
                throw new IllegalStateException("Cannot set next in edge as the given base " + base + " is not adjacent to the current edge");
        }

        @Override
        public String toString() {
            return nodeA + "-" + nodeB + " (" + prepareEdge + ") " + weightAB + " " + weightBA;
        }
    }

    private static class PrepareShortcut implements PrepareEdge {
        private final int prepareEdge;
        private final int from;
        private final int to;
        private double weight;
        private int skipped1;
        private int skipped2;
        private int origEdgeCount;
        private PrepareEdge nextOut;
        private PrepareEdge nextIn;

        private PrepareShortcut(int prepareEdge, int from, int to, double weight, int skipped1, int skipped2, int origEdgeCount) {
            this.prepareEdge = prepareEdge;
            this.from = from;
            this.to = to;
            assert Double.isFinite(weight);
            this.weight = weight;
            this.skipped1 = skipped1;
            this.skipped2 = skipped2;
            this.origEdgeCount = origEdgeCount;
        }

        @Override
        public boolean isShortcut() {
            return true;
        }

        @Override
        public int getPrepareEdge() {
            return prepareEdge;
        }

        @Override
        public int getNodeA() {
            return from;
        }

        @Override
        public int getNodeB() {
            return to;
        }

        @Override
        public double getWeightAB() {
            return weight;
        }

        @Override
        public double getWeightBA() {
            return weight;
        }

        @Override
        public int getOrigEdgeKeyFirstAB() {
            throw new IllegalStateException("Not supported for node-based shortcuts");
        }

        @Override
        public int getOrigEdgeKeyFirstBA() {
            throw new IllegalStateException("Not supported for node-based shortcuts");
        }

        @Override
        public int getOrigEdgeKeyLastAB() {
            throw new IllegalStateException("Not supported for node-based shortcuts");
        }

        @Override
        public int getOrigEdgeKeyLastBA() {
            throw new IllegalStateException("Not supported for node-based shortcuts");
        }

        @Override
        public int getSkipped1() {
            return skipped1;
        }

        @Override
        public int getSkipped2() {
            return skipped2;
        }

        @Override
        public int getOrigEdgeCount() {
            return origEdgeCount;
        }

        @Override
        public void setSkipped1(int skipped1) {
            this.skipped1 = skipped1;
        }

        @Override
        public void setSkipped2(int skipped2) {
            this.skipped2 = skipped2;
        }

        @Override
        public void setWeight(double weight) {
            this.weight = weight;
        }

        @Override
        public void setOrigEdgeCount(int origEdgeCount) {
            this.origEdgeCount = origEdgeCount;
        }

        @Override
        public PrepareEdge getNextOut(int base) {
            return nextOut;
        }

        @Override
        public void setNextOut(int base, PrepareEdge prepareEdge) {
            this.nextOut = prepareEdge;
        }

        @Override
        public PrepareEdge getNextIn(int base) {
            return nextIn;
        }

        @Override
        public void setNextIn(int base, PrepareEdge prepareEdge) {
            this.nextIn = prepareEdge;
        }

        @Override
        public String toString() {
            return from + "-" + to + " " + weight;
        }
    }

    private static class EdgeBasedPrepareShortcut extends PrepareShortcut {
        // we use this subclass to save some memory for node-based where these are not needed
        private final int origEdgeKeyFirst;
        private final int origEdgeKeyLast;

        public EdgeBasedPrepareShortcut(int prepareEdge, int from, int to, int origEdgeKeyFirst, int origEdgeKeyLast,
                                        double weight, int skipped1, int skipped2, int origEdgeCount) {
            super(prepareEdge, from, to, weight, skipped1, skipped2, origEdgeCount);
            this.origEdgeKeyFirst = origEdgeKeyFirst;
            this.origEdgeKeyLast = origEdgeKeyLast;
        }

        @Override
        public int getOrigEdgeKeyFirstAB() {
            return origEdgeKeyFirst;
        }

        @Override
        public int getOrigEdgeKeyFirstBA() {
            return origEdgeKeyFirst;
        }

        @Override
        public int getOrigEdgeKeyLastAB() {
            return origEdgeKeyLast;
        }

        @Override
        public int getOrigEdgeKeyLastBA() {
            return origEdgeKeyLast;
        }

        @Override
        public String toString() {
            return getNodeA() + "-" + getNodeB() + " (" + origEdgeKeyFirst + ", " + origEdgeKeyLast + ") " + getWeightAB();
        }
    }

    /**
     * This helper graph can be used to quickly obtain the edge-keys of the edges of a node. It is only used for
     * edge-based CH. In principle we could use base graph for this, but it turned out it is faster to use this
     * graph (because it does not need to read all the edge flags to determine the access flags).
     */
    static class OrigGraph {
        // we store a list of 'edges' in the format: adjNode|edgeId|accessFlags, we use two ints for each edge
        private final IntArrayList adjNodes;
        private final IntArrayList keysAndFlags;
        // for each node we store the index at which the edges for this node begin in the above edge list
        private final IntArrayList firstEdgesByNode;

        private OrigGraph(IntArrayList firstEdgesByNode, IntArrayList adjNodes, IntArrayList keysAndFlags) {
            this.firstEdgesByNode = firstEdgesByNode;
            this.adjNodes = adjNodes;
            this.keysAndFlags = keysAndFlags;
        }

        PrepareGraphOrigEdgeExplorer createOutOrigEdgeExplorer() {
            return new OrigEdgeIteratorImpl(this, false);
        }

        PrepareGraphOrigEdgeExplorer createInOrigEdgeExplorer() {
            return new OrigEdgeIteratorImpl(this, true);
        }

        static class Builder {
            private final IntArrayList fromNodes = new IntArrayList();
            private final IntArrayList toNodes = new IntArrayList();
            private final IntArrayList keysAndFlags = new IntArrayList();
            private int maxFrom = -1;
            private int maxTo = -1;

            void addEdge(int from, int to, int edge, boolean fwd, boolean bwd) {
                fromNodes.add(from);
                toNodes.add(to);
                keysAndFlags.add(getKeyWithFlags(GHUtility.createEdgeKey(edge, false), fwd, bwd));
                maxFrom = Math.max(maxFrom, from);
                maxTo = Math.max(maxTo, to);

                fromNodes.add(to);
                toNodes.add(from);
                keysAndFlags.add(getKeyWithFlags(GHUtility.createEdgeKey(edge, true), bwd, fwd));
                maxFrom = Math.max(maxFrom, to);
                maxTo = Math.max(maxTo, from);
            }

            OrigGraph build() {
                int[] sortOrder = IndirectSort.mergesort(0, fromNodes.elementsCount, new IndirectComparator.AscendingIntComparator(fromNodes.buffer));
                sortAndTrim(fromNodes, sortOrder);
                sortAndTrim(toNodes, sortOrder);
                sortAndTrim(keysAndFlags, sortOrder);
                return new OrigGraph(buildFirstEdgesByNode(), toNodes, keysAndFlags);
            }

            private static int getKeyWithFlags(int key, boolean fwd, boolean bwd) {
                // we use only 30 bits for the key and store two access flags along with the same int
                // this allows for a maximum of 536mio edges in base graph which is still enough for planet-wide OSM,
                // but if we exceed this limit we should probably move one of the fwd/bwd bits to the nodes field or
                // store the edge instead of the key as we did before #2567 (only here)
                if (key > Integer.MAX_VALUE >> 1)
                    throw new IllegalArgumentException("Maximum edge key exceeded: " + key + ", max: " + (Integer.MAX_VALUE >> 1));
                key <<= 1;
                if (fwd)
                    key++;
                key <<= 1;
                if (bwd)
                    key++;
                return key;
            }

            private IntArrayList buildFirstEdgesByNode() {
                // it is assumed the edges have been sorted already
                final int numFroms = maxFrom + 1;
                final int numEdges = fromNodes.size();

                IntArrayList firstEdgesByNode = zero(numFroms + 1);
                if (numFroms == 0) {
                    firstEdgesByNode.set(0, numEdges);
                    return firstEdgesByNode;
                }
                int edgeIndex = 0;
                for (int from = 0; from < numFroms; from++) {
                    while (edgeIndex < numEdges && fromNodes.get(edgeIndex) < from) {
                        edgeIndex++;
                    }
                    firstEdgesByNode.set(from, edgeIndex);
                }
                firstEdgesByNode.set(numFroms, numEdges);
                return firstEdgesByNode;
            }

        }
    }

    private static class OrigEdgeIteratorImpl implements PrepareGraphOrigEdgeExplorer, PrepareGraphOrigEdgeIterator {
        private final OrigGraph graph;
        private final boolean reverse;
        private int node;
        private int endEdge;
        private int index;

        public OrigEdgeIteratorImpl(OrigGraph graph, boolean reverse) {
            this.graph = graph;
            this.reverse = reverse;
        }

        @Override
        public PrepareGraphOrigEdgeIterator setBaseNode(int node) {
            this.node = node;
            index = graph.firstEdgesByNode.get(node) - 1;
            endEdge = graph.firstEdgesByNode.get(node + 1);
            return this;
        }

        @Override
        public boolean next() {
            while (true) {
                index++;
                if (index >= endEdge)
                    return false;
                if (hasAccess())
                    return true;
            }
        }

        @Override
        public int getBaseNode() {
            return node;
        }

        @Override
        public int getAdjNode() {
            return graph.adjNodes.get(index);
        }

        @Override
        public int getOrigEdgeKeyFirst() {
            return graph.keysAndFlags.get(index) >>> 2;
        }

        @Override
        public int getOrigEdgeKeyLast() {
            return getOrigEdgeKeyFirst();
        }

        private boolean hasAccess() {
            int e = graph.keysAndFlags.get(index);
            if (reverse)
                return (e & 0b01) == 0b01;
            else
                return (e & 0b10) == 0b10;
        }

        @Override
        public String toString() {
            return getBaseNode() + "-" + getAdjNode() + "(" + getOrigEdgeKeyFirst() + ")";
        }
    }

    private static void sortAndTrim(IntArrayList arr, int[] sortOrder) {
        arr.buffer = applySortOrder(sortOrder, arr.buffer);
        arr.elementsCount = arr.buffer.length;
    }

    private static int[] applySortOrder(int[] sortOrder, int[] arr) {
        if (sortOrder.length > arr.length) {
            throw new IllegalArgumentException("sort order must not be shorter than array");
        }
        int[] result = new int[sortOrder.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = arr[sortOrder[i]];
        }
        return result;
    }
}
