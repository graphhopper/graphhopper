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

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 */
public class DefaultEdgeFilter implements EdgeFilter {
    private final boolean bwd;
    private final boolean fwd;
    protected final BooleanEncodedValue accessEnc;

    protected DefaultEdgeFilter(BooleanEncodedValue accessEnc, boolean fwd, boolean bwd) {
        this.accessEnc = accessEnc;
        this.fwd = fwd;
        this.bwd = bwd;
    }

    public static DefaultEdgeFilter outEdges(BooleanEncodedValue accessEnc) {
        return new DefaultEdgeFilter(accessEnc, true, false);
    }

    public static DefaultEdgeFilter outEdges(FlagEncoder flagEncoder) {
        return new DefaultEdgeFilter(flagEncoder.getAccessEnc(), true, false);
    }

    public static DefaultEdgeFilter inEdges(FlagEncoder flagEncoder) {
        return new DefaultEdgeFilter(flagEncoder.getAccessEnc(), false, true);
    }

    /**
     * Accepts all edges that are either forward or backward for the given flag encoder.
     * Edges where neither one of the flags is enabled will still not be accepted. If you need to retrieve all edges
     * regardless of their encoding use {@link EdgeFilter#ALL_EDGES} instead.
     */
    public static DefaultEdgeFilter allEdges(FlagEncoder flagEncoder) {
        return new DefaultEdgeFilter(flagEncoder.getAccessEnc(), true, true);
    }

    @Override
    public final boolean accept(EdgeIteratorState iter) {
        if (iter.getBaseNode() == iter.getAdjNode()) {
            // this is needed for edge-based CH, see #1525
            // background: we need to explicitly accept shortcut edges that are loops, because if we insert a loop
            // shortcut with the fwd flag a DefaultEdgeFilter with bwd=true and fwd=false does not find it, although
            // it is also an 'incoming' edge.
            return iter.get(accessEnc) || iter.getReverse(accessEnc);
        }
        return fwd && iter.get(accessEnc) || bwd && iter.getReverse(accessEnc);
    }

    public boolean acceptsBackward() {
        return bwd;
    }

    public boolean acceptsForward() {
        return fwd;
    }

    @Override
    public String toString() {
        return accessEnc.toString() + ", bwd:" + bwd + ", fwd:" + fwd;
    }
}
