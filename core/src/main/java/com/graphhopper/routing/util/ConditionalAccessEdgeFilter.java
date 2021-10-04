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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.storage.ConditionalEdges;
import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 * @author Andrzej Oles
 */
public class ConditionalAccessEdgeFilter implements EdgeFilter {
    private static int DEFAULT_FILTER_ID = 0;
    private final boolean bwd;
    private final boolean fwd;
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue conditionalEnc;
    /**
     * Used to be able to create non-equal filter instances with equal access encoder and fwd/bwd flags.
     */
    private int filterId;

    private ConditionalAccessEdgeFilter(BooleanEncodedValue accessEnc, BooleanEncodedValue conditionalEnc, boolean fwd, boolean bwd, int filterId) {
        this.accessEnc = accessEnc;
        this.conditionalEnc = conditionalEnc;
        this.fwd = fwd;
        this.bwd = bwd;
        this.filterId = filterId;
    }

    private ConditionalAccessEdgeFilter(FlagEncoder flagEncoder, boolean fwd, boolean bwd, int filterId) {
        this(flagEncoder.getAccessEnc(), flagEncoder.getBooleanEncodedValue(EncodingManager.getKey(flagEncoder, ConditionalEdges.ACCESS)), fwd, bwd, filterId);
    }

    public static ConditionalAccessEdgeFilter outEdges(BooleanEncodedValue accessEnc, BooleanEncodedValue conditionalEnc) {
        return new ConditionalAccessEdgeFilter(accessEnc, conditionalEnc, true, false, DEFAULT_FILTER_ID);
    }

    public static ConditionalAccessEdgeFilter inEdges(BooleanEncodedValue accessEnc, BooleanEncodedValue conditionalEnc) {
        return new ConditionalAccessEdgeFilter(accessEnc, conditionalEnc, false, true, DEFAULT_FILTER_ID);
    }

    /**
     * Accepts all edges that are either forward or backward for the given flag encoder.
     * Edges where neither one of the flags is enabled will still not be accepted. If you need to retrieve all edges
     * regardless of their encoding use {@link EdgeFilter#ALL_EDGES} instead.
     */
    public static ConditionalAccessEdgeFilter allEdges(BooleanEncodedValue accessEnc, BooleanEncodedValue conditionalEnc) {
        return new ConditionalAccessEdgeFilter(accessEnc, conditionalEnc, true, true, DEFAULT_FILTER_ID);
    }

    public static ConditionalAccessEdgeFilter outEdges(FlagEncoder flagEncoder) {
        return new ConditionalAccessEdgeFilter(flagEncoder, true, false, DEFAULT_FILTER_ID);
    }

    public static ConditionalAccessEdgeFilter inEdges(FlagEncoder flagEncoder) {
        return new ConditionalAccessEdgeFilter(flagEncoder, false, true, DEFAULT_FILTER_ID);
    }

    public static ConditionalAccessEdgeFilter allEdges(FlagEncoder flagEncoder) {
        return new ConditionalAccessEdgeFilter(flagEncoder, true, true, DEFAULT_FILTER_ID);
    }

    public ConditionalAccessEdgeFilter setFilterId(int filterId) {
        this.filterId = filterId;
        return this;
    }

    public BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    @Override
    public final boolean accept(EdgeIteratorState iter) {
        return fwd && iter.get(accessEnc) || bwd && iter.getReverse(accessEnc) || fwd && iter.get(conditionalEnc) || bwd && iter.getReverse(conditionalEnc);
    }

    @Override
    public String toString() {
        return accessEnc.toString() + ", " + conditionalEnc.toString() + ", bwd:" + bwd + ", fwd:" + fwd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConditionalAccessEdgeFilter that = (ConditionalAccessEdgeFilter) o;

        if (bwd != that.bwd) return false;
        if (fwd != that.fwd) return false;
        if (filterId != that.filterId) return false;
        return accessEnc.equals(that.accessEnc) && conditionalEnc.equals(that.conditionalEnc);
    }

    @Override
    public int hashCode() {
        int result = (bwd ? 1 : 0);
        result = 31 * result + (fwd ? 1 : 0);
        result = 31 * result + accessEnc.hashCode();
        result = 31 * result + conditionalEnc.hashCode();
        result = 31 * result + filterId;
        return result;
    }
}
