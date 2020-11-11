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

package com.graphhopper.routing.subnetwork;

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.IntArrayDeque;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.LongArrayDeque;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Edge-based version of Tarjan's algorithm to find strongly connected components on a directed graph. Compared
 * to the more traditional node-based version that traverses the nodes of the graph this version works directly with
 * the edges. This way its possible to take into account possible turn restrictions.
 * <p>
 * The algorithm is of course very similar to the node-based version and it might be possible to reuse some code between
 * the two, but especially the version with an explicit stack needs different 'state' information and loops require
 * some special treatment as well.
 *
 * @author easbar
 * @see TarjanSCC
 */
public class EdgeBasedTarjanSCC {
    private final Graph graph;
    private final BooleanEncodedValue accessEnc;
    private final EdgeExplorer explorer;
    private final BitUtil bitUtil = BitUtil.LITTLE;
    private final TurnCostProvider turnCostProvider;
    private final int[] edgeKeyIndex;
    private final int[] edgeKeyLowLink;
    private final BitSet edgeKeyOnStack;
    private final IntArrayDeque tarjanStack;
    private final LongArrayDeque dfsStackPQ;
    private final IntArrayDeque dfsStackAdj;
    private final ConnectedComponents components;
    private final boolean excludeSingleEdgeComponents;

    private int currIndex = 0;
    private int p;
    private int q;
    private int adj;
    private State dfsState;

    /**
     * @param excludeSingleEdgeComponents if set to false components that only contain a single edge will not be
     *                                    returned when calling {@link #findComponents} or {@link #findComponentsRecursive()},
     *                                    which can be useful to save some memory.
     * @param turnCostProvider            used to check the turn costs between edges. if a turn has infinite costs the corresponding
     *                                    path will be ignored (edges that are only connected by a path with such a turn will not
     *                                    be considered to belong to the same component)
     */
    public EdgeBasedTarjanSCC(Graph graph, BooleanEncodedValue accessEnc, TurnCostProvider turnCostProvider, boolean excludeSingleEdgeComponents) {
        this.graph = graph;
        this.accessEnc = accessEnc;
        explorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(accessEnc));
        this.turnCostProvider = turnCostProvider;

        edgeKeyIndex = new int[2 * graph.getEdges()];
        edgeKeyLowLink = new int[2 * graph.getEdges()];
        Arrays.fill(edgeKeyIndex, -1);
        Arrays.fill(edgeKeyLowLink, -1);
        edgeKeyOnStack = new BitSet(2 * graph.getEdges());
        if (!edgeKeyOnStack.getClass().getName().contains("hppc"))
            throw new IllegalStateException("Was meant to be hppc BitSet");
        tarjanStack = new IntArrayDeque();
        dfsStackPQ = new LongArrayDeque();
        dfsStackAdj = new IntArrayDeque();
        components = new ConnectedComponents(excludeSingleEdgeComponents ? -1 : 2 * graph.getEdges());
        this.excludeSingleEdgeComponents = excludeSingleEdgeComponents;
    }

    private enum State {
        UPDATE,
        HANDLE_NEIGHBOR,
        FIND_COMPONENT,
        BUILD_COMPONENT
    }

    /**
     * Runs Tarjan's algorithm in a recursive way. Doing it like this requires a large stack size for large graphs,
     * which can be set like `-Xss1024M`. Usually the version using an explicit stack ({@link #findComponents()}) should be
     * preferred. However, this recursive implementation is easier to understand.
     */
    public ConnectedComponents findComponentsRecursive() {
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            int edgeKeyFwd = createEdgeKey(iter, false);
            if (edgeKeyIndex[edgeKeyFwd] == -1)
                findComponentForEdgeKey(edgeKeyFwd, iter.getAdjNode());
            int edgeKeyBwd = createEdgeKey(iter, true);
            if (edgeKeyIndex[edgeKeyBwd] == -1)
                findComponentForEdgeKey(edgeKeyBwd, iter.getAdjNode());
        }
        return components;
    }

    private void findComponentForEdgeKey(int p, int adjNode) {
        setupNextEdgeKey(p);
        // we have to create a new explorer on each iteration because of the nested edge iterations
        final int edge = getEdgeFromKey(p);
        EdgeExplorer explorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(accessEnc));
        EdgeIterator iter = explorer.setBaseNode(adjNode);
        while (iter.next()) {
            if (isTurnRestricted(edge, adjNode, iter.getEdge()))
                continue;
            int q = createEdgeKey(iter, false);
            handleNeighbor(p, q, iter.getAdjNode());
            // we need a special treatment for loops because our edge iterator only sees it once but it can be travelled
            // both ways
            if (iter.getBaseNode() == iter.getAdjNode())
                handleNeighbor(p, q + 1, iter.getAdjNode());
        }
        buildComponent(p);
    }

    private void setupNextEdgeKey(int p) {
        edgeKeyIndex[p] = currIndex;
        edgeKeyLowLink[p] = currIndex;
        currIndex++;
        tarjanStack.addLast(p);
        edgeKeyOnStack.set(p);
    }

    private void handleNeighbor(int p, int q, int adj) {
        if (edgeKeyIndex[q] == -1) {
            findComponentForEdgeKey(q, adj);
            edgeKeyLowLink[p] = Math.min(edgeKeyLowLink[p], edgeKeyLowLink[q]);
        } else if (edgeKeyOnStack.get(q))
            edgeKeyLowLink[p] = Math.min(edgeKeyLowLink[p], edgeKeyIndex[q]);
    }

    private void buildComponent(int p) {
        if (edgeKeyLowLink[p] == edgeKeyIndex[p]) {
            if (tarjanStack.getLast() == p) {
                tarjanStack.removeLast();
                edgeKeyOnStack.clear(p);
                components.numComponents++;
                components.numEdgeKeys++;
                if (!excludeSingleEdgeComponents)
                    components.singleEdgeComponents.set(p);
            } else {
                IntArrayList component = new IntArrayList();
                while (true) {
                    int q = tarjanStack.removeLast();
                    component.add(q);
                    edgeKeyOnStack.clear(q);
                    if (q == p)
                        break;
                }
                component.trimToSize();
                assert component.size() > 1;
                components.numComponents++;
                components.numEdgeKeys += component.size();
                components.components.add(component);
                if (component.size() > components.biggestComponent.size())
                    components.biggestComponent = component;
            }
        }
    }

    /**
     * Runs Tarjan's algorithm using an explicit stack.
     */
    public ConnectedComponents findComponents() {
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            int edgeKeyFwd = createEdgeKey(iter, false);
            if (edgeKeyIndex[edgeKeyFwd] == -1)
                pushFindComponentForEdgeKey(edgeKeyFwd, iter.getAdjNode());
            startSearch();
            // We need to start the search for both edge keys of this edge, but its important to check if the second
            // has already been found by the first search. So we cannot simply push them both and start the search once.
            int edgeKeyBwd = createEdgeKey(iter, true);
            if (edgeKeyIndex[edgeKeyBwd] == -1)
                pushFindComponentForEdgeKey(edgeKeyBwd, iter.getAdjNode());
            startSearch();
        }
        return components;
    }

    private void startSearch() {
        while (hasNext()) {
            pop();
            switch (dfsState) {
                case BUILD_COMPONENT:
                    buildComponent(p);
                    break;
                case UPDATE:
                    edgeKeyLowLink[p] = Math.min(edgeKeyLowLink[p], edgeKeyLowLink[q]);
                    break;
                case HANDLE_NEIGHBOR:
                    if (edgeKeyIndex[q] != -1 && edgeKeyOnStack.get(q))
                        edgeKeyLowLink[p] = Math.min(edgeKeyLowLink[p], edgeKeyIndex[q]);
                    if (edgeKeyIndex[q] == -1) {
                        // we are pushing updateLowLinks first so it will run *after* findComponent finishes
                        pushUpdateLowLinks(p, q);
                        pushFindComponentForEdgeKey(q, adj);
                    }
                    break;
                case FIND_COMPONENT:
                    setupNextEdgeKey(p);
                    // we push buildComponent first so it will run *after* we finished traversing the edges
                    pushBuildComponent(p);
                    final int edge = getEdgeFromKey(p);
                    EdgeIterator it = explorer.setBaseNode(adj);
                    while (it.next()) {
                        if (isTurnRestricted(edge, adj, it.getEdge()))
                            continue;
                        int q = createEdgeKey(it, false);
                        pushHandleNeighbor(p, q, it.getAdjNode());
                        if (it.getBaseNode() == it.getAdjNode())
                            pushHandleNeighbor(p, q + 1, it.getAdjNode());
                    }
                    break;
                default:
                    throw new IllegalStateException("Unknown state: " + dfsState);
            }
        }
    }

    private boolean hasNext() {
        return !dfsStackPQ.isEmpty();
    }

    private void pop() {
        long l = dfsStackPQ.removeLast();
        int a = dfsStackAdj.removeLast();
        // We are maintaining two stacks to hold four kinds of information: two edge keys (p&q), the adj node and the
        // kind of code ('state') we want to execute for a given stack item. The following code combined with the pushXYZ
        // methods does the fwd/bwd conversion between this information and the values on our stack(s).
        int low = bitUtil.getIntLow(l);
        int high = bitUtil.getIntHigh(l);
        if (a == -1) {
            dfsState = State.UPDATE;
            p = low;
            q = high;
            adj = -1;
        } else if (a == -2 && high == -2) {
            dfsState = State.BUILD_COMPONENT;
            p = low;
            q = -1;
            adj = -1;
        } else if (high == -1) {
            dfsState = State.FIND_COMPONENT;
            p = low;
            q = -1;
            adj = a;
        } else {
            assert low >= 0 && high >= 0 && a >= 0;
            dfsState = State.HANDLE_NEIGHBOR;
            p = low;
            q = high;
            adj = a;
        }
    }

    private void pushUpdateLowLinks(int p, int q) {
        assert p >= 0 && q >= 0;
        dfsStackPQ.addLast(bitUtil.combineIntsToLong(p, q));
        dfsStackAdj.addLast(-1);
    }

    private void pushBuildComponent(int p) {
        assert p >= 0;
        dfsStackPQ.addLast(bitUtil.combineIntsToLong(p, -2));
        dfsStackAdj.addLast(-2);
    }

    private void pushFindComponentForEdgeKey(int p, int adj) {
        assert p >= 0 && adj >= 0;
        dfsStackPQ.addLast(bitUtil.combineIntsToLong(p, -1));
        dfsStackAdj.addLast(adj);
    }

    private void pushHandleNeighbor(int p, int q, int adj) {
        assert p >= 0 && q >= 0 && adj >= 0;
        dfsStackPQ.addLast(bitUtil.combineIntsToLong(p, q));
        dfsStackAdj.addLast(adj);
    }

    private boolean isTurnRestricted(int inEdge, int node, int outEdge) {
        return turnCostProvider.calcTurnWeight(inEdge, node, outEdge) == Double.POSITIVE_INFINITY;
    }

    public static int createEdgeKey(EdgeIteratorState edgeState, boolean reverse) {
        int edgeKey = edgeState.getEdge() << 1;
        if (edgeState.get(EdgeIteratorState.REVERSE_STATE) == !reverse)
            edgeKey++;
        return edgeKey;
    }

    public static int getEdgeFromKey(int edgeKey) {
        return edgeKey / 2;
    }

    public static class ConnectedComponents {
        private final List<IntArrayList> components;
        private final BitSet singleEdgeComponents;
        private IntArrayList biggestComponent;
        private int numComponents;
        private int numEdgeKeys;

        ConnectedComponents(int edgeKeys) {
            components = new ArrayList<>();
            singleEdgeComponents = new BitSet(Math.max(edgeKeys, 0));
            if (!(singleEdgeComponents.getClass().getName().contains("hppc")))
                throw new IllegalStateException("Was meant to be hppc BitSet");
            biggestComponent = new IntArrayList();
        }

        /**
         * A list of arrays each containing the edge keys of a strongly connected component. Components with only a single
         * edge key are not included here, but need to be obtained using {@link #getSingleEdgeComponents()}.
         * The edge key is either 2*edgeId (if the edge direction corresponds to the storage order) or 2*edgeId+1 (for
         * the opposite direction).
         */
        public List<IntArrayList> getComponents() {
            return components;
        }

        /**
         * The set of edge-keys that form their own (single-edge key) component. If {@link EdgeBasedTarjanSCC#excludeSingleEdgeComponents}
         * is enabled this set will be empty.
         */
        public BitSet getSingleEdgeComponents() {
            return singleEdgeComponents;
        }

        /**
         * The total number of strongly connected components. This always includes single-edge components.
         */
        public int getTotalComponents() {
            return numComponents;
        }

        /**
         * A reference to the biggest component contained in {@link #getComponents()} or an empty list if there are
         * either no components or the biggest component has only a single edge (and hence {@link #getComponents()} is
         * empty).
         */
        public IntArrayList getBiggestComponent() {
            return biggestComponent;
        }

        public int getEdgeKeys() {
            return numEdgeKeys;
        }

    }
}

