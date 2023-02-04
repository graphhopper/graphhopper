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

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import static com.graphhopper.util.GHUtility.getEdgeFromEdgeKey;

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
    private final EdgeTransitionFilter edgeTransitionFilter;
    private final EdgeExplorer explorer;
    private final BitUtil bitUtil = BitUtil.LITTLE;
    private final IntArrayDeque tarjanStack;
    private final LongArrayDeque dfsStackPQ;
    private final IntArrayDeque dfsStackAdj;
    private final ConnectedComponents components;
    private final boolean excludeSingleEdgeComponents;
    private TarjanIntIntMap edgeKeyIndex;
    private TarjanIntIntMap edgeKeyLowLink;
    private TarjanIntSet edgeKeyOnStack;

    private int currIndex = 0;
    private int p;
    private int q;
    private int adj;
    private State dfsState;

    /**
     * Runs Tarjan's algorithm using an explicit stack.
     *
     * @param edgeTransitionFilter        Only edge transitions accepted by this filter will be considered when we explore the graph.
     *                                    If a turn is not accepted the corresponding path will be ignored (edges that are only connected
     *                                    by a path with such a turn will not be considered to belong to the same component)
     * @param excludeSingleEdgeComponents if set to true components that only contain a single edge will not be
     *                                    returned when calling {@link #findComponents} or {@link #findComponentsRecursive()},
     *                                    which can be useful to save some memory.
     */
    public static ConnectedComponents findComponents(Graph graph, EdgeTransitionFilter edgeTransitionFilter, boolean excludeSingleEdgeComponents) {
        return new EdgeBasedTarjanSCC(graph, edgeTransitionFilter, excludeSingleEdgeComponents).findComponents();
    }

    /**
     * Like {@link #findComponents(Graph, EdgeTransitionFilter, boolean)}, but the search only starts at the
     * given edges. This does not mean the search cannot expand to other edges, but this can be controlled by the
     * edgeTransitionFilter. This method does not return single edge components (the excludeSingleEdgeComponents option is
     * set to true).
     */
    public static ConnectedComponents findComponentsForStartEdges(Graph graph, EdgeTransitionFilter edgeTransitionFilter, IntContainer edges) {
        return new EdgeBasedTarjanSCC(graph, edgeTransitionFilter, true).findComponentsForStartEdges(edges);
    }

    /**
     * Runs Tarjan's algorithm in a recursive way. Doing it like this requires a large stack size for large graphs,
     * which can be set like `-Xss1024M`. Usually the version using an explicit stack ({@link #findComponents()}) should be
     * preferred. However, this recursive implementation is easier to understand.
     *
     * @see #findComponents(Graph, EdgeTransitionFilter, boolean)
     */
    public static ConnectedComponents findComponentsRecursive(Graph graph, EdgeTransitionFilter edgeTransitionFilter, boolean excludeSingleEdgeComponents) {
        return new EdgeBasedTarjanSCC(graph, edgeTransitionFilter, excludeSingleEdgeComponents).findComponentsRecursive();
    }

    private EdgeBasedTarjanSCC(Graph graph, EdgeTransitionFilter edgeTransitionFilter, boolean excludeSingleEdgeComponents) {
        this.graph = graph;
        this.edgeTransitionFilter = edgeTransitionFilter;
        this.explorer = graph.createEdgeExplorer();
        tarjanStack = new IntArrayDeque();
        dfsStackPQ = new LongArrayDeque();
        dfsStackAdj = new IntArrayDeque();
        components = new ConnectedComponents(excludeSingleEdgeComponents ? -1 : 2 * graph.getEdges());
        this.excludeSingleEdgeComponents = excludeSingleEdgeComponents;
    }

    private void initForEntireGraph() {
        final int edges = graph.getEdges();
        edgeKeyIndex = new TarjanArrayIntIntMap(2 * edges);
        edgeKeyLowLink = new TarjanArrayIntIntMap(2 * edges);
        edgeKeyOnStack = new TarjanArrayIntSet(2 * edges);
    }

    private void initForStartEdges(int edges) {
        edgeKeyIndex = new TarjanHashIntIntMap(2 * edges);
        edgeKeyLowLink = new TarjanHashIntIntMap(2 * edges);
        edgeKeyOnStack = new TarjanHashIntSet(2 * edges);
    }

    private enum State {
        UPDATE,
        HANDLE_NEIGHBOR,
        FIND_COMPONENT,
        BUILD_COMPONENT
    }

    private ConnectedComponents findComponentsRecursive() {
        initForEntireGraph();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            int edgeKeyFwd = createEdgeKey(iter, false);
            if (!edgeKeyIndex.has(edgeKeyFwd))
                findComponentForEdgeKey(edgeKeyFwd, iter.getAdjNode());
            int edgeKeyBwd = createEdgeKey(iter, true);
            if (!edgeKeyIndex.has(edgeKeyBwd))
                findComponentForEdgeKey(edgeKeyBwd, iter.getAdjNode());
        }
        return components;
    }

    private void findComponentForEdgeKey(int p, int adjNode) {
        setupNextEdgeKey(p);
        // we have to create a new explorer on each iteration because of the nested edge iterations
        final int edge = getEdgeFromEdgeKey(p);
        EdgeExplorer explorer = graph.createEdgeExplorer();
        EdgeIterator iter = explorer.setBaseNode(adjNode);
        while (iter.next()) {
            if (!edgeTransitionFilter.accept(edge, iter))
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
        edgeKeyIndex.set(p, currIndex);
        edgeKeyLowLink.set(p, currIndex);
        currIndex++;
        tarjanStack.addLast(p);
        edgeKeyOnStack.add(p);
    }

    private void handleNeighbor(int p, int q, int adj) {
        if (!edgeKeyIndex.has(q)) {
            findComponentForEdgeKey(q, adj);
            edgeKeyLowLink.minTo(p, edgeKeyLowLink.get(q));
        } else if (edgeKeyOnStack.contains(q))
            edgeKeyLowLink.minTo(p, edgeKeyIndex.get(q));
    }

    private void buildComponent(int p) {
        if (edgeKeyLowLink.get(p) == edgeKeyIndex.get(p)) {
            if (tarjanStack.getLast() == p) {
                tarjanStack.removeLast();
                edgeKeyOnStack.remove(p);
                components.numComponents++;
                components.numEdgeKeys++;
                if (!excludeSingleEdgeComponents)
                    components.singleEdgeComponents.set(p);
            } else {
                IntArrayList component = new IntArrayList();
                while (true) {
                    int q = tarjanStack.removeLast();
                    component.add(q);
                    edgeKeyOnStack.remove(q);
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

    private ConnectedComponents findComponents() {
        initForEntireGraph();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            findComponentsForEdgeState(iter);
        }
        return components;
    }

    private ConnectedComponents findComponentsForStartEdges(IntContainer startEdges) {
        initForStartEdges(startEdges.size());
        for (IntCursor edge : startEdges) {
            // todo: using getEdgeIteratorState here is not efficient
            EdgeIteratorState edgeState = graph.getEdgeIteratorState(edge.value, Integer.MIN_VALUE);
            if (!edgeTransitionFilter.accept(NO_EDGE, edgeState))
                continue;
            findComponentsForEdgeState(edgeState);
        }
        return components;
    }

    private void findComponentsForEdgeState(EdgeIteratorState edge) {
        int edgeKeyFwd = createEdgeKey(edge, false);
        if (!edgeKeyIndex.has(edgeKeyFwd))
            pushFindComponentForEdgeKey(edgeKeyFwd, edge.getAdjNode());
        startSearch();
        // We need to start the search for both edge keys of this edge, but its important to check if the second
        // has already been found by the first search. So we cannot simply push them both and start the search once.
        int edgeKeyBwd = createEdgeKey(edge, true);
        if (!edgeKeyIndex.has(edgeKeyBwd))
            pushFindComponentForEdgeKey(edgeKeyBwd, edge.getAdjNode());
        startSearch();
    }

    private void startSearch() {
        while (hasNext()) {
            pop();
            switch (dfsState) {
                case BUILD_COMPONENT:
                    buildComponent(p);
                    break;
                case UPDATE:
                    edgeKeyLowLink.minTo(p, edgeKeyLowLink.get(q));
                    break;
                case HANDLE_NEIGHBOR:
                    if (edgeKeyIndex.has(q) && edgeKeyOnStack.contains(q))
                        edgeKeyLowLink.minTo(p, edgeKeyIndex.get(q));
                    if (!edgeKeyIndex.has(q)) {
                        // we are pushing updateLowLinks first so it will run *after* findComponent finishes
                        pushUpdateLowLinks(p, q);
                        pushFindComponentForEdgeKey(q, adj);
                    }
                    break;
                case FIND_COMPONENT:
                    setupNextEdgeKey(p);
                    // we push buildComponent first so it will run *after* we finished traversing the edges
                    pushBuildComponent(p);
                    final int edge = getEdgeFromEdgeKey(p);
                    EdgeIterator it = explorer.setBaseNode(adj);
                    while (it.next()) {
                        if (!edgeTransitionFilter.accept(edge, it))
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

    public static int createEdgeKey(EdgeIteratorState edgeState, boolean reverse) {
        return TraversalMode.EDGE_BASED.createTraversalId(edgeState, reverse);
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
         * the opposite direction). Use {@link GHUtility#getEdgeFromEdgeKey(int)} to convert edge keys back to
         * edge IDs.
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

    private interface TarjanIntIntMap {
        void set(int key, int value);

        void minTo(int key, int min);

        boolean has(int key);

        int get(int key);
    }

    private static class TarjanArrayIntIntMap implements TarjanIntIntMap {
        private final int[] arr;

        TarjanArrayIntIntMap(int elements) {
            arr = new int[elements];
            Arrays.fill(arr, -1);
        }

        @Override
        public void set(int key, int value) {
            arr[key] = value;
        }

        @Override
        public void minTo(int key, int value) {
            arr[key] = Math.min(arr[key], value);
        }

        @Override
        public boolean has(int key) {
            return arr[key] != -1;
        }

        @Override
        public int get(int key) {
            return arr[key];
        }
    }

    private static class TarjanHashIntIntMap implements TarjanIntIntMap {
        private final IntIntScatterMap map;

        TarjanHashIntIntMap(int keys) {
            this.map = new IntIntScatterMap(keys);
        }

        @Override
        public void set(int key, int value) {
            map.put(key, value);
        }

        @Override
        public void minTo(int key, int value) {
            // todo: optimize with map.indexOf(key) etc
            map.put(key, Math.min(map.getOrDefault(key, -1), value));
        }

        @Override
        public boolean has(int key) {
            return map.containsKey(key);
        }

        @Override
        public int get(int key) {
            return map.getOrDefault(key, -1);
        }
    }

    private interface TarjanIntSet {
        void add(int key);

        boolean contains(int key);

        void remove(int key);
    }

    private static class TarjanArrayIntSet implements TarjanIntSet {
        private final BitSet set;

        TarjanArrayIntSet(int keys) {
            set = new BitSet(keys);
            if (!set.getClass().getName().contains("hppc"))
                throw new IllegalStateException("Was meant to be hppc BitSet");
        }

        @Override
        public void add(int key) {
            set.set(key);
        }

        @Override
        public boolean contains(int key) {
            return set.get(key);
        }

        @Override
        public void remove(int key) {
            set.clear(key);
        }
    }

    private static class TarjanHashIntSet implements TarjanIntSet {
        private final IntScatterSet set;

        TarjanHashIntSet(int keys) {
            set = new IntScatterSet(keys);
        }

        @Override
        public void add(int key) {
            set.add(key);
        }

        @Override
        public boolean contains(int key) {
            return set.contains(key);
        }

        @Override
        public void remove(int key) {
            set.remove(key);
        }
    }

    public interface EdgeTransitionFilter {
        /**
         * @return true if edgeState is allowed *and* turning from prevEdge onto edgeState is allowed, false otherwise
         */
        boolean accept(int prevEdge, EdgeIteratorState edgeState);
    }

}

