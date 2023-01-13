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
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.core.util.BitUtil;
import com.graphhopper.core.util.EdgeExplorer;
import com.graphhopper.core.util.EdgeIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tarjan's algorithm to find strongly connected components of a directed graph. Two nodes belong to the same connected
 * component iff they are reachable from each other. Reachability from A to B is not necessarily equal to reachability
 * from B to A because the graph is directed.
 * <p>
 * This class offers two ways to run the algorithm: Either using (function call) recursion {@link #findComponentsRecursive()}
 * or recursion using an explicit stack {@link #findComponents()}. The first one is easier to implement and understand
 * and the second one allows running the algorithm  also on large graphs without having to deal with JVM stack size
 * limits.
 * <p>
 * Tarjan's algorithm is explained for example here:
 * - http://en.wikipedia.org/wiki/Tarjan's_strongly_connected_components_algorithm
 * - http://www.timl.id.au/?p=327
 * - http://homepages.ecs.vuw.ac.nz/~djp/files/P05.pdf
 *
 * @author easbar
 */
public class TarjanSCC {
    private final Graph graph;
    private final EdgeExplorer explorer;
    private final EdgeFilter edgeFilter;
    private final BitUtil bitUtil = BitUtil.LITTLE;
    private final int[] nodeIndex;
    private final int[] nodeLowLink;
    private final BitSet nodeOnStack;
    private final IntArrayDeque tarjanStack;
    private final LongArrayDeque dfsStack;
    private final ConnectedComponents components;
    private final boolean excludeSingleNodeComponents;

    private int currIndex = 0;
    private int v;
    private int w;
    private State dfsState;

    /**
     * Runs Tarjan's algorithm using an explicit stack.
     *
     * @param excludeSingleNodeComponents if set to true components that only contain a single node will not be
     *                                    returned when calling {@link #findComponents} or {@link #findComponentsRecursive()},
     *                                    which can be useful to save some memory.
     */
    public static ConnectedComponents findComponents(Graph graph, EdgeFilter edgeFilter, boolean excludeSingleNodeComponents) {
        return new TarjanSCC(graph, edgeFilter, excludeSingleNodeComponents).findComponents();
    }

    /**
     * Runs Tarjan's algorithm in a recursive way. Doing it like this requires a large stack size for large graphs,
     * which can be set like `-Xss1024M`. Usually the version using an explicit stack ({@link #findComponents()}) should be
     * preferred. However, this recursive implementation is easier to understand.
     *
     * @see #findComponents(Graph, EdgeFilter, boolean)
     */
    public static ConnectedComponents findComponentsRecursive(Graph graph, EdgeFilter edgeFilter, boolean excludeSingleNodeComponents) {
        return new TarjanSCC(graph, edgeFilter, excludeSingleNodeComponents).findComponentsRecursive();
    }

    private TarjanSCC(Graph graph, EdgeFilter edgeFilter, boolean excludeSingleNodeComponents) {
        this.graph = graph;
        this.edgeFilter = edgeFilter;
        explorer = graph.createEdgeExplorer(edgeFilter);

        nodeIndex = new int[graph.getNodes()];
        nodeLowLink = new int[graph.getNodes()];
        Arrays.fill(nodeIndex, -1);
        Arrays.fill(nodeLowLink, -1);
        nodeOnStack = new BitSet(graph.getNodes());
        if (!nodeOnStack.getClass().getName().contains("hppc"))
            throw new IllegalStateException("Was meant to be hppc BitSet");
        tarjanStack = new IntArrayDeque();
        dfsStack = new LongArrayDeque();
        components = new ConnectedComponents(excludeSingleNodeComponents ? -1 : graph.getNodes());
        this.excludeSingleNodeComponents = excludeSingleNodeComponents;
    }

    private enum State {
        UPDATE,
        HANDLE_NEIGHBOR,
        FIND_COMPONENT,
        BUILD_COMPONENT
    }

    private ConnectedComponents findComponentsRecursive() {
        for (int node = 0; node < graph.getNodes(); node++) {
            if (nodeIndex[node] == -1) {
                findComponentForNode(node);
            }
        }
        return components;
    }

    private void findComponentForNode(int v) {
        setupNextNode(v);
        // we have to create a new explorer on each iteration because of the nested edge iterations
        EdgeExplorer explorer = graph.createEdgeExplorer(edgeFilter);
        EdgeIterator iter = explorer.setBaseNode(v);
        while (iter.next()) {
            int w = iter.getAdjNode();
            if (nodeIndex[w] == -1) {
                findComponentForNode(w);
                nodeLowLink[v] = Math.min(nodeLowLink[v], nodeLowLink[w]);
            } else if (nodeOnStack.get(w))
                nodeLowLink[v] = Math.min(nodeLowLink[v], nodeIndex[w]);
        }
        buildComponent(v);
    }

    private void setupNextNode(int v) {
        nodeIndex[v] = currIndex;
        nodeLowLink[v] = currIndex;
        currIndex++;
        tarjanStack.addLast(v);
        nodeOnStack.set(v);
    }

    private void buildComponent(int v) {
        if (nodeLowLink[v] == nodeIndex[v]) {
            if (tarjanStack.getLast() == v) {
                tarjanStack.removeLast();
                nodeOnStack.clear(v);
                components.numComponents++;
                components.numNodes++;
                if (!excludeSingleNodeComponents)
                    components.singleNodeComponents.set(v);
            } else {
                IntArrayList component = new IntArrayList();
                while (true) {
                    int w = tarjanStack.removeLast();
                    component.add(w);
                    nodeOnStack.clear(w);
                    if (w == v)
                        break;
                }
                component.trimToSize();
                assert component.size() > 1;
                components.numComponents++;
                components.numNodes += component.size();
                components.components.add(component);
                if (component.size() > components.biggestComponent.size())
                    components.biggestComponent = component;
            }
        }
    }

    private ConnectedComponents findComponents() {
        for (int node = 0; node < graph.getNodes(); ++node) {
            if (nodeIndex[node] != -1)
                continue;

            pushFindComponentForNode(node);
            while (hasNext()) {
                pop();
                switch (dfsState) {
                    case BUILD_COMPONENT:
                        buildComponent(v);
                        break;
                    case UPDATE:
                        nodeLowLink[v] = Math.min(nodeLowLink[v], nodeLowLink[w]);
                        break;
                    case HANDLE_NEIGHBOR: {
                        if (nodeIndex[w] != -1 && nodeOnStack.get(w))
                            nodeLowLink[v] = Math.min(nodeLowLink[v], nodeIndex[w]);
                        if (nodeIndex[w] == -1) {
                            // we are pushing updateLowLinks first so it will run *after* findComponent finishes
                            pushUpdateLowLinks(v, w);
                            pushFindComponentForNode(w);
                        }
                        break;
                    }
                    case FIND_COMPONENT: {
                        setupNextNode(v);
                        // we push buildComponent first so it will run *after* we finished traversing the edges
                        pushBuildComponent(v);
                        EdgeIterator iter = explorer.setBaseNode(v);
                        while (iter.next()) {
                            pushHandleNeighbor(v, iter.getAdjNode());
                        }
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unknown state: " + dfsState);
                }
            }
        }
        return components;
    }

    private boolean hasNext() {
        return !dfsStack.isEmpty();
    }

    private void pop() {
        long l = dfsStack.removeLast();
        // We are maintaining a stack of longs to hold three kinds of information: two node indices (v&w) and the kind
        // of code ('state') we want to execute for a given stack item. The following code combined with the pushXYZ
        // methods does the fwd/bwd conversion between this information and a single long value.
        int low = bitUtil.getIntLow(l);
        int high = bitUtil.getIntHigh(l);
        if (low > 0 && high > 0) {
            dfsState = State.HANDLE_NEIGHBOR;
            v = low - 1;
            w = high - 1;
        } else if (low > 0 && high < 0) {
            dfsState = State.UPDATE;
            v = low - 1;
            w = -high - 1;
        } else if (low == 0) {
            dfsState = State.BUILD_COMPONENT;
            v = high - 1;
            w = -1;
        } else {
            dfsState = State.FIND_COMPONENT;
            v = low - 1;
            w = -1;
        }
    }

    private void pushHandleNeighbor(int v, int w) {
        assert v >= 0 && v < Integer.MAX_VALUE;
        assert w >= 0 && w < Integer.MAX_VALUE;
        dfsStack.addLast(bitUtil.combineIntsToLong(v + 1, w + 1));
    }

    private void pushUpdateLowLinks(int v, int w) {
        assert v >= 0 && v < Integer.MAX_VALUE;
        assert w >= 0 && w < Integer.MAX_VALUE;
        dfsStack.addLast(bitUtil.combineIntsToLong(v + 1, -(w + 1)));
    }

    private void pushBuildComponent(int v) {
        assert v >= 0 && v < Integer.MAX_VALUE;
        dfsStack.addLast(bitUtil.combineIntsToLong(0, v + 1));
    }

    private void pushFindComponentForNode(int v) {
        assert v >= 0 && v < Integer.MAX_VALUE;
        dfsStack.addLast(bitUtil.combineIntsToLong(v + 1, 0));
    }

    public static class ConnectedComponents {
        private final List<IntArrayList> components;
        private final BitSet singleNodeComponents;
        private IntArrayList biggestComponent;
        private int numComponents;
        private int numNodes;

        ConnectedComponents(int nodes) {
            components = new ArrayList<>();
            singleNodeComponents = new BitSet(Math.max(nodes, 0));
            if (!(singleNodeComponents.getClass().getName().contains("hppc")))
                throw new IllegalStateException("Was meant to be hppc BitSet");
            biggestComponent = new IntArrayList();
        }

        /**
         * A list of arrays each containing the nodes of a strongly connected component. Components with only a single
         * node are not included here, but need to be obtained using {@link #getSingleNodeComponents()}.
         */
        public List<IntArrayList> getComponents() {
            return components;
        }

        /**
         * The set of nodes that form their own (single-node) component. If {@link TarjanSCC#excludeSingleNodeComponents}
         * is enabled this set will be empty.
         */
        public BitSet getSingleNodeComponents() {
            return singleNodeComponents;
        }

        /**
         * The total number of strongly connected components. This always includes single-node components.
         */
        public int getTotalComponents() {
            return numComponents;
        }

        /**
         * A reference to the biggest component contained in {@link #getComponents()} or an empty list if there are
         * either no components or the biggest component has only a single node (and hence {@link #getComponents()} is
         * empty).
         */
        public IntArrayList getBiggestComponent() {
            return biggestComponent;
        }

        public int getNodes() {
            return numNodes;
        }

    }
}
