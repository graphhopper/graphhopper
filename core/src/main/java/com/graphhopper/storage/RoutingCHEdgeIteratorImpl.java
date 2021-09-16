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

import com.graphhopper.routing.ch.ShortcutFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

public class RoutingCHEdgeIteratorImpl extends RoutingCHEdgeIteratorStateImpl implements RoutingCHEdgeExplorer, RoutingCHEdgeIterator {
    private final BaseGraph.EdgeIteratorImpl baseIterator;
    private final ShortcutFilter shortcutFilter;
    private int nextEdgeId;

    public static RoutingCHEdgeIteratorImpl inEdges(CHStorage chStore, BaseGraph baseGraph, Weighting weighting) {
        return new RoutingCHEdgeIteratorImpl(chStore, baseGraph, weighting, ShortcutFilter.inEdges());
    }

    public static RoutingCHEdgeIteratorImpl outEdges(CHStorage chStore, BaseGraph baseGraph, Weighting weighting) {
        return new RoutingCHEdgeIteratorImpl(chStore, baseGraph, weighting, ShortcutFilter.outEdges());
    }

    public RoutingCHEdgeIteratorImpl(CHStorage chStore, BaseGraph baseGraph, Weighting weighting, ShortcutFilter shortcutFilter) {
        super(chStore, baseGraph, new BaseGraph.EdgeIteratorImpl(baseGraph, EdgeFilter.ALL_EDGES), weighting);
        this.baseIterator = (BaseGraph.EdgeIteratorImpl) super.baseEdgeState;
        this.shortcutFilter = shortcutFilter;
    }

    @Override
    EdgeIteratorState edgeState() {
        return baseIterator;
    }

    @Override
    public RoutingCHEdgeIterator setBaseNode(int baseNode) {
        assert baseGraph.isFrozen();
        // todonow: just?
        //       baseIterator.setBaseNode(baseNode);
        baseIterator.nextEdgeId = baseIterator.edgeId = baseGraph.getEdgeRef(baseNode);
        baseIterator.baseNode = baseNode;

        int lastShortcut = store.getLastShortcut(store.toNodePointer(baseNode));
        nextEdgeId = edgeId = lastShortcut < 0 ? baseIterator.edgeId : baseGraph.edgeCount + lastShortcut;
        return this;
    }

    @Override
    public boolean next() {
        while (true) {
            boolean hasNext = goToNext();
            if (!hasNext) {
                return false;
            } else if (hasAccess()) {
                return true;
            }
        }
    }

    private boolean goToNext() {
        // todo: note that it would be more efficient to separate in/out edges, especially for edge-based where we
        //       do not use bidirectional shortcuts
        while (EdgeIterator.Edge.isValid(nextEdgeId) && nextEdgeId >= baseGraph.edgeCount) {
            edgeId = nextEdgeId;
            shortcutPointer = store.toShortcutPointer(edgeId - baseGraph.edgeCount);
            baseNode = store.getNodeA(shortcutPointer);
            adjNode = store.getNodeB(shortcutPointer);
            nextEdgeId = edgeId - 1;
            reverse = false;
            if (nextEdgeId < baseGraph.edgeCount || store.getNodeA(store.toShortcutPointer(nextEdgeId - baseGraph.edgeCount)) != baseNode)
                nextEdgeId = baseIterator.edgeId;
            // todonow: since we do not filter anything here just remove while loop. or should we filter anything here?
            return true;
        }

        // todonow: just?
        //       baseIterator.next();
        while (true) {
            if (!EdgeIterator.Edge.isValid(baseIterator.nextEdgeId))
                return false;
            baseIterator.goToNext();
            // we update edgeId even when iterating base edges
            edgeId = baseIterator.edgeId;
            // todonow: since we do not filter anything here just remove while loop. or should we filter anything here?
            return true;
        }
    }

    @Override
    public String toString() {
        return baseIterator.toString();
    }

    private boolean hasAccess() {
        if (isShortcut()) {
            return shortcutFilter.accept(this);
        } else {
            // c.f. comment in AccessFilter
            if (baseIterator.getBaseNode() == baseIterator.getAdjNode()) {
                return finiteWeight(false) || finiteWeight(true);
            }
            return shortcutFilter.fwd && finiteWeight(false) || shortcutFilter.bwd && finiteWeight(true);
        }
    }

    private boolean finiteWeight(boolean reverse) {
        return !Double.isInfinite(getOrigEdgeWeight(reverse, false));
    }

}
