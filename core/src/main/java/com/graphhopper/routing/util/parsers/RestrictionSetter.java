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
import com.graphhopper.util.ArrayUtil;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

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
        disableRedundantRestrictions(restrictions, encBits);
        LongIntMap artificialEdgeKeysByIncViaPairs = new LongIntScatterMap();
        IntObjectMap<IntSet> artificialEdgesByEdge = new IntObjectScatterMap<>();
        List<InternalRestriction> internalRestrictions = restrictions.stream().map(this::convert).toList();
        for (int i = 0; i < internalRestrictions.size(); i++) {
            if (encBits.get(i).cardinality() < 1) continue;
            InternalRestriction restriction = internalRestrictions.get(i);
            if (restriction.getViaEdgeKeys().isEmpty())
                continue;
            int incomingEdge = restriction.getFromEdge();
            for (IntCursor c : restriction.getViaEdgeKeys()) {
                int viaEdgeKey = c.value;
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
                restriction.actualViaEdgeKeys.set(c.index, artificialEdgeKey);
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
            if (restriction.getViaEdgeKeys().isEmpty()) {
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
                int viaEdgeKey = restriction.getActualViaEdgeKeys().get(restriction.getActualViaEdgeKeys().size() - 1);
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

    private void disableRedundantRestrictions(List<Restriction> restrictions, List<BitSet> encBits) {
        for (int encIdx = 0; encIdx < turnRestrictionEncs.size(); encIdx++) {
            // first we disable all duplicates
            Set<Restriction> uniqueRestrictions = new HashSet<>();
            for (int i = 0; i < restrictions.size(); i++) {
                if (!encBits.get(i).get(encIdx))
                    continue;
                if (!uniqueRestrictions.add(restrictions.get(i)))
                    encBits.get(i).clear(encIdx);
            }
            // build an index of restrictions to quickly find all restrictions containing a given edge
            IntObjectScatterMap<List<Restriction>> restrictionsByEdges = new IntObjectScatterMap<>();
            for (int i = 0; i < restrictions.size(); i++) {
                if (!encBits.get(i).get(encIdx))
                    continue;
                Restriction restriction = restrictions.get(i);
                for (IntCursor edge : restriction.edges) {
                    if (restriction.edges.size() > 2 && edge.index > 0 && edge.value == restriction.edges.get(edge.index - 1))
                        // If we allowed a restriction like -4-(A)-6-(B)-6-(A)-8- (with nodes A,B and edges 4,6,6,8) it
                        // might falsely be considered 'redundant' by our current logic if there was another restriction 6-6 (via=B)
                        // when only a restriction 6-6 (via A) would make the 4-6-6-8 actually redundant.
                        // ... and since via-edge restrictions with such consecutive duplicates are only imaginary we just throw an error here...
                        throw new IllegalArgumentException("Restrictions with more than two edges must not contain duplicate consecutive edges");
                    int idx = restrictionsByEdges.indexOf(edge.value);
                    if (idx < 0) {
                        List<Restriction> list = new ArrayList<>();
                        list.add(restriction);
                        restrictionsByEdges.indexInsert(idx, edge.value, list);
                    } else {
                        restrictionsByEdges.indexGet(idx).add(restriction);
                    }
                }
            }
            // Only keep restrictions that do not contain another restriction. For example, it would be unnecessary to restrict
            // 6-8-2 when 6-8 is restricted already
            for (int i = 0; i < restrictions.size(); i++) {
                if (!encBits.get(i).get(encIdx))
                    continue;
                if (containsAnotherRestriction(restrictions.get(i), restrictionsByEdges))
                    encBits.get(i).clear(encIdx);
            }
        }
    }

    private boolean containsAnotherRestriction(Restriction restriction, IntObjectMap<List<Restriction>> restrictionsByEdges) {
        if (restriction.edges.size() < 3)
            // Special case for restrictions with two edges (via-node restrictions): If the two edges are connected at
            // both nodes the edges of two such restrictions can be equal without the restrictions being the same.
            // We have to make sure they aren't both excluded because they contain each other! We can just return false
            // here, since such restrictions cannot contain another restriction anyway.
            // For via-edge restrictions (>2 edges) we do not run into this problem, because we already excluded duplicates here.
            return false;
        for (IntCursor edge : restriction.edges) {
            List<Restriction> restrictionsWithThisEdge = restrictionsByEdges.get(edge.value);
            for (Restriction r : restrictionsWithThisEdge) {
                if (r == restriction) continue;
                if (r.equals(restriction))
                    throw new IllegalStateException("Equal restrictions should have already been filtered out here!");
                if (isSubsetOf(r.edges, restriction.edges)) {
                    // super special case: if two consecutive edges share both nodes, the 'direction' of the restriction
                    // could be opposite to the containing restriction -> they aren't really redundant!
                    // Ok, this could be a bit cleaner if we did the conversion to InternalRestriction (where the nodes and edge keys are known) first.
                    for (int i = 1; i < r.edges.size(); i++) {
                        EdgeIteratorState e1 = baseGraph.getEdgeIteratorState(r.edges.get(i - 1), Integer.MIN_VALUE);
                        EdgeIteratorState e2 = baseGraph.getEdgeIteratorState(r.edges.get(i), Integer.MIN_VALUE);
                        if (IntHashSet.from(e1.getBaseNode(), e1.getAdjNode()).equals(IntHashSet.from(e2.getBaseNode(), e2.getAdjNode())))
                            return false;
                    }
                    return true;
                }
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

    private InternalRestriction convert(Restriction restriction) {
        IntArrayList edges = restriction.edges;
        if (edges.size() < 2)
            throw new IllegalArgumentException("Invalid restriction, there must be at least two edges");
        else if (edges.size() == 2)
            return new InternalRestriction(edges.get(0), new IntArrayList(), IntArrayList.from(restriction.viaNode), edges.get(1));
        else {
            Pair<IntArrayList, IntArrayList> p = findNodesAndEdgeKeys(baseGraph, edges);
            p.first.remove(p.first.size() - 1);
            p.second.remove(0);
            p.second.remove(p.second.size() - 1);
            return new InternalRestriction(edges.get(0), p.second, p.first, edges.get(edges.size() - 1));
        }
    }

    private Pair<IntArrayList, IntArrayList> findNodesAndEdgeKeys(BaseGraph baseGraph, IntArrayList edges) {
        // we get a list of edges and need to find the directions of the edges and the connecting nodes
        List<Pair<IntArrayList, IntArrayList>> solutions = new ArrayList<>();
        findEdgeChain(baseGraph, edges, 0, IntArrayList.from(), IntArrayList.from(), solutions);
        if (solutions.isEmpty()) {
            throw new IllegalArgumentException("Disconnected edges: " + edges);
        } else if (solutions.size() > 1) {
            throw new IllegalArgumentException("Ambiguous edge restriction: " + edges);
        } else {
            return solutions.get(0);
        }
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
        public int hashCode() {
            return 31 * viaNode + edges.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            // this is actually needed, because we build a Set of Restrictions to remove duplicates
            if (!(obj instanceof Restriction)) return false;
            return ((Restriction) obj).viaNode == viaNode && ((Restriction) obj).edges.equals(edges);
        }

        @Override
        public String toString() {
            return "edges: " + edges.toString() + ", viaNode: " + viaNode;
        }
    }

    private static class InternalRestriction {
        private final IntArrayList viaNodes;
        private final int fromEdge;
        private final IntArrayList viaEdgeKeys;
        private final IntArrayList actualViaEdgeKeys;
        private final int toEdge;

        public InternalRestriction(int fromEdge, IntArrayList viaEdgeKeys, IntArrayList viaNodes, int toEdge) {
            this.toEdge = toEdge;
            this.viaEdgeKeys = viaEdgeKeys;
            this.actualViaEdgeKeys = ArrayUtil.constant(viaEdgeKeys.size(), -1);
            this.fromEdge = fromEdge;
            this.viaNodes = viaNodes;
        }

        public IntArrayList getViaNodes() {
            return viaNodes;
        }

        public int getFromEdge() {
            return fromEdge;
        }

        public IntArrayList getViaEdgeKeys() {
            return viaEdgeKeys;
        }

        public IntArrayList getActualViaEdgeKeys() {
            return actualViaEdgeKeys;
        }

        public int getToEdge() {
            return toEdge;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(fromEdge + "-");
            for (int i = 0; i < viaNodes.size() - 1; i++)
                result.append("(").append(viaNodes.get(i)).append(")-").append(GHUtility.getEdgeFromEdgeKey(viaEdgeKeys.get(i))).append("-");
            return result + "(" + viaNodes.get(viaNodes.size() - 1) + ")-" + toEdge;
        }
    }
}
