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

package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.core.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongFunction;

public class WayToEdgeConverter {
    private final BaseGraph baseGraph;
    private final LongFunction<Iterator<IntCursor>> edgesByWay;

    public WayToEdgeConverter(BaseGraph baseGraph, LongFunction<Iterator<IntCursor>> edgesByWay) {
        this.baseGraph = baseGraph;
        this.edgesByWay = edgesByWay;
    }

    /**
     * Finds the edge IDs associated with the given OSM ways that are adjacent to the given via-node.
     * For each way there can be multiple edge IDs and there should be exactly one that is adjacent to the via-node
     * for each way. Otherwise we throw {@link OSMRestrictionException}
     */
    public NodeResult convertForViaNode(LongArrayList fromWays, int viaNode, LongArrayList toWays) throws OSMRestrictionException {
        if (fromWays.isEmpty() || toWays.isEmpty())
            throw new IllegalArgumentException("There must be at least one from- and to-way");
        if (fromWays.size() > 1 && toWays.size() > 1)
            throw new IllegalArgumentException("There can only be multiple from- or to-ways, but not both");
        NodeResult result = new NodeResult(fromWays.size(), toWays.size());
        for (LongCursor fromWay : fromWays)
            edgesByWay.apply(fromWay.value).forEachRemaining(e -> {
                if (baseGraph.isAdjacentToNode(e.value, viaNode))
                    result.fromEdges.add(e.value);
            });
        if (result.fromEdges.size() < fromWays.size())
            throw new OSMRestrictionException("has from member ways that aren't adjacent to the via member node");
        else if (result.fromEdges.size() > fromWays.size())
            throw new OSMRestrictionException("has from member ways that aren't split at the via member node");

        for (LongCursor toWay : toWays)
            edgesByWay.apply(toWay.value).forEachRemaining(e -> {
                if (baseGraph.isAdjacentToNode(e.value, viaNode))
                    result.toEdges.add(e.value);
            });
        if (result.toEdges.size() < toWays.size())
            throw new OSMRestrictionException("has to member ways that aren't adjacent to the via member node");
        else if (result.toEdges.size() > toWays.size())
            throw new OSMRestrictionException("has to member ways that aren't split at the via member node");
        return result;
    }

    public static class NodeResult {
        private final IntArrayList fromEdges;
        private final IntArrayList toEdges;

        public NodeResult(int numFrom, int numTo) {
            fromEdges = new IntArrayList(numFrom);
            toEdges = new IntArrayList(numTo);
        }

        public IntArrayList getFromEdges() {
            return fromEdges;
        }

        public IntArrayList getToEdges() {
            return toEdges;
        }
    }

    /**
     * Finds the edge IDs associated with the given OSM ways that are adjacent to each other. For example for given
     * from-, via- and to-ways there can be multiple edges associated with each (because each way can be split into
     * multiple edges). We then need to find the from-edge that is connected with one of the via-edges which in turn
     * must be connected with one of the to-edges. We use DFS/backtracking to do this.
     * There can also be *multiple* via-ways, but the concept is the same.
     * Note that there can also be multiple from- or to-*ways*, but only one of each of them should be considered at a
     * time. In contrast to the via-ways there are only multiple from/to-ways, because of restrictions like no_entry or
     * no_exit where there can be multiple from- or to-members. So we need to find one edge-chain for each pair of from-
     * and to-ways.
     * Besides the edge IDs we also return the node IDs that connect the edges, so we can add turn restrictions at these
     * nodes later.
     */
    public EdgeResult convertForViaWays(LongArrayList fromWays, LongArrayList viaWays, LongArrayList toWays) throws OSMRestrictionException {
        if (fromWays.isEmpty() || toWays.isEmpty() || viaWays.isEmpty())
            throw new IllegalArgumentException("There must be at least one from-, via- and to-way");
        if (fromWays.size() > 1 && toWays.size() > 1)
            throw new IllegalArgumentException("There can only be multiple from- or to-ways, but not both");
        List<IntArrayList> solutions = new ArrayList<>();
        for (LongCursor fromWay : fromWays)
            for (LongCursor toWay : toWays)
                findEdgeChain(fromWay.value, viaWays, toWay.value, solutions);
        if (solutions.size() < fromWays.size() * toWays.size())
            throw new OSMRestrictionException("has from/to member ways that aren't connected with the via member way(s)");
        else if (solutions.size() > fromWays.size() * toWays.size())
            throw new OSMRestrictionException("has from/to member ways that aren't split at the via member way(s)");
        return buildResult(solutions, new EdgeResult(fromWays.size(), viaWays.size(), toWays.size()));
    }

    private static EdgeResult buildResult(List<IntArrayList> edgeChains, EdgeResult result) {
        for (IntArrayList edgeChain : edgeChains) {
            result.fromEdges.add(edgeChain.get(0));
            if (result.nodes.isEmpty()) {
                // the via-edges and nodes are the same for edge chain
                for (int i = 1; i < edgeChain.size() - 3; i += 2) {
                    result.nodes.add(edgeChain.get(i));
                    result.viaEdges.add(edgeChain.get(i + 1));
                }
                result.nodes.add(edgeChain.get(edgeChain.size() - 2));
            }
            result.toEdges.add(edgeChain.get(edgeChain.size() - 1));
        }
        return result;
    }

    private void findEdgeChain(long fromWay, LongArrayList viaWays, long toWay, List<IntArrayList> solutions) throws OSMRestrictionException {
        // For each edge chain there must be one edge associated with the from-way, one for each via-way and one
        // associated with the to-way. We use DFS with backtracking to find all edge chains that connect an edge
        // associated with the from-way with one associated with the to-way.
        IntArrayList viaEdgesForViaWays = new IntArrayList(viaWays.size());
        for (LongCursor c : viaWays) {
            Iterator<IntCursor> iterator = edgesByWay.apply(c.value);
            viaEdgesForViaWays.add(iterator.next().value);
            if (iterator.hasNext())
                throw new OSMRestrictionException("has via member way that isn't split at adjacent ways: " + c.value);
        }
        IntArrayList toEdges = listFromIterator(edgesByWay.apply(toWay));

        // the search starts at *every* from edge
        edgesByWay.apply(fromWay).forEachRemaining(from -> {
            EdgeIteratorState edge = baseGraph.getEdgeIteratorState(from.value, Integer.MIN_VALUE);
            explore(viaEdgesForViaWays, toEdges, edge.getBaseNode(), 0, IntArrayList.from(edge.getEdge(), edge.getBaseNode()), solutions);
            explore(viaEdgesForViaWays, toEdges, edge.getAdjNode(), 0, IntArrayList.from(edge.getEdge(), edge.getAdjNode()), solutions);
        });
    }

    private void explore(IntArrayList viaEdgesForViaWays, IntArrayList toEdges, int node, int viaCount, IntArrayList curr, List<IntArrayList> solutions) {
        if (viaCount == viaEdgesForViaWays.size()) {
            for (IntCursor to : toEdges) {
                if (baseGraph.isAdjacentToNode(to.value, node)) {
                    IntArrayList solution = new IntArrayList(curr);
                    solution.add(to.value);
                    solutions.add(solution);
                }
            }
            return;
        }
        for (int i = 0; i < viaEdgesForViaWays.size(); i++) {
            int viaEdge = viaEdgesForViaWays.get(i);
            if (viaEdge < 0) continue;
            if (baseGraph.isAdjacentToNode(viaEdge, node)) {
                int otherNode = baseGraph.getOtherNode(viaEdge, node);
                curr.add(viaEdge, otherNode);
                // every via edge must only be used once, but instead of removing it we just set it to -1
                viaEdgesForViaWays.set(i, -1);
                explore(viaEdgesForViaWays, toEdges, otherNode, viaCount + 1, curr, solutions);
                // backtrack
                curr.elementsCount -= 2;
                viaEdgesForViaWays.set(i, viaEdge);
            }
        }
    }

    private static IntArrayList listFromIterator(Iterator<IntCursor> iterator) {
        IntArrayList result = new IntArrayList();
        iterator.forEachRemaining(c -> result.add(c.value));
        return result;
    }

    public static class EdgeResult {
        private final IntArrayList fromEdges;
        private final IntArrayList viaEdges;
        private final IntArrayList toEdges;
        private final IntArrayList nodes;

        public EdgeResult(int numFrom, int numVia, int numTo) {
            fromEdges = new IntArrayList(numFrom);
            viaEdges = new IntArrayList(numVia);
            toEdges = new IntArrayList(numTo);
            nodes = new IntArrayList(numVia + 1);
        }

        public IntArrayList getFromEdges() {
            return fromEdges;
        }

        public IntArrayList getViaEdges() {
            return viaEdges;
        }

        public IntArrayList getToEdges() {
            return toEdges;
        }

        /**
         * All the intermediate nodes, i.e. for an edge chain like this:
         * <pre>
         *   a   b   c   d
         * 0---1---2---3---4
         * </pre>
         * where 'a' is the from-edge and 'd' is the to-edge this will be [1,2,3]
         */
        public IntArrayList getNodes() {
            return nodes;
        }
    }
}
