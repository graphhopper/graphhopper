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

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

public class PrepareCHEdgeIterator implements PrepareCHEdgeExplorer {
    private final EdgeExplorer edgeExplorer;
    private final Weighting weighting;
    private final DefaultEdgeFilter shortcutFilter;
    private final boolean fwd;
    private final boolean bwd;
    private EdgeIterator chIterator;

    public static PrepareCHEdgeIterator inEdgeIterator(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new PrepareCHEdgeIterator(edgeExplorer, weighting, DefaultEdgeFilter.inEdges(weighting.getFlagEncoder().getAccessEnc()), false, true);
    }

    public static PrepareCHEdgeIterator outEdgeIterator(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new PrepareCHEdgeIterator(edgeExplorer, weighting, DefaultEdgeFilter.outEdges(weighting.getFlagEncoder().getAccessEnc()), true, false);
    }

    public static PrepareCHEdgeIterator allEdgeIterator(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new PrepareCHEdgeIterator(edgeExplorer, weighting, DefaultEdgeFilter.allEdges(weighting.getFlagEncoder().getAccessEnc()), true, true);
    }

    private PrepareCHEdgeIterator(EdgeExplorer edgeExplorer, Weighting weighting, DefaultEdgeFilter shortcutFilter, boolean fwd, boolean bwd) {
        this.edgeExplorer = edgeExplorer;
        this.weighting = weighting;
        this.shortcutFilter = shortcutFilter;
        this.fwd = fwd;
        this.bwd = bwd;
    }

    @Override
    public PrepareCHEdgeIterator setBaseNode(int node) {
        chIterator = edgeExplorer.setBaseNode(node);
        return this;
    }

    public boolean next() {
        while (true) {
            final EdgeIterator iter = iter();
            final boolean hasNext = iter.next();
            if ((fwd && !Double.isInfinite(getWeight(false))) || (bwd && !Double.isInfinite(getWeight(true)))) {
                return hasNext;
            } else if (!hasNext) {
                return false;
            }
        }
    }

    public int getEdge() {
        return iter().getEdge();
    }

    public int getBaseNode() {
        return iter().getBaseNode();
    }

    public int getAdjNode() {
        return iter().getAdjNode();
    }

    public int getOrigEdgeFirst() {
        return iter().getOrigEdgeFirst();
    }

    public int getOrigEdgeLast() {
        return iter().getOrigEdgeLast();
    }

    public boolean isShortcut() {
        final EdgeIterator iter = iter();
        return iter instanceof CHEdgeIterator && ((CHEdgeIterator) iter).isShortcut();
    }

    public double getWeight(boolean reverse) {
        if (isShortcut()) {
            final CHEdgeIterator iter = chIter();
            // currently we still need to use the edge filter, because we maintain access flags for CH shortcuts
            if (!shortcutFilter.accept(iter)) {
                return Double.POSITIVE_INFINITY;
            } else {
                return iter.getWeight();
            }
        } else {
            // early exit if access is blocked, will be moved into weighting(s) in the future
            final BooleanEncodedValue accessEnc = weighting.getFlagEncoder().getAccessEnc();
            final boolean access = reverse ? iter().getReverse(accessEnc) : iter().get(accessEnc);
            // have to accept loops here, c.f. comments in DefaultEdgeFilter
            if (!access && iter().getBaseNode() != iter().getAdjNode()) {
                return Double.POSITIVE_INFINITY;
            }
            return weighting.calcWeight(iter(), reverse, EdgeIterator.NO_EDGE);
        }
    }

    public void setWeight(double weight) {
        chIter().setWeight(weight);
    }

    @Override
    public String toString() {
        if (chIterator == null) {
            return "not initialized";
        } else {
            return getBaseNode() + "->" + getAdjNode() + " (" + getEdge() + ")";
        }
    }

    int getMergeStatus(int flags) {
        return chIter().getMergeStatus(flags);
    }

    void setFlagsAndWeight(int flags, double weight) {
        chIter().setFlagsAndWeight(flags, weight);
    }

    void setSkippedEdges(int skippedEdge1, int skippedEdge2) {
        chIter().setSkippedEdges(skippedEdge1, skippedEdge2);
    }

    private EdgeIterator iter() {
        if (chIterator == null) {
            throw new IllegalStateException("You need to call setBaseNode() first");
        }
        return chIterator;
    }

    private CHEdgeIterator chIter() {
        EdgeIterator iter = iter();
        if (!(iter instanceof CHEdgeIterator)) {
            throw new IllegalStateException("Expected a CH edge iterator, but was: " + iter.getClass().getSimpleName());
        }
        return (CHEdgeIterator) iter;
    }
}
