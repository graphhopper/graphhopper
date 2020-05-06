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

package com.graphhopper.routing.ch;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

public class PrepareCHEdgeIteratorImpl implements PrepareCHEdgeExplorer, PrepareCHEdgeIterator {
    private final EdgeExplorer edgeExplorer;
    private final Weighting weighting;
    private final ShortcutFilter shortcutFilter;
    private final BooleanEncodedValue accessEnc;
    private EdgeIterator chIterator;

    public static PrepareCHEdgeExplorer inEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new PrepareCHEdgeIteratorImpl(edgeExplorer, weighting, ShortcutFilter.inEdges());
    }

    public static PrepareCHEdgeExplorer outEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new PrepareCHEdgeIteratorImpl(edgeExplorer, weighting, ShortcutFilter.outEdges());
    }

    public static PrepareCHEdgeExplorer allEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new PrepareCHEdgeIteratorImpl(edgeExplorer, weighting, ShortcutFilter.allEdges());
    }

    public PrepareCHEdgeIteratorImpl(EdgeExplorer edgeExplorer, Weighting weighting, ShortcutFilter shortcutFilter) {
        this.edgeExplorer = edgeExplorer;
        this.weighting = weighting;
        this.shortcutFilter = shortcutFilter;
        accessEnc = weighting.getFlagEncoder().getAccessEnc();
    }

    @Override
    public PrepareCHEdgeIteratorImpl setBaseNode(int node) {
        chIterator = edgeExplorer.setBaseNode(node);
        return this;
    }

    @Override
    public boolean next() {
        assertBaseNodeSet();
        while (true) {
            boolean hasNext = chIterator.next();
            if (!hasNext) {
                return false;
            } else if (hasAccess()) {
                return true;
            }
        }
    }

    private boolean hasAccess() {
        if (isShortcut()) {
            return shortcutFilter.accept((CHEdgeIterator) chIterator);
        } else {
            // c.f. comment in DefaultEdgeFilter
            if (chIterator.getBaseNode() == chIterator.getAdjNode()) {
                return finiteWeight(false) || finiteWeight(true);
            }
            return shortcutFilter.fwd && finiteWeight(false) || shortcutFilter.bwd && finiteWeight(true);
        }
    }

    private boolean finiteWeight(boolean reverse) {
        return !Double.isInfinite(getOrigEdgeWeight(reverse, false));
    }

    /**
     * @param needWeight if true this method will return as soon as its clear that the weight is finite (no need to
     *                   do the full computation)
     */
    private double getOrigEdgeWeight(boolean reverse, boolean needWeight) {
        // todo: for #1776 move the access check into the weighting
        final boolean access = reverse
                ? chIterator.getReverse(accessEnc)
                : chIterator.get(accessEnc);
        if (!access) {
            return Double.POSITIVE_INFINITY;
        }
        if (!needWeight) {
            return 0;
        }
        return weighting.calcEdgeWeight(chIterator, reverse);
    }

    @Override
    public int getEdge() {
        assertBaseNodeSet();
        return chIterator.getEdge();
    }

    @Override
    public int getBaseNode() {
        assertBaseNodeSet();
        return chIterator.getBaseNode();
    }

    @Override
    public int getAdjNode() {
        assertBaseNodeSet();
        return chIterator.getAdjNode();
    }

    @Override
    public int getOrigEdgeFirst() {
        assertBaseNodeSet();
        return chIterator.getOrigEdgeFirst();
    }

    @Override
    public int getOrigEdgeLast() {
        assertBaseNodeSet();
        return chIterator.getOrigEdgeLast();
    }

    @Override
    public boolean isShortcut() {
        assertBaseNodeSet();
        final EdgeIterator iter = chIterator;
        return iter instanceof CHEdgeIterator && ((CHEdgeIterator) iter).isShortcut();
    }

    @Override
    public double getWeight(boolean reverse) {
        if (isShortcut()) {
            return ((CHEdgeIterator) chIterator).getWeight();
        } else {
            assertBaseNodeSet();
            return getOrigEdgeWeight(reverse, true);
        }
    }

    @Override
    public void setWeight(double weight) {
        assertBaseNodeSet();
        ((CHEdgeIterator) chIterator).setWeight(weight);
    }

    @Override
    public String toString() {
        if (chIterator == null) {
            return "not initialized";
        } else {
            return getBaseNode() + "->" + getAdjNode() + " (" + getEdge() + ")";
        }
    }

    @Override
    public int getMergeStatus(int flags) {
        assertBaseNodeSet();
        return ((CHEdgeIterator) chIterator).getMergeStatus(flags);
    }

    @Override
    public void setFlagsAndWeight(int flags, double weight) {
        assertBaseNodeSet();
        ((CHEdgeIterator) chIterator).setFlagsAndWeight(flags, weight);
    }

    @Override
    public void setSkippedEdges(int skippedEdge1, int skippedEdge2) {
        assertBaseNodeSet();
        ((CHEdgeIterator) chIterator).setSkippedEdges(skippedEdge1, skippedEdge2);
    }

    private void assertBaseNodeSet() {
        assert chIterator != null : "You need to call setBaseNode() before using the iterator";
    }

}
