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

import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 */
public class DefaultEdgeFilter implements EdgeFilter {
    private final boolean bwd;
    private final boolean fwd;
    private FlagEncoder encoder;

    /**
     * Creates an edges filter which accepts both direction of the specified vehicle.
     */
    public DefaultEdgeFilter(FlagEncoder encoder) {
        this(encoder, true, true);
    }

    public DefaultEdgeFilter(FlagEncoder encoder, boolean bwd, boolean fwd) {
        this.encoder = encoder;
        this.bwd = bwd;
        this.fwd = fwd;
    }

    @Override
    public final boolean accept(EdgeIteratorState iter) {
        return fwd && iter.isForward(encoder) || bwd && iter.isBackward(encoder);
    }

    public boolean acceptsBackward() {
        return bwd;
    }

    public boolean acceptsForward() {
        return fwd;
    }

    @Override
    public String toString() {
        return encoder.toString() + ", bwd:" + bwd + ", fwd:" + fwd;
    }
}
