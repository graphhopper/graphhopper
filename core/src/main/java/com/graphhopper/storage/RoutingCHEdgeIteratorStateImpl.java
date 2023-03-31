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

package com.graphhopper.storage;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;

public class RoutingCHEdgeIteratorStateImpl implements RoutingCHEdgeIteratorState {
    final CHStorage store;
    final BaseGraph baseGraph;
    private final Weighting weighting;
    private final BooleanEncodedValue accessEnc;
    int edgeId = -1;
    int baseNode;
    int adjNode;
    final BaseGraph.EdgeIteratorStateImpl baseEdgeState;
    long shortcutPointer = -1;

    public RoutingCHEdgeIteratorStateImpl(CHStorage store, BaseGraph baseGraph, BaseGraph.EdgeIteratorStateImpl baseEdgeState, Weighting weighting) {
        this.store = store;
        this.baseGraph = baseGraph;
        this.baseEdgeState = baseEdgeState;
        this.weighting = weighting;
        this.accessEnc = weighting.getFlagEncoder().getAccessEnc();
    }

    boolean init(int edge, int expectedAdjNode) {
        if (edge < 0 || edge >= baseGraph.edgeCount + store.getShortcuts())
            throw new IllegalArgumentException("edge must be in bounds: [0," + (baseGraph.edgeCount + store.getShortcuts()) + "[");
        edgeId = edge;
        if (isShortcut()) {
            shortcutPointer = store.toShortcutPointer(edge - baseGraph.edgeCount);
            baseNode = store.getNodeA(shortcutPointer);
            adjNode = store.getNodeB(shortcutPointer);

            if (expectedAdjNode == adjNode || expectedAdjNode == Integer.MIN_VALUE) {
                return true;
            } else if (expectedAdjNode == baseNode) {
                baseNode = adjNode;
                adjNode = expectedAdjNode;
                return true;
            }
            return false;
        } else {
            return baseEdgeState.init(edge, expectedAdjNode);
        }
    }

    @Override
    public int getEdge() {
        // we maintain this even for base edges, maybe try if not maintaining it is faster
        return edgeId;
    }

    @Override
    public int getOrigEdge() {
        return isShortcut() ? NO_EDGE : edgeState().getEdge();
    }

    @Override
    public int getOrigEdgeFirst() {
        if (!isShortcut() || !store.isEdgeBased())
            return getEdge();
        return store.getOrigEdgeFirst(shortcutPointer);
    }

    @Override
    public int getOrigEdgeLast() {
        if (!isShortcut() || !store.isEdgeBased())
            return getEdge();
        return store.getOrigEdgeLast(shortcutPointer);
    }

    @Override
    public int getBaseNode() {
        return isShortcut() ? baseNode : edgeState().getBaseNode();
    }

    @Override
    public int getAdjNode() {
        return isShortcut() ? adjNode : edgeState().getAdjNode();
    }

    @Override
    public boolean isShortcut() {
        return edgeId >= baseGraph.edgeCount;
    }

    @Override
    public int getSkippedEdge1() {
        checkShortcut(true, "getSkippedEdge1");
        return store.getSkippedEdge1(shortcutPointer);
    }

    @Override
    public int getSkippedEdge2() {
        checkShortcut(true, "getSkippedEdge2");
        return store.getSkippedEdge2(shortcutPointer);
    }

    @Override
    public double getWeight(boolean reverse) {
        if (isShortcut()) {
            return store.getWeight(shortcutPointer);
        } else {
            return getOrigEdgeWeight(reverse, true);
        }
    }

// ORS-GH MOD START add method for TD core routing
    @Override
    public int getTime(boolean reverse, long time) {//FIXME: remove time?
        if (isShortcut()) {
            return store.getTime(shortcutPointer);
        } else {
            return (int) weighting.calcEdgeMillisWithAccess(getBaseGraphEdgeState(), reverse);
        }
    }
// ORS-GH MOD END

    /**
     * @param needWeight if true this method will return as soon as its clear that the weight is finite (no need to
     *                   do the full computation)
     */
    double getOrigEdgeWeight(boolean reverse, boolean needWeight) {
        // todo: for #1835 move the access check into the weighting
        final EdgeIteratorState baseEdge = getBaseGraphEdgeState();
        final boolean access = reverse
                ? baseEdge.getReverse(accessEnc)
                : baseEdge.get(accessEnc);
        if (baseEdge.getBaseNode() != baseEdge.getAdjNode() && !access) {
            return Double.POSITIVE_INFINITY;
        }
        if (!needWeight) {
            return 0;
        }
        return weighting.calcEdgeWeight(baseEdge, reverse);
    }

    public EdgeIteratorState getBaseGraphEdgeState() {
        checkShortcut(false, "getBaseGraphEdgeState");
        return edgeState();
    }

    EdgeIteratorState edgeState() {
        // use this only via this getter method as it might have been overwritten
        return baseEdgeState;
    }

    void checkShortcut(boolean shouldBeShortcut, String methodName) {
        if (isShortcut()) {
            if (!shouldBeShortcut)
                throw new IllegalStateException("Cannot call " + methodName + " on shortcut " + getEdge());
        } else if (shouldBeShortcut)
            throw new IllegalStateException("Method " + methodName + " only for shortcuts " + getEdge());
    }
}
