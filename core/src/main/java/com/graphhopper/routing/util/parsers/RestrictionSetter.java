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

package com.graphhopper.routing.util.parsers;

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.procedures.IntProcedure;
import com.carrotsearch.hppc.procedures.LongIntProcedure;
import com.graphhopper.reader.osm.Pair;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.*;

import java.util.*;

import static com.graphhopper.util.EdgeIteratorState.REVERSE_STATE;

/**
 * Used to add via-node and via-edge restrictions to a given graph. Via-edge restrictions are realized
 * by augmenting the graph with artificial edges. For proper handling of overlapping turn restrictions
 * (turn restrictions that share the same via-edges) and turn restrictions for different encoded values
 * it is important to add all restrictions with a single call.
 */
public class RestrictionSetter {
    private static final IntSet EMPTY_SET = IntHashSet.from();
    private final BaseGraph baseGraph;
    private final List<BooleanEncodedValue> turnRestrictionEncs;

    public RestrictionSetter(BaseGraph baseGraph, List<BooleanEncodedValue> turnRestrictionEncs) {
        this.baseGraph = baseGraph;
        this.turnRestrictionEncs = turnRestrictionEncs;
    }

    public static Restriction createViaNodeRestriction(int fromEdge, int viaNode, int toEdge) {
        return new Restriction(IntArrayList.from(fromEdge, toEdge), viaNode);
    }

    public static Restriction createViaEdgeRestriction(IntArrayList edges) {
        if (edges.size() < 3)
            throw new IllegalArgumentException("Via-edge restrictions must have at least three edges, but got: " + edges.size());
        return new Restriction(edges, -1);
    }

    public void setRestrictions(List<Restriction> restrictions, List<BitSet> encBits) {
        if (restrictions.size() != encBits.size())
            throw new IllegalArgumentException("There must be as many encBits as restrictions. Got: " + encBits.size() + " and " + restrictions.size());
        List<InternalRestriction> internalRestrictions = restrictions.stream().map(this::convertToInternal).toList();
        disableRedundantRestrictions(internalRestrictions, encBits);
        LongIntMap artificialEdgeKeysByIncViaPairs = new LongIntScatterMap();
        IntObjectMap<IntSet> artificialEdgesByEdge = new IntObjectScatterMap<>();
        for (int i = 0; i < internalRestrictions.size(); i++) {
            if (encBits.get(i).cardinality() < 1) continue;
            InternalRestriction restriction = internalRestrictions.get(i);
            if (restriction.getEdgeKeys().size() < 3)
                continue;
            int incomingEdge = restriction.getFromEdge();
            for (int j = 1; j < restriction.getEdgeKeys().size() - 1; ++j) {
                int viaEdgeKey = restriction.getEdgeKeys().get(j);
                long key = BitUtil.LITTLE.toLong(incomingEdge, viaEdgeKey);
                int artificialEdgeKey;
                if (artificialEdgeKeysByIncViaPairs.containsKey(key)) {
                    artificialEdgeKey = artificialEdgeKeysByIncViaPairs.get(key);
                } else {
                    int viaEdge = GHUtility.getEdgeFromEdgeKey(viaEdgeKey);
                    EdgeIteratorState artificialEdgeState = baseGraph.copyEdge(viaEdge, true);
                    int artificialEdge = artificialEdgeState.getEdge();
                    if (artificialEdgesByEdge.containsKey(viaEdge)) {
                        IntSet artificialEdges = artificialEdgesByEdge.get(viaEdge);
                        artificialEdges.forEach((IntProcedure) a -> {
                            for (BooleanEncodedValue turnRestrictionEnc : turnRestrictionEncs)
                                restrictTurnsBetweenEdges(turnRestrictionEnc, artificialEdgeState, a);
                        });
                        artificialEdges.add(artificialEdge);
                    } else {
                        IntSet artificialEdges = new IntScatterSet();
                        artificialEdges.add(artificialEdge);
                        artificialEdgesByEdge.put(viaEdge, artificialEdges);
                    }
                    for (BooleanEncodedValue turnRestrictionEnc : turnRestrictionEncs)
                        restrictTurnsBetweenEdges(turnRestrictionEnc, artificialEdgeState, viaEdge);
                    artificialEdgeKey = artificialEdgeState.getEdgeKey();
                    if (baseGraph.getEdgeIteratorStateForKey(viaEdgeKey).get(REVERSE_STATE))
                        artificialEdgeKey = GHUtility.reverseEdgeKey(artificialEdgeKey);
                    artificialEdgeKeysByIncViaPairs.put(key, artificialEdgeKey);
                }
                restriction.actualEdgeKeys.set(j, artificialEdgeKey);
                incomingEdge = GHUtility.getEdgeFromEdgeKey(artificialEdgeKey);
            }
        }
        artificialEdgeKeysByIncViaPairs.forEach((LongIntProcedure) (incViaPair, artificialEdgeKey) -> {
            int incomingEdge = BitUtil.LITTLE.getIntLow(incViaPair);
            int viaEdgeKey = BitUtil.LITTLE.getIntHigh(incViaPair);
            int viaEdge = GHUtility.getEdgeFromEdgeKey(viaEdgeKey);
            int node = baseGraph.getEdgeIteratorStateForKey(viaEdgeKey).getBaseNode();
            // we restrict turning onto the original edge and all artificial edges except the one we created for this in-edge
            // i.e. we force turning onto the artificial edge we created for this in-edge
            for (BooleanEncodedValue turnRestrictionEnc : turnRestrictionEncs)
                restrictTurn(turnRestrictionEnc, incomingEdge, node, viaEdge);
            IntSet artificialEdges = artificialEdgesByEdge.get(viaEdge);
            artificialEdges.forEach((IntProcedure) a -> {
                if (a != GHUtility.getEdgeFromEdgeKey(artificialEdgeKey))
                    for (BooleanEncodedValue turnRestrictionEnc : turnRestrictionEncs)
                        restrictTurn(turnRestrictionEnc, incomingEdge, node, a);
            });
        });
        for (int i = 0; i < internalRestrictions.size(); i++) {
            if (encBits.get(i).cardinality() < 1) continue;
            InternalRestriction restriction = internalRestrictions.get(i);
            if (restriction.getEdgeKeys().size() < 3) {
                IntSet fromEdges = artificialEdgesByEdge.getOrDefault(restriction.getFromEdge(), new IntScatterSet());
                fromEdges.add(restriction.getFromEdge());
                IntSet toEdges = artificialEdgesByEdge.getOrDefault(restriction.getToEdge(), new IntScatterSet());
                toEdges.add(restriction.getToEdge());
                for (int j = 0; j < turnRestrictionEncs.size(); j++) {
                    BooleanEncodedValue turnRestrictionEnc = turnRestrictionEncs.get(j);
                    if (encBits.get(i).get(j)) {
                        fromEdges.forEach((IntProcedure) from -> toEdges.forEach((IntProcedure) to -> {
                            restrictTurn(turnRestrictionEnc, from, restriction.getViaNodes().get(0), to);
                        }));
                    }
                }
            } else {
                int viaEdgeKey = restriction.getActualEdgeKeys().get(restriction.getActualEdgeKeys().size() - 2);
                int viaEdge = GHUtility.getEdgeFromEdgeKey(viaEdgeKey);
                int node = baseGraph.getEdgeIteratorStateForKey(viaEdgeKey).getAdjNode();
                // For via-edge restrictions we deny turning from the from-edge onto the via-edge,
                // but allow turning onto the artificial edge(s) instead (see above). Then we deny
                // turning from the artificial edge onto the to-edge here.
                for (int j = 0; j < turnRestrictionEncs.size(); j++) {
                    BooleanEncodedValue turnRestrictionEnc = turnRestrictionEncs.get(j);
                    if (encBits.get(i).get(j)) {
                        restrictTurn(turnRestrictionEnc, viaEdge, node, restriction.getToEdge());
                        // also restrict the turns to the artificial edges corresponding to the to-edge
                        artificialEdgesByEdge.getOrDefault(restriction.getToEdge(), EMPTY_SET).forEach(
                                (IntProcedure) toEdge -> restrictTurn(turnRestrictionEnc, viaEdge, node, toEdge)
                        );
                    }
                }
            }
        }
    }

    private void disableRedundantRestrictions(List<InternalRestriction> restrictions, List<BitSet> encBits) {
        for (int encIdx = 0; encIdx < turnRestrictionEncs.size(); encIdx++) {
            // first we disable all duplicates
            Set<InternalRestriction> uniqueRestrictions = new HashSet<>();
            for (int i = 0; i < restrictions.size(); i++) {
                if (!encBits.get(i).get(encIdx))
                    continue;
                if (!uniqueRestrictions.add(restrictions.get(i)))
                    encBits.get(i).clear(encIdx);
            }
            // build an index of restrictions to quickly find all restrictions containing a given edge key
            IntObjectScatterMap<List<InternalRestriction>> restrictionsByEdgeKeys = new IntObjectScatterMap<>();
            for (int i = 0; i < restrictions.size(); i++) {
                if (!encBits.get(i).get(encIdx))
                    continue;
                InternalRestriction restriction = restrictions.get(i);
                for (IntCursor edgeKey : restriction.edgeKeys) {
                    int idx = restrictionsByEdgeKeys.indexOf(edgeKey.value);
                    if (idx < 0) {
                        List<InternalRestriction> list = new ArrayList<>();
                        list.add(restriction);
                        restrictionsByEdgeKeys.indexInsert(idx, edgeKey.value, list);
                    } else {
                        restrictionsByEdgeKeys.indexGet(idx).add(restriction);
                    }
                }
            }
            // Only keep restrictions that do not contain another restriction. For example, it would be unnecessary to restrict
            // 6-8-2 when 6-8 is restricted already
            for (int i = 0; i < restrictions.size(); i++) {
                if (!encBits.get(i).get(encIdx))
                    continue;
                if (containsAnotherRestriction(restrictions.get(i), restrictionsByEdgeKeys))
                    encBits.get(i).clear(encIdx);
            }
        }
    }

    private boolean containsAnotherRestriction(InternalRestriction restriction, IntObjectMap<List<InternalRestriction>> restrictionsByEdgeKeys) {
        for (IntCursor edgeKey : restriction.edgeKeys) {
            List<InternalRestriction> restrictionsWithThisEdgeKey = restrictionsByEdgeKeys.get(edgeKey.value);
            for (InternalRestriction r : restrictionsWithThisEdgeKey) {
                if (r == restriction) continue;
                if (r.equals(restriction))
                    throw new IllegalStateException("Equal restrictions should have already been filtered out here!");
                if (isSubsetOf(r.edgeKeys, restriction.edgeKeys))
                    return true;
            }
        }
        return false;
    }

    private static boolean isSubsetOf(IntArrayList candidate, IntArrayList array) {
        if (candidate.size() > array.size())
            return false;
        for (int i = 0; i <= array.size() - candidate.size(); i++) {
            boolean isSubset = true;
            for (int j = 0; j < candidate.size(); j++) {
                if (candidate.get(j) != array.get(i + j)) {
                    isSubset = false;
                    break;
                }
            }
            if (isSubset)
                return true;
        }
        return false;
    }

    private void restrictTurnsBetweenEdges(BooleanEncodedValue turnRestrictionEnc, EdgeIteratorState edgeState, int otherEdge) {
        restrictTurn(turnRestrictionEnc, otherEdge, edgeState.getBaseNode(), edgeState.getEdge());
        restrictTurn(turnRestrictionEnc, edgeState.getEdge(), edgeState.getBaseNode(), otherEdge);
        restrictTurn(turnRestrictionEnc, otherEdge, edgeState.getAdjNode(), edgeState.getEdge());
        restrictTurn(turnRestrictionEnc, edgeState.getEdge(), edgeState.getAdjNode(), otherEdge);
    }

    private InternalRestriction convertToInternal(Restriction restriction) {
        IntArrayList edges = restriction.edges;
        if (edges.size() < 2)
            throw new IllegalArgumentException("Invalid restriction, there must be at least two edges");
        else if (edges.size() == 2) {
            int fromKey = baseGraph.getEdgeIteratorState(edges.get(0), restriction.viaNode).getEdgeKey();
            int toKey = baseGraph.getEdgeIteratorState(edges.get(1), restriction.viaNode).getReverseEdgeKey();
            return new InternalRestriction(IntArrayList.from(restriction.viaNode), IntArrayList.from(fromKey, toKey));
        } else {
            Pair<IntArrayList, IntArrayList> p = findNodesAndEdgeKeys(baseGraph, edges);
            p.first.remove(p.first.size() - 1);
            return new InternalRestriction(p.first, p.second);
        }
    }

    private Pair<IntArrayList, IntArrayList> findNodesAndEdgeKeys(BaseGraph baseGraph, IntArrayList edges) {
        // we get a list of edges and need to find the directions of the edges and the connecting nodes
        List<Pair<IntArrayList, IntArrayList>> solutions = new ArrayList<>();
        findEdgeChain(baseGraph, edges, 0, IntArrayList.from(), IntArrayList.from(), solutions);
        if (solutions.isEmpty()) {
            throw new IllegalArgumentException("Disconnected edges: " + edges + " " + edgesToLocationString(baseGraph, edges));
        } else if (solutions.size() > 1) {
            throw new IllegalArgumentException("Ambiguous edge restriction: " + edges + " " + edgesToLocationString(baseGraph, edges));
        } else {
            return solutions.get(0);
        }
    }

    private static String edgesToLocationString(BaseGraph baseGraph, IntArrayList edges) {
        return Arrays.stream(edges.buffer, 0, edges.size()).mapToObj(e -> baseGraph.getEdgeIteratorState(e, Integer.MIN_VALUE).fetchWayGeometry(FetchMode.ALL))
                .toList().toString();
    }

    private void findEdgeChain(BaseGraph baseGraph, IntArrayList edges, int index, IntArrayList nodes, IntArrayList edgeKeys, List<Pair<IntArrayList, IntArrayList>> solutions) {
        if (index == edges.size()) {
            solutions.add(new Pair<>(new IntArrayList(nodes), new IntArrayList(edgeKeys)));
            return;
        }
        EdgeIteratorState edgeState = baseGraph.getEdgeIteratorState(edges.get(index), Integer.MIN_VALUE);
        if (index == 0 || edgeState.getBaseNode() == nodes.get(nodes.size() - 1)) {
            nodes.add(edgeState.getAdjNode());
            edgeKeys.add(edgeState.getEdgeKey());
            findEdgeChain(baseGraph, edges, index + 1, nodes, edgeKeys, solutions);
            nodes.elementsCount--;
            edgeKeys.elementsCount--;
        }
        if (index == 0 || edgeState.getAdjNode() == nodes.get(nodes.size() - 1)) {
            nodes.add(edgeState.getBaseNode());
            edgeKeys.add(edgeState.getReverseEdgeKey());
            findEdgeChain(baseGraph, edges, index + 1, nodes, edgeKeys, solutions);
            nodes.elementsCount--;
            edgeKeys.elementsCount--;
        }
    }

    private void restrictTurn(BooleanEncodedValue turnRestrictionEnc, int fromEdge, int viaNode, int toEdge) {
        if (fromEdge < 0 || toEdge < 0 || viaNode < 0)
            throw new IllegalArgumentException("from/toEdge and viaNode must be >= 0");
        baseGraph.getTurnCostStorage().set(turnRestrictionEnc, fromEdge, viaNode, toEdge, true);
    }

    public static BitSet copyEncBits(BitSet encBits) {
        return new BitSet(Arrays.copyOf(encBits.bits, encBits.bits.length), encBits.wlen);
    }

    public static class Restriction {
        public final IntArrayList edges;
        private final int viaNode;

        private Restriction(IntArrayList edges, int viaNode) {
            this.edges = edges;
            this.viaNode = viaNode;
        }

        @Override
        public String toString() {
            return "edges: " + edges.toString() + ", viaNode: " + viaNode;
        }
    }

    private static class InternalRestriction {
        private final IntArrayList viaNodes;
        private final IntArrayList edgeKeys;
        private final IntArrayList actualEdgeKeys;

        public InternalRestriction(IntArrayList viaNodes, IntArrayList edgeKeys) {
            this.edgeKeys = edgeKeys;
            this.viaNodes = viaNodes;
            this.actualEdgeKeys = ArrayUtil.constant(edgeKeys.size(), -1);
            this.actualEdgeKeys.set(0, edgeKeys.get(0));
            this.actualEdgeKeys.set(edgeKeys.size() - 1, edgeKeys.get(edgeKeys.size() - 1));
        }

        public IntArrayList getViaNodes() {
            return viaNodes;
        }

        public int getFromEdge() {
            return GHUtility.getEdgeFromEdgeKey(edgeKeys.get(0));
        }

        public IntArrayList getEdgeKeys() {
            return edgeKeys;
        }

        public IntArrayList getActualEdgeKeys() {
            return actualEdgeKeys;
        }

        public int getToEdge() {
            return GHUtility.getEdgeFromEdgeKey(edgeKeys.get(edgeKeys.size() - 1));
        }

        @Override
        public int hashCode() {
            return 31 * viaNodes.hashCode() + edgeKeys.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            // this is actually needed, because we build a Set of InternalRestrictions to remove duplicates
            // no need to compare the actualEdgeKeys
            if (!(obj instanceof InternalRestriction)) return false;
            return ((InternalRestriction) obj).viaNodes.equals(viaNodes) && ((InternalRestriction) obj).edgeKeys.equals(edgeKeys);
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < viaNodes.size(); i++)
                result.append(GHUtility.getEdgeFromEdgeKey(edgeKeys.get(i))).append("-(").append(viaNodes.get(i)).append(")-");
            return result + "" + GHUtility.getEdgeFromEdgeKey(edgeKeys.get(edgeKeys.size() - 1));
        }
    }
}
