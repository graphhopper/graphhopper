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

import com.graphhopper.util.CHEdgeIteratorState;

public class ShortcutFilter {
    public final boolean fwd;
    public final boolean bwd;

    private ShortcutFilter(boolean fwd, boolean bwd) {
        this.fwd = fwd;
        this.bwd = bwd;
    }

    public static ShortcutFilter outEdges() {
        return new ShortcutFilter(true, false);
    }

    public static ShortcutFilter inEdges() {
        return new ShortcutFilter(false, true);
    }

    public static ShortcutFilter allEdges() {
        return new ShortcutFilter(true, true);
    }

    public boolean accept(CHEdgeIteratorState edgeState) {
        // c.f. comment in AccessFilter
        if (edgeState.getBaseNode() == edgeState.getAdjNode()) {
            return edgeState.getFwdAccess() || edgeState.getBwdAccess();
        }
        return fwd && edgeState.getFwdAccess() || bwd && edgeState.getBwdAccess();
    }
}
