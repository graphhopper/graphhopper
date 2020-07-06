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
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

public class RoutingCHEdgeIteratorImpl extends RoutingCHEdgeIteratorStateImpl implements RoutingCHEdgeExplorer, RoutingCHEdgeIterator {
    private final EdgeExplorer edgeExplorer;
    private final ShortcutFilter shortcutFilter;
    private EdgeIterator edgeIterator;

    public static RoutingCHEdgeIteratorImpl inEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new RoutingCHEdgeIteratorImpl(edgeExplorer, weighting, ShortcutFilter.inEdges());
    }

    public static RoutingCHEdgeIteratorImpl outEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new RoutingCHEdgeIteratorImpl(edgeExplorer, weighting, ShortcutFilter.outEdges());
    }

    public RoutingCHEdgeIteratorImpl(EdgeExplorer edgeExplorer, Weighting weighting, ShortcutFilter shortcutFilter) {
        super(null, weighting);
        this.edgeExplorer = edgeExplorer;
        this.shortcutFilter = shortcutFilter;
    }

    @Override
    EdgeIteratorState edgeState() {
        return edgeIterator;
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

    @Override
    public String toString() {
        return edgeIterator.toString();
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
