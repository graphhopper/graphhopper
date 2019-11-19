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
package com.graphhopper.routing.util;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 */
public class WeightedEdgeFilter implements EdgeFilter {
    private static int DEFAULT_FILTER_ID = 0;
    private final boolean bwd;
    private final boolean fwd;
    private final Weighting weighting;
    /**
     * Used to be able to create non-equal filter instances with equal access encoder and fwd/bwd flags.
     */
    private int filterId;

    private WeightedEdgeFilter(Weighting weighting, boolean fwd, boolean bwd, int filterId) {
        this.weighting = weighting;
        this.fwd = fwd;
        this.bwd = bwd;
        this.filterId = filterId;
    }

    public static WeightedEdgeFilter allEdges(Weighting weighting) {
        return new WeightedEdgeFilter(weighting, true, true, DEFAULT_FILTER_ID);
    }

    public static WeightedEdgeFilter outEdges(Weighting weighting) {
        return new WeightedEdgeFilter(weighting, true, false, DEFAULT_FILTER_ID);
    }

    public static WeightedEdgeFilter inEdges(Weighting weighting) {
        return new WeightedEdgeFilter(weighting, false, true, DEFAULT_FILTER_ID);
    }

    public WeightedEdgeFilter setFilterId(int filterId) {
        this.filterId = filterId;
        return this;
    }

    @Override
    public final boolean accept(EdgeIteratorState iter) {
        if (iter.getBaseNode() == iter.getAdjNode()) {
            // this is needed for edge-based CH, see #1525
            // background: we need to explicitly accept shortcut edges that are loops, because if we insert a loop
            // shortcut with the fwd flag a DefaultEdgeFilter with bwd=true and fwd=false does not find it, although
            // it is also an 'incoming' edge.
            return !Double.isInfinite(weighting.calcWeight(iter, false, EdgeIterator.NO_EDGE))
                    || !Double.isInfinite(weighting.calcWeight(iter, true, EdgeIterator.NO_EDGE));
        }
        return fwd && !Double.isInfinite(weighting.calcWeight(iter, false, EdgeIterator.NO_EDGE))
                || bwd && !Double.isInfinite(weighting.calcWeight(iter, true, EdgeIterator.NO_EDGE));
    }

    @Override
    public String toString() {
        return weighting.toString() + ", bwd:" + bwd + ", fwd:" + fwd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WeightedEdgeFilter that = (WeightedEdgeFilter) o;

        if (bwd != that.bwd) return false;
        if (fwd != that.fwd) return false;
        if (filterId != that.filterId) return false;
        return weighting.equals(that.weighting);
    }

    @Override
    public int hashCode() {
        int result = (bwd ? 1 : 0);
        result = 31 * result + (fwd ? 1 : 0);
        result = 31 * result + weighting.hashCode();
        result = 31 * result + filterId;
        return result;
    }
}
