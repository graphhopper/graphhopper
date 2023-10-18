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
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

/**
 * This EdgeFilter combines the weighting result and the 'subnetwork' EncodedValue to consider the subnetwork removal
 * in LocationIndex lookup. In future the 'subnetwork' EncodedValue could be moved into the Weighting.
 */
public class DefaultSnapFilter implements EdgeFilter {
    private final Weighting weighting;
    private final BooleanEncodedValue inSubnetworkEnc;

    public DefaultSnapFilter(Weighting weighting, BooleanEncodedValue inSubnetworkEnc) {
        this.weighting = weighting;
        this.inSubnetworkEnc = inSubnetworkEnc;
    }

    @Override
    public boolean accept(EdgeIteratorState edgeState) {
        return !edgeState.get(inSubnetworkEnc) && (Double.isFinite(weighting.calcEdgeWeight(edgeState, false)) ||
                Double.isFinite(weighting.calcEdgeWeight(edgeState, true)));
    }
}
