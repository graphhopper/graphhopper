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
package com.graphhopper.matching;

import com.graphhopper.core.util.EdgeIteratorState;
import java.util.List;

/**
 *
 * @author Peter Karich
 */
public class EdgeMatch {

    private final EdgeIteratorState edgeState;
    private final List<State> states;

    public EdgeMatch(EdgeIteratorState edgeState, List<State> state) {
        this.edgeState = edgeState;

        if (edgeState == null) {
            throw new IllegalStateException("Cannot fetch null EdgeState");
        }

        this.states = state;
        if (this.states == null) {
            throw new IllegalStateException("state list cannot be null");
        }
    }

    public EdgeIteratorState getEdgeState() {
        return edgeState;
    }

    public List<State> getStates() {
        return states;
    }

    @Override
    public String toString() {
        return "edge:" + edgeState + ", states:" + states;
    }
}
