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
package com.graphhopper.routing.cch;

import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Node order for a customizable contraction hierarchy, following RoutingKit's
 * {@code compute_nested_node_dissection_order_using_inertial_flow}.
 * <p>
 * The graph is treated as undirected. Inertial flow tries four geographic directions and keeps the
 * cut with the best balance among the 25%, 33% and 40% terminal sets. Nodes on the far side of that
 * cut become the separator and are placed last in the order, so a CCH contracts them last. Each side
 * is ordered the same way.
 * <p>
 * Level 0 in the returned provider is the first node to contract.
 */
public final class NestedDissectionOrder {
    private NestedDissectionOrder() {
    }

    public static NodeOrderingProvider compute(Graph graph, EdgeFilter access) {
        if (graph == null)
            throw new IllegalArgumentException("graph must not be null");
        if (access == null)
            throw new IllegalArgumentException("access filter must not be null");
        int nodes = graph.getNodes();
        double[] latitude = new double[nodes];
        double[] longitude = new double[nodes];
        NodeAccess nodeAccess = graph.getNodeAccess();
        for (int node = 0; node < nodes; node++) {
            latitude[node] = nodeAccess.getLat(node);
            longitude[node] = nodeAccess.getLon(node);
        }
        Fragment fragment = makeFragment(graph, access);
        int[] order = separatorDecomposition(fragment, latitude, longitude);
        return NodeOrderingProvider.fromArray(order);
    }

    private static Fragment makeFragment(Graph graph, EdgeFilter access) {
        int nodes = graph.getNodes();
        int[] inputTail = new int[Math.max(16, graph.getEdges() * 2)];
        int[] inputHead = new int[inputTail.length];
        int inputCount = 0;
        EdgeExplorer explorer = graph.createEdgeExplorer(access);
        for (int node = 0; node < nodes; node++) {
            EdgeIterator iter = explorer.setBaseNode(node);
            while (iter.next()) {
                int adj = iter.getAdjNode();
                if (adj == node)
                    continue;
                if (inputCount == inputTail.length) {
                    inputTail = Arrays.copyOf(inputTail, inputTail.length * 2);
                    inputHead = Arrays.copyOf(inputHead, inputHead.length * 2);
                }
                inputTail[inputCount] = node;
                inputHead[inputCount] = adj;
                inputCount++;
            }
        }
        int[] global = new int[nodes];
        for (int node = 0; node < nodes; node++)
            global[node] = node;
        return symmetrize(nodes, global, inputTail, inputHead, inputCount);
    }

    /**
     * Each input arc becomes a pair of opposing arcs so the flow graph is undirected.
     */
    private static Fragment symmetrize(int nodes, int[] global, int[] inputTail, int[] inputHead, int inputCount) {
        int arcs = 0;
        for (int i = 0; i < inputCount; i++)
            if (inputTail[i] != inputHead[i])
                arcs++;
        int[] tail = new int[arcs * 2];
        int[] head = new int[arcs * 2];
        int[] back = new int[arcs * 2];
        int forward = 0;
        for (int i = 0; i < inputCount; i++) {
            if (inputTail[i] == inputHead[i])
                continue;
            int reverse = forward + arcs;
            tail[forward] = inputTail[i];
            head[forward] = inputHead[i];
            tail[reverse] = inputHead[i];
            head[reverse] = inputTail[i];
            back[forward] = reverse;
            back[reverse] = forward;
            forward++;
        }
        return sortArcs(nodes, global, tail, head, back);
    }

    private static Fragment sortArcs(int nodes, int[] global, int[] tail, int[] head, int[] back) {
        int arcs = tail.length;
        Integer[] position = new Integer[arcs];
        for (int i = 0; i < arcs; i++)
            position[i] = i;
        Arrays.sort(position, (left, right) -> {
            int byTail = Integer.compare(tail[left], tail[right]);
            if (byTail != 0)
                return byTail;
            int byHead = Integer.compare(head[left], head[right]);
            if (byHead != 0)
                return byHead;
            return Integer.compare(left, right);
        });
        int[] sortedTail = new int[arcs];
        int[] sortedHead = new int[arcs];
        int[] sortedBack = new int[arcs];
        int[] newIndex = new int[arcs];
        for (int i = 0; i < arcs; i++)
            newIndex[position[i]] = i;
        for (int i = 0; i < arcs; i++) {
            int old = position[i];
            sortedTail[i] = tail[old];
            sortedHead[i] = head[old];
            sortedBack[i] = newIndex[back[old]];
        }
        Fragment fragment = new Fragment(global, firstOut(nodes, sortedTail), sortedTail, sortedHead, sortedBack);
        checkBackArcs(fragment);
        return fragment;
    }

    private static int[] firstOut(int nodes, int[] tail) {
        int[] firstOut = new int[nodes + 1];
        for (int node : tail)
            firstOut[node + 1]++;
        for (int node = 0; node < nodes; node++)
            firstOut[node + 1] += firstOut[node];
        return firstOut;
    }

    private static void checkBackArcs(Fragment fragment) {
        for (int arc = 0; arc < fragment.arcCount(); arc++) {
            int reverse = fragment.back[arc];
            if (fragment.back[reverse] != arc
                    || fragment.tail[arc] != fragment.head[reverse]
                    || fragment.head[arc] != fragment.tail[reverse])
                throw new IllegalStateException("Broken reverse arc at " + arc);
        }
    }

    private static int[] separatorDecomposition(Fragment fragment, double[] latitude, double[] longitude) {
        int nodes = fragment.nodeCount();
        int[] order = new int[nodes];
        if (nodes <= 1) {
            if (nodes == 1)
                order[0] = fragment.global[0];
            return order;
        }

        List<Fragment> parts = connectedComponents(fragment);
        int orderBegin = 0;
        int orderEnd = nodes;
        for (Fragment part : parts) {
            if (part.nodeCount() == 1) {
                order[--orderEnd] = part.global[0];
                continue;
            }
            boolean[] separator = computeSeparator(part, latitude, longitude);
            if (count(separator) == 0) {
                int[] fallback = new int[part.nodeCount()];
                for (int node = 0; node < fallback.length; node++)
                    fallback[node] = part.global[node];
                System.arraycopy(fallback, 0, order, orderBegin, fallback.length);
                orderBegin += fallback.length;
                continue;
            }
            int[] subOrder = separatorDecomposition(withoutSeparatorArcs(part, separator), latitude, longitude);
            System.arraycopy(subOrder, 0, order, orderBegin, subOrder.length);
            orderBegin += subOrder.length;
        }
        if (orderBegin != orderEnd)
            throw new IllegalStateException("Nested dissection left a gap in the order: " + orderBegin + " vs " + orderEnd);
        return order;
    }

    private static boolean[] computeSeparator(Fragment fragment, double[] latitude, double[] longitude) {
        Cut cut25 = inertialFlow(fragment, 25, latitude, longitude);
        Cut cut33 = inertialFlow(fragment, 33, latitude, longitude);
        Cut cut40 = inertialFlow(fragment, 40, latitude, longitude);
        Cut chosen = chooseCut(cut25, cut33, cut40);
        return deriveSeparator(fragment, pickSmallerSide(fragment.nodeCount(), chosen));
    }

    /**
     * Prefer the cut with the smaller cut-size / side-size ratio, in the same order as RoutingKit:
     * 25% if it beats both alternatives, otherwise 33% if it beats 40%, otherwise 40%.
     */
    private static Cut chooseCut(Cut cut25, Cut cut33, Cut cut40) {
        if ((long) cut25.cutSize * cut33.count < (long) cut33.cutSize * cut25.count
                && (long) cut25.cutSize * cut40.count < (long) cut40.cutSize * cut25.count)
            return cut25;
        if ((long) cut33.cutSize * cut40.count < (long) cut40.cutSize * cut33.count)
            return cut33;
        return cut40;
    }

    private static Cut inertialFlow(Fragment fragment, int minBalancePercent, double[] latitude, double[] longitude) {
        int nodes = fragment.nodeCount();
        int sideSize = (int) ((long) nodes * minBalancePercent / 100);
        if (sideSize < 1)
            sideSize = 1;
        BlockingFlow horizontal = new BlockingFlow(fragment, terminals(fragment, sideSize, latitude, longitude, Direction.LATITUDE));
        BlockingFlow vertical = new BlockingFlow(fragment, terminals(fragment, sideSize, latitude, longitude, Direction.LONGITUDE));
        BlockingFlow mainDiagonal = new BlockingFlow(fragment, terminals(fragment, sideSize, latitude, longitude, Direction.MAIN_DIAGONAL));
        BlockingFlow antiDiagonal = new BlockingFlow(fragment, terminals(fragment, sideSize, latitude, longitude, Direction.ANTI_DIAGONAL));
        BlockingFlow[] cutters = {horizontal, vertical, mainDiagonal, antiDiagonal};
        for (; ; ) {
            BlockingFlow cutter = smallestCutter(cutters);
            if (cutter.isFinished())
                return cutter.balancedCut();
            cutter.advance();
        }
    }

    private static BlockingFlow smallestCutter(BlockingFlow[] cutters) {
        BlockingFlow best = cutters[0];
        for (int i = 1; i < cutters.length; i++)
            if (cutters[i].flowIntensity < best.flowIntensity)
                best = cutters[i];
        return best;
    }

    private static SourceTarget terminals(Fragment fragment, int sideSize, double[] latitude, double[] longitude, Direction direction) {
        int nodes = fragment.nodeCount();
        Integer[] byKey = new Integer[nodes];
        for (int node = 0; node < nodes; node++)
            byKey[node] = node;
        Arrays.sort(byKey, (left, right) -> {
            int byCoordinate = Double.compare(direction.key(latitude, longitude, fragment.global[left]), direction.key(latitude, longitude, fragment.global[right]));
            if (byCoordinate != 0)
                return byCoordinate;
            return Integer.compare(left, right);
        });
        boolean[] source = new boolean[nodes];
        boolean[] target = new boolean[nodes];
        for (int i = 0; i < sideSize; i++)
            source[byKey[i]] = true;
        for (int i = nodes - sideSize; i < nodes; i++)
            target[byKey[i]] = true;
        return new SourceTarget(source, target);
    }

    private static boolean[] pickSmallerSide(int nodes, Cut cut) {
        boolean[] side = cut.side;
        int count = cut.count;
        if (count >= (nodes + 1) / 2) {
            for (int node = 0; node < nodes; node++)
                side[node] = !side[node];
            count = nodes - count;
        }
        cut.count = count;
        return side;
    }

    /**
     * Separator nodes lie on the large side of the cut, adjacent to the small side.
     */
    private static boolean[] deriveSeparator(Fragment fragment, boolean[] smallSide) {
        int nodes = fragment.nodeCount();
        int smallCount = count(smallSide);
        boolean smallSideIsMarked = smallCount <= nodes / 2;
        boolean[] separator = new boolean[nodes];
        for (int arc = 0; arc < fragment.arcCount(); arc++) {
            int tail = fragment.tail[arc];
            int head = fragment.head[arc];
            if ((smallSide[tail] == smallSideIsMarked) && (smallSide[head] != smallSideIsMarked))
                separator[head] = true;
        }
        return separator;
    }

    private static Fragment withoutSeparatorArcs(Fragment fragment, boolean[] separator) {
        int kept = 0;
        for (int arc = 0; arc < fragment.arcCount(); arc++)
            if (!separator[fragment.tail[arc]] && !separator[fragment.head[arc]])
                kept++;
        int[] tail = new int[kept];
        int[] head = new int[kept];
        int[] back = new int[kept];
        int[] newIndex = new int[fragment.arcCount()];
        Arrays.fill(newIndex, -1);
        int cursor = 0;
        for (int arc = 0; arc < fragment.arcCount(); arc++) {
            if (separator[fragment.tail[arc]] || separator[fragment.head[arc]])
                continue;
            newIndex[arc] = cursor;
            tail[cursor] = fragment.tail[arc];
            head[cursor] = fragment.head[arc];
            back[cursor] = fragment.back[arc];
            cursor++;
        }
        for (int arc = 0; arc < kept; arc++) {
            int reverse = newIndex[back[arc]];
            if (reverse < 0)
                throw new IllegalStateException("Separator removal dropped one direction of an arc");
            back[arc] = reverse;
        }
        Fragment reduced = new Fragment(fragment.global, firstOut(fragment.nodeCount(), tail), tail, head, back);
        checkBackArcs(reduced);
        return reduced;
    }

    private static List<Fragment> connectedComponents(Fragment fragment) {
        int nodes = fragment.nodeCount();
        int[] component = new int[nodes];
        Arrays.fill(component, -1);
        int[] newId = new int[nodes];
        int[] stack = new int[nodes];
        int nextId = 0;
        int componentCount = 0;
        for (int root = 0; root < nodes; root++) {
            if (component[root] != -1)
                continue;
            component[root] = componentCount;
            int stackSize = 1;
            stack[0] = root;
            while (stackSize > 0) {
                int node = stack[--stackSize];
                newId[node] = nextId++;
                for (int arc = fragment.firstOut[node]; arc < fragment.firstOut[node + 1]; arc++) {
                    int head = fragment.head[arc];
                    if (component[head] == -1) {
                        component[head] = componentCount;
                        stack[stackSize++] = head;
                    }
                }
            }
            componentCount++;
        }

        int[] remappedTail = new int[fragment.arcCount()];
        int[] remappedHead = new int[fragment.arcCount()];
        int[] remappedGlobal = new int[nodes];
        int[] remappedComponent = new int[nodes];
        for (int arc = 0; arc < fragment.arcCount(); arc++) {
            remappedTail[arc] = newId[fragment.tail[arc]];
            remappedHead[arc] = newId[fragment.head[arc]];
        }
        for (int node = 0; node < nodes; node++) {
            remappedGlobal[newId[node]] = fragment.global[node];
            remappedComponent[newId[node]] = component[node];
        }
        Fragment renumbered = sortArcs(nodes, remappedGlobal, remappedTail, remappedHead, fragment.back);

        List<Fragment> parts = new ArrayList<>();
        if (nodes == 0)
            return parts;
        int nodeBegin = 0;
        int arcBegin = 0;
        int nodeEnd = 1;
        int current = remappedComponent[0];
        while (nodeEnd <= nodes) {
            boolean boundary = nodeEnd == nodes || remappedComponent[nodeEnd] != current;
            if (boundary) {
                int arcEnd = arcBegin;
                while (arcEnd < renumbered.arcCount() && remappedComponent[renumbered.tail[arcEnd]] == current)
                    arcEnd++;
                parts.add(extractPart(renumbered, nodeBegin, nodeEnd, arcBegin, arcEnd));
                nodeBegin = nodeEnd;
                arcBegin = arcEnd;
                if (nodeEnd < nodes)
                    current = remappedComponent[nodeEnd];
            }
            nodeEnd++;
        }
        return parts;
    }

    private static Fragment extractPart(Fragment fragment, int nodeBegin, int nodeEnd, int arcBegin, int arcEnd) {
        int partNodes = nodeEnd - nodeBegin;
        int partArcs = arcEnd - arcBegin;
        int[] global = Arrays.copyOfRange(fragment.global, nodeBegin, nodeEnd);
        int[] tail = new int[partArcs];
        int[] head = new int[partArcs];
        int[] back = new int[partArcs];
        for (int arc = 0; arc < partArcs; arc++) {
            tail[arc] = fragment.tail[arcBegin + arc] - nodeBegin;
            head[arc] = fragment.head[arcBegin + arc] - nodeBegin;
            back[arc] = fragment.back[arcBegin + arc] - arcBegin;
        }
        Fragment part = new Fragment(global, firstOut(partNodes, tail), tail, head, back);
        checkBackArcs(part);
        return part;
    }

    private static int count(boolean[] flags) {
        int count = 0;
        for (boolean flag : flags)
            if (flag)
                count++;
        return count;
    }

    private enum Direction {
        LATITUDE {
            @Override
            double key(double[] latitude, double[] longitude, int node) {
                return latitude[node];
            }
        },
        LONGITUDE {
            @Override
            double key(double[] latitude, double[] longitude, int node) {
                return longitude[node];
            }
        },
        MAIN_DIAGONAL {
            @Override
            double key(double[] latitude, double[] longitude, int node) {
                return latitude[node] + longitude[node];
            }
        },
        ANTI_DIAGONAL {
            @Override
            double key(double[] latitude, double[] longitude, int node) {
                return latitude[node] - longitude[node];
            }
        };

        abstract double key(double[] latitude, double[] longitude, int node);
    }

    private static final class SourceTarget {
        final boolean[] source;
        final boolean[] target;

        SourceTarget(boolean[] source, boolean[] target) {
            this.source = source;
            this.target = target;
        }
    }

    private static final class Cut {
        final boolean[] side;
        int count;
        final int cutSize;

        Cut(boolean[] side, int count, int cutSize) {
            this.side = side;
            this.count = count;
            this.cutSize = cutSize;
        }
    }

    private static final class Fragment {
        final int[] global;
        final int[] firstOut;
        final int[] tail;
        final int[] head;
        final int[] back;

        Fragment(int[] global, int[] firstOut, int[] tail, int[] head, int[] back) {
            this.global = global;
            this.firstOut = firstOut;
            this.tail = tail;
            this.head = head;
            this.back = back;
        }

        int nodeCount() {
            return global.length;
        }

        int arcCount() {
            return tail.length;
        }
    }

    /**
     * Unit-capacity blocking flow on the residual graph, stopped by the caller once this instance
     * has the smallest flow among the directional cutters.
     */
    private static final class BlockingFlow {
        final Fragment fragment;
        final boolean[] source;
        final boolean[] target;
        final boolean[] saturated;
        final boolean[] blocked;
        int flowIntensity;
        boolean finished;

        BlockingFlow(Fragment fragment, SourceTarget terminals) {
            this.fragment = fragment;
            this.source = terminals.source;
            this.target = terminals.target;
            this.saturated = new boolean[fragment.arcCount()];
            this.blocked = new boolean[fragment.arcCount()];
        }

        boolean isFinished() {
            return finished;
        }

        void advance() {
            if (!finished && computeBlockingFlow())
                flowIntensity += augment();
            else
                finished = true;
        }

        Cut balancedCut() {
            int nodes = fragment.nodeCount();
            boolean[] sourceReachable = Arrays.copyOf(source, nodes);
            boolean[] targetReachable = Arrays.copyOf(target, nodes);
            int[] stack = new int[nodes];
            int[] sourcePierce = new int[Math.max(1, fragment.arcCount())];
            int[] targetPierce = new int[Math.max(1, fragment.arcCount())];

            int stackEnd = 0;
            for (int node = 0; node < nodes; node++)
                if (source[node])
                    stack[stackEnd++] = node;
            Grow sourceSide = grow(stack, stackEnd, sourceReachable, sourcePierce, 0, true);

            stackEnd = 0;
            for (int node = 0; node < nodes; node++)
                if (target[node])
                    stack[stackEnd++] = node;
            Grow targetSide = grow(stack, stackEnd, targetReachable, targetPierce, 0, false);

            for (; ; ) {
                if (sourceSide.count <= targetSide.count) {
                    Pierce pierce = popPierce(sourcePierce, sourceSide.pierceEnd, sourceReachable, targetReachable);
                    if (pierce.node < 0)
                        return new Cut(sourceReachable, sourceSide.count, flowIntensity);
                    sourceReachable[pierce.node] = true;
                    stack[0] = pierce.node;
                    Grow grown = grow(stack, 1, sourceReachable, sourcePierce, pierce.remaining, true);
                    sourceSide = new Grow(sourceSide.count + grown.count, grown.pierceEnd);
                } else {
                    Pierce pierce = popPierce(targetPierce, targetSide.pierceEnd, sourceReachable, targetReachable);
                    if (pierce.node < 0)
                        return new Cut(targetReachable, targetSide.count, flowIntensity);
                    targetReachable[pierce.node] = true;
                    stack[0] = pierce.node;
                    Grow grown = grow(stack, 1, targetReachable, targetPierce, pierce.remaining, false);
                    targetSide = new Grow(targetSide.count + grown.count, grown.pierceEnd);
                }
            }
        }

        private Grow grow(int[] stack, int stackEnd, boolean[] reachable, int[] pierce, int pierceEnd, boolean fromSource) {
            int grown = 0;
            while (stackEnd > 0) {
                grown++;
                int node = stack[--stackEnd];
                for (int arc = fragment.firstOut[node]; arc < fragment.firstOut[node + 1]; arc++) {
                    int head = fragment.head[arc];
                    boolean residualBlocked = fromSource ? saturated[arc] : saturated[fragment.back[arc]];
                    if (residualBlocked) {
                        if (pierceEnd == pierce.length)
                            throw new IllegalStateException("Piercing stack overflow");
                        pierce[pierceEnd++] = head;
                    } else if (!reachable[head]) {
                        reachable[head] = true;
                        stack[stackEnd++] = head;
                    }
                }
            }
            return new Grow(grown, pierceEnd);
        }

        private Pierce popPierce(int[] pierce, int pierceEnd, boolean[] sourceReachable, boolean[] targetReachable) {
            while (pierceEnd > 0) {
                int node = pierce[--pierceEnd];
                if (!sourceReachable[node] && !targetReachable[node])
                    return new Pierce(node, pierceEnd);
            }
            return new Pierce(-1, 0);
        }

        private boolean computeBlockingFlow() {
            int nodes = fragment.nodeCount();
            Arrays.fill(blocked, false);
            boolean[] onSameLevelOrLower = new boolean[nodes];
            boolean[] queued = new boolean[nodes];
            int[] queue = new int[nodes];
            int begin = 0;
            int end = 0;
            for (int node = 0; node < nodes; node++) {
                if (source[node])
                    queue[end++] = node;
            }
            int levelEnd = end;
            boolean targetReachable = false;
            while (begin != end) {
                for (int i = begin; i < levelEnd; i++)
                    onSameLevelOrLower[queue[i]] = true;
                for (int i = begin; i < levelEnd; i++) {
                    int node = queue[i];
                    for (int arc = fragment.firstOut[node]; arc < fragment.firstOut[node + 1]; arc++) {
                        if (saturated[arc] || onSameLevelOrLower[fragment.head[arc]]) {
                            blocked[arc] = true;
                        } else {
                            int head = fragment.head[arc];
                            if (target[head])
                                targetReachable = true;
                            else if (!queued[head]) {
                                queue[end++] = head;
                                queued[head] = true;
                            }
                        }
                    }
                }
                begin = levelEnd;
                levelEnd = end;
            }
            return targetReachable;
        }

        private int augment() {
            int nodes = fragment.nodeCount();
            int[] pathNode = new int[nodes];
            int[] pathArc = new int[nodes];
            int augmented = 0;
            for (int root = 0; root < nodes; root++) {
                if (!source[root])
                    continue;
                pathNode[0] = root;
                int pathLength = 0;
                for (; ; ) {
                    int node = pathNode[pathLength];
                    int arc = firstUnblocked(node);
                    if (arc < 0) {
                        if (pathLength == 0)
                            break;
                        pathLength--;
                        blocked[pathArc[pathLength]] = true;
                    } else {
                        int head = fragment.head[arc];
                        pathArc[pathLength] = arc;
                        pathLength++;
                        if (pathLength >= nodes)
                            throw new IllegalStateException("Blocking-flow path is longer than the graph");
                        pathNode[pathLength] = head;
                        if (target[head]) {
                            for (int i = 0; i < pathLength; i++) {
                                int forward = pathArc[i];
                                blocked[forward] = true;
                                int reverse = fragment.back[forward];
                                if (saturated[reverse])
                                    saturated[reverse] = false;
                                else
                                    saturated[forward] = true;
                            }
                            pathLength = 0;
                            augmented++;
                        }
                    }
                }
            }
            return augmented;
        }

        private int firstUnblocked(int node) {
            for (int arc = fragment.firstOut[node]; arc < fragment.firstOut[node + 1]; arc++)
                if (!blocked[arc])
                    return arc;
            return -1;
        }

        private static final class Grow {
            final int count;
            final int pierceEnd;

            Grow(int count, int pierceEnd) {
                this.count = count;
                this.pierceEnd = pierceEnd;
            }
        }

        private static final class Pierce {
            final int node;
            final int remaining;

            Pierce(int node, int remaining) {
                this.node = node;
                this.remaining = remaining;
            }
        }
    }
}
