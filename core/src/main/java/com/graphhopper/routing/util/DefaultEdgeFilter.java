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
    private BooleanEncodedValue accessEnc;

    /**
     * Creates an edges filter which allows edges that are either accessible in forward or in backward direction.
     */
    public DefaultEdgeFilter(BooleanEncodedValue accessEnc) {
        this(accessEnc, true, true);
    }

    // necessary?
//    public DefaultEdgeFilter(EncodedValueLookup lookup, String prefix) {
//        this(lookup.getBooleanEncodedValue(prefix + "access"), true, true);
//    }

    /**
     * Creates an edges filter which allows edges that are either accessible in forward (if fwd is true) or in backward direction (if bwd is true).
     */
    public DefaultEdgeFilter(BooleanEncodedValue accessEnc, boolean fwd, boolean bwd) {
        this.accessEnc = accessEnc;
        this.fwd = fwd;
        this.bwd = bwd;
    }

    @Override
    public final boolean accept(EdgeIteratorState iter) {
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
        return accessEnc + ", fwd:" + fwd + ", bwd:" + bwd;
    }
}
