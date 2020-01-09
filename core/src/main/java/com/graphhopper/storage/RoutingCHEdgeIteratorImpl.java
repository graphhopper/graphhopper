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
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

public class RoutingCHEdgeIteratorImpl implements RoutingCHEdgeExplorer, RoutingCHEdgeIterator {
    private final EdgeExplorer edgeExplorer;
    private final ShortcutFilter shortcutFilter;
    private final Weighting weighting;
    private final BooleanEncodedValue accessEnc;
    private EdgeIterator edgeIterator;

    public static RoutingCHEdgeIteratorImpl inEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new RoutingCHEdgeIteratorImpl(edgeExplorer, weighting, ShortcutFilter.inEdges());
    }

    public static RoutingCHEdgeIteratorImpl outEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new RoutingCHEdgeIteratorImpl(edgeExplorer, weighting, ShortcutFilter.outEdges());
    }

    public static RoutingCHEdgeIteratorImpl allEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new RoutingCHEdgeIteratorImpl(edgeExplorer, weighting, ShortcutFilter.allEdges());
    }

    public RoutingCHEdgeIteratorImpl(EdgeExplorer edgeExplorer, Weighting weighting, ShortcutFilter shortcutFilter) {
        this.edgeExplorer = edgeExplorer;
        this.shortcutFilter = shortcutFilter;
        this.weighting = weighting;
        accessEnc = weighting.getFlagEncoder().getAccessEnc();
    }

    @Override
    public EdgeIteratorState getBaseGraphEdgeState() {
        if (isShortcut()) {
            throw new IllegalStateException("Base edge can only be obtained for original edges, was: " + edgeIterator);
        }
        return edgeIterator;
    }

    @Override
    public int getEdge() {
        return edgeIterator.getEdge();
    }

    @Override
    public int getOrigEdgeFirst() {
        return edgeIterator.getOrigEdgeFirst();
    }

    @Override
    public int getOrigEdgeLast() {
        return edgeIterator.getOrigEdgeLast();
    }

    @Override
    public int getBaseNode() {
        return edgeIterator.getBaseNode();
    }

    @Override
    public int getAdjNode() {
        return edgeIterator.getAdjNode();
    }

    @Override
    public boolean isShortcut() {
        return (edgeIterator instanceof CHEdgeIteratorState) && ((CHEdgeIteratorState) edgeIterator).isShortcut();
    }

    @Override
    public int getSkippedEdge1() {
        return ((CHEdgeIteratorState) edgeIterator).getSkippedEdge1();
    }

    @Override
    public int getSkippedEdge2() {
        return ((CHEdgeIteratorState) edgeIterator).getSkippedEdge2();
    }

    @Override
    public double getWeight(boolean reverse) {
        if (isShortcut()) {
            return ((CHEdgeIteratorState) edgeIterator).getWeight();
        } else {
            return getOrigEdgeWeight(reverse, true);
        }
    }

    /**
     * @param needWeight if true this method will return as soon as its clear that the weight is finite (no need to
     *                   do the full computation)
     */
    double getOrigEdgeWeight(boolean reverse, boolean needWeight) {
        // todo: for #1776 move the access check into the weighting
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
        return weighting.calcWeight(baseEdge, reverse, EdgeIterator.NO_EDGE);
    }

    @Override
    public RoutingCHEdgeIterator setBaseNode(int baseNode) {
        edgeIterator = edgeExplorer.setBaseNode(baseNode);
        return this;
    }

    @Override
    public boolean next() {
        while (true) {
            boolean hasNext = edgeIterator.next();
            if (!hasNext) {
                return false;
            } else if (hasAccess()) {
                return true;
            }
        }
    }

    private boolean hasAccess() {
        if (isShortcut()) {
            return shortcutFilter.accept((CHEdgeIteratorState) edgeIterator);
        } else {
            // c.f. comment in DefaultEdgeFilter
            if (edgeIterator.getBaseNode() == edgeIterator.getAdjNode()) {
                return finiteWeight(false) || finiteWeight(true);
            }
            return shortcutFilter.fwd && finiteWeight(false) || shortcutFilter.bwd && finiteWeight(true);
        }
    }

    private boolean finiteWeight(boolean reverse) {
        return !Double.isInfinite(getOrigEdgeWeight(reverse, false));
    }

}
