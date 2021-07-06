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

package com.graphhopper.routing;

import java.util.function.IntToDoubleFunction;

public class EdgeRestrictions {
    private final IntToDoubleFunction getEdgePenaltyFrom;
    private final IntToDoubleFunction getGetEdgePenaltyTo;

    public static EdgeRestrictions none() {
        return new EdgeRestrictions(null, null);
    }

    public EdgeRestrictions(IntToDoubleFunction getEdgePenaltyFrom, IntToDoubleFunction getGetEdgePenaltyTo) {
        this.getEdgePenaltyFrom = getEdgePenaltyFrom;
        this.getGetEdgePenaltyTo = getGetEdgePenaltyTo;
    }

    public IntToDoubleFunction getGetEdgePenaltyFrom() {
        return getEdgePenaltyFrom;
    }

    public IntToDoubleFunction getGetGetEdgePenaltyTo() {
        return getGetEdgePenaltyTo;
    }
}
