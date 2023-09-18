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
import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 */
public class AccessFilter implements EdgeFilter {
    private final boolean bwd;
    private final boolean fwd;
    private final BooleanEncodedValue accessEnc;

    private AccessFilter(BooleanEncodedValue accessEnc, boolean fwd, boolean bwd) {
        this.accessEnc = accessEnc;
        this.fwd = fwd;
        this.bwd = bwd;
    }

    public static AccessFilter outEdges(BooleanEncodedValue accessEnc) {
        return new AccessFilter(accessEnc, true, false);
    }

    public static AccessFilter inEdges(BooleanEncodedValue accessEnc) {
        return new AccessFilter(accessEnc, false, true);
    }

    /**
     * Accepts all edges that are either forward or backward according to the given accessEnc.
     * Edges where neither one of the flags is enabled will still not be accepted. If you need to retrieve all edges
     * regardless of their encoding use {@link EdgeFilter#ALL_EDGES} instead.
     */
    public static AccessFilter allEdges(BooleanEncodedValue accessEnc) {
        return new AccessFilter(accessEnc, true, true);
    }

    public BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    @Override
    public final boolean accept(EdgeIteratorState iter) {
        return fwd && iter.get(accessEnc) || bwd && iter.getReverse(accessEnc);
    }

    @Override
    public String toString() {
        return accessEnc.toString() + ", bwd:" + bwd + ", fwd:" + fwd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccessFilter that = (AccessFilter) o;

        if (bwd != that.bwd) return false;
        if (fwd != that.fwd) return false;
        return accessEnc.equals(that.accessEnc);
    }

    @Override
    public int hashCode() {
        int result = (bwd ? 1 : 0);
        result = 31 * result + (fwd ? 1 : 0);
        result = 31 * result + accessEnc.hashCode();
        return result;
    }
}
