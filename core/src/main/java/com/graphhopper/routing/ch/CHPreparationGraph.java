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
import com.carrotsearch.hppc.sorting.IndirectComparator;
import com.carrotsearch.hppc.sorting.IndirectSort;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

import static com.graphhopper.util.ArrayUtil.zero;

/**
 * Graph data structure used for CH preparation. It allows caching weights, and edges that are not needed anymore
 * (those adjacent to contracted nodes) can be removed (see {@link #disconnect}.
 */
public class CHPreparationGraph {
    private final int nodes;
    private final int edges;
    private final boolean edgeBased;
    private final TurnCostFunction turnCostFunction;
    // each edge/shortcut between nodes a/b is represented as a single object and we maintain a list of references
    // to these objects at every node. this needs to be memory-efficient especially for node-based (because there
    // are less shortcuts overall so the size of the prepare graph is crucial, while for edge-based most memory is
    // consumed towards the end of the preparation anyway). for edge-based it would actually be better/faster to keep
    // separate lists of incoming/outgoing edges and even use uni-directional edge-objects.
    private SplitArray2D<PrepareEdge> prepareEdges;
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
        prepareEdges = new SplitArray2D<>(nodes, 2);
        origGraphBuilder = edgeBased ? new OrigGraph.Builder() : null;
        neighborSet = new IntHashSet();
        nextShortcutId = edges;
    }

    public static void buildFromGraph(CHPreparationGraph prepareGraph, Graph graph, Weighting weighting) {
        if (graph.getNodes() != prepareGraph.getNodes())
            throw new IllegalArgumentException("Cannot initialize from given graph. The number of nodes does not match: " +
                    graph.getNodes() + " vs. " + prepareGraph.getNodes());
        if (graph.getEdges() != prepareGraph.getOriginalEdges())
            throw new IllegalArgumentException("Cannot initialize from given graph. The number of edges does not match: " +
                    graph.getEdges() + " vs. " + prepareGraph.getOriginalEdges());
        BooleanEncodedValue accessEnc = weighting.getFlagEncoder().getAccessEnc();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            double weightFwd = iter.get(accessEnc) ? weighting.calcEdgeWeight(iter, false) : Double.POSITIVE_INFINITY;
            double weightBwd = iter.getReverse(accessEnc) ? weighting.calcEdgeWeight(iter, true) : Double.POSITIVE_INFINITY;
            prepareGraph.addEdge(iter.getBaseNode(), iter.getAdjNode(), iter.getEdge(), weightFwd, weightBwd);
        }
        prepareGraph.prepareForContraction();
    }

    /**
     * Builds a turn cost function for a given graph('s turn cost storage) and a weighting.
     * The trivial implementation would be simply returning {@link Weighting#calcTurnWeight}. However, it turned out
     * that reading all turn costs for the current encoder and then storing them in separate arrays upfront speeds up
     * edge-based CH preparation by about 25%. See #2084
     */
    public static TurnCostFunction buildTurnCostFunctionFromTurnCostStorage(Graph graph, Weighting weighting) {
        FlagEncoder encoder = weighting.getFlagEncoder();
        String key = TurnCost.key(encoder.toString());
        if (!encoder.hasEncodedValue(key))
            return (inEdge, viaNode, outEdge) -> 0;

        DecimalEncodedValue turnCostEnc = encoder.getDecimalEncodedValue(key);
        TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
        // we maintain a list of inEdge/outEdge/turn-cost triples (we use two arrays for this) that is sorted by nodes
        LongArrayList turnCostEdgePairs = new LongArrayList();
        DoubleArrayList turnCosts = new DoubleArrayList();
        // for each node we store the index of the first turn cost entry/triple in the list
        final int[] turnCostNodes = new int[graph.getNodes() + 1];
        TurnCostStorage.TurnRelationIterator tcIter = turnCostStorage.getAllTurnRelations();
        int lastNode = -1;
        while (tcIter.next()) {
            int viaNode = tcIter.getViaNode();
            if (viaNode < lastNode)
                throw new IllegalStateException();
            long edgePair = BitUtil.LITTLE.combineIntsToLong(tcIter.getFromEdge(), tcIter.getToEdge());
            // note that as long as we only use OSM turn restrictions all the turn costs are infinite anyway
            double turnCost = tcIter.getCost(turnCostEnc);
            int index = turnCostEdgePairs.size();
            turnCostEdgePairs.add(edgePair);
            turnCosts.add(turnCost);
            if (viaNode != lastNode) {
                for (int i = lastNode + 1; i <= viaNode; i++) {
                    turnCostNodes[i] = index;
                }
            }
            lastNode = viaNode;
        }
        for (int i = lastNode + 1; i <= turnCostNodes.length - 1; i++) {
            turnCostNodes[i] = turnCostEdgePairs.size();
        }
        turnCostNodes[turnCostNodes.length - 1] = turnCostEdgePairs.size();

        // currently the u-turn costs are the same for all junctions, so for now we just get them for one of them
        double uTurnCosts = weighting.calcTurnWeight(1, 0, 1);
        return (inEdge, viaNode, outEdge) -> {
            if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge))
                return 0;
            else if (inEdge == outEdge)
                return uTurnCosts;
            // traverse all turn cost entries we have for this viaNode and return the turn costs if we find a match
            for (int i = turnCostNodes[viaNode]; i < turnCostNodes[viaNode + 1]; i++) {
                long l = turnCostEdgePairs.get(i);
                if (inEdge == BitUtil.LITTLE.getIntLow(l) && outEdge == BitUtil.LITTLE.getIntHigh(l))
                    return turnCosts.get(i);
            }
            return 0;
        };
    }

    public int getNodes() {
        return nodes;
    }

    public int getOriginalEdges() {
        return edges;
    }

    public int getDegree(int node) {
        return prepareEdges.size(node);
    }

    public void addEdge(int from, int to, int edge, double weightFwd, double weightBwd) {
        checkNotReady();
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
        return nextShortcutId++;
    }

    public void prepareForContraction() {
        checkNotReady();
        prepareEdges.trimToSize();
        origGraph = edgeBased ? origGraphBuilder.build() : null;
        origGraphBuilder = null;
        ready = true;
    }

    public PrepareGraphEdgeExplorer createOutEdgeExplorer() {
        checkReady();
        return new PrepareGraphEdgeExplorerImpl(prepareEdges, false);
    }

    public PrepareGraphEdgeExplorer createInEdgeExplorer() {
        checkReady();
        return new PrepareGraphEdgeExplorerImpl(prepareEdges, true);
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

    public double getTurnWeight(int inEdge, int viaNode, int outEdge) {
        return turnCostFunction.getTurnWeight(inEdge, viaNode, outEdge);
    }

    public IntContainer disconnect(int node) {
        checkReady();
        // we use this neighbor set to guarantee a deterministic order of the returned
        // node ids
        neighborSet.clear();
        IntArrayList neighbors = new IntArrayList(getDegree(node));
        for (int i = 0; i < prepareEdges.size(node); i++) {
            PrepareEdge prepareEdge = prepareEdges.get(node, i);
            int adjNode = prepareEdge.getNodeB();
            if (adjNode == node)
                adjNode = prepareEdge.getNodeA();
            if (adjNode == node)
                // this is a loop
                continue;
            prepareEdges.remove(adjNode, prepareEdge);
            if (neighborSet.add(adjNode))
                neighbors.add(adjNode);
        }
        prepareEdges.clear(node);
        return neighbors;
    }

    public void close() {
        checkReady();
        prepareEdges = null;
        neighborSet = null;
        if (edgeBased)
            origGraph = null;
    }

    private void addOutEdge(int node, PrepareEdge prepareEdge) {
        prepareEdges.addPartTwo(node, prepareEdge);
    }

    private void addInEdge(int node, PrepareEdge prepareEdge) {
        prepareEdges.addPartOne(node, prepareEdge);
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
        private final SplitArray2D<PrepareEdge> prepareEdges;
        private final boolean reverse;
        private int node = -1;
        private int end;
        private PrepareEdge currEdge;
        private int index;

        PrepareGraphEdgeExplorerImpl(SplitArray2D<PrepareEdge> prepareEdges, boolean reverse) {
            this.prepareEdges = prepareEdges;
            this.reverse = reverse;
        }

        @Override
        public PrepareGraphEdgeIterator setBaseNode(int node) {
            this.node = node;
            // we store the in edges in the first and the out edges in the second part of the prepareEdges
            this.index = reverse ? -1 : (prepareEdges.mid(node) - 1);
            this.end = reverse ? prepareEdges.mid(node) : prepareEdges.size(node);
            return this;
        }

        @Override
        public boolean next() {
            index++;
            if (index == end) {
                currEdge = null;
                return false;
            }
            currEdge = prepareEdges.get(node, index);
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
            return currEdge == null ? "not_started" : getBaseNode() + "-" + getAdjNode();
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
    }

    public static class PrepareBaseEdge implements PrepareEdge {
        private final int prepareEdge;
        private final int nodeA;
        private final int nodeB;
        private final float weightAB;
        private final float weightBA;

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
            int key = prepareEdge << 1;
            return nodeA > nodeB ? key + 1 : key;
        }

        @Override
        public int getOrigEdgeKeyFirstBA() {
            int key = prepareEdge << 1;
            return nodeB > nodeA ? key + 1 : key;
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
    private static class OrigGraph {
        // we store a list of 'edges' in the format: adjNode|edgeId|accessFlags, we use two ints for each edge
        private final IntArrayList adjNodes;
        private final IntArrayList edgesAndFlags;
        // for each node we store the index at which the edges for this node begin in the above edge list
        private final IntArrayList firstEdgesByNode;

        private OrigGraph(IntArrayList firstEdgesByNode, IntArrayList adjNodes, IntArrayList edgesAndFlags) {
            this.firstEdgesByNode = firstEdgesByNode;
            this.adjNodes = adjNodes;
            this.edgesAndFlags = edgesAndFlags;
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
            private final IntArrayList edgesAndFlags = new IntArrayList();
            private int maxFrom = -1;
            private int maxTo = -1;

            void addEdge(int from, int to, int edge, boolean fwd, boolean bwd) {
                fromNodes.add(from);
                toNodes.add(to);
                edgesAndFlags.add(getEdgeWithFlags(edge, fwd, bwd));
                maxFrom = Math.max(maxFrom, from);
                maxTo = Math.max(maxTo, to);

                fromNodes.add(to);
                toNodes.add(from);
                edgesAndFlags.add(getEdgeWithFlags(edge, bwd, fwd));
                maxFrom = Math.max(maxFrom, to);
                maxTo = Math.max(maxTo, from);
            }

            OrigGraph build() {
                int[] sortOrder = IndirectSort.mergesort(0, fromNodes.elementsCount, new IndirectComparator.AscendingIntComparator(fromNodes.buffer));
                sortAndTrim(fromNodes, sortOrder);
                sortAndTrim(toNodes, sortOrder);
                sortAndTrim(edgesAndFlags, sortOrder);
                return new OrigGraph(buildFirstEdgesByNode(), toNodes, edgesAndFlags);
            }

            private int getEdgeWithFlags(int edge, boolean fwd, boolean bwd) {
                // we use only 30 bits for the edge Id and store two access flags along with the same int
                if (edge >= Integer.MAX_VALUE >> 2)
                    throw new IllegalArgumentException("Maximum edge ID exceeded: " + Integer.MAX_VALUE);
                edge <<= 1;
                if (fwd)
                    edge++;
                edge <<= 1;
                if (bwd)
                    edge++;
                return edge;
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
            int e = graph.edgesAndFlags.get(index);
            return GHUtility.createEdgeKey(node, getAdjNode(), e >> 2, false);
        }

        @Override
        public int getOrigEdgeKeyLast() {
            return getOrigEdgeKeyFirst();
        }

        private boolean hasAccess() {
            int e = graph.edgesAndFlags.get(index);
            if (reverse) {
                return (e & 0b01) == 0b01;
            } else {
                return (e & 0b10) == 0b10;
            }
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
