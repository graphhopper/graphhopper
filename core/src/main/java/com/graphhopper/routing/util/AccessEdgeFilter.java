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

import static com.graphhopper.storage.ConditionalEdges.ACCESS;

/**
 * Helper class to create an edge explorer accepting restricted edges which are conditionally accessible
 *
 * @author Andrzej Oles
 */
// TODO Refactoring ORS: Check whether the right EncodedValue is used instead of the flagEncoder in AccessFilter.*
public class AccessEdgeFilter {
    public static EdgeFilter outEdges(FlagEncoder flagEncoder) {
        if (hasConditionalAccess(flagEncoder))
            return ConditionalAccessEdgeFilter.outEdges(flagEncoder);
        else
            return AccessFilter.outEdges(flagEncoder.getAccessEnc());
    }

    public static EdgeFilter inEdges(FlagEncoder flagEncoder) {
        if (hasConditionalAccess(flagEncoder))
            return ConditionalAccessEdgeFilter.inEdges(flagEncoder);
        else
            return AccessFilter.inEdges(flagEncoder.getAccessEnc());
    }

    public static EdgeFilter allEdges(FlagEncoder flagEncoder) {
        if (hasConditionalAccess(flagEncoder))
            return ConditionalAccessEdgeFilter.allEdges(flagEncoder);
        else
            return AccessFilter.allEdges(flagEncoder.getAccessEnc());
    }

    private static boolean hasConditionalAccess(FlagEncoder flagEncoder) {
        return flagEncoder.hasEncodedValue(EncodingManager.getKey(flagEncoder, ACCESS));
    }
}
