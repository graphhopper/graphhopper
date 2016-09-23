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
package com.graphhopper.reader.dem;

import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Elevation interpolator for tunnels. Estimates elevations of inner nodes of
 * the tunnel based on elevations of entry/exit nodes of the tunnel.
 *
 * @author Alexey Valikov
 */
public class TunnelElevationInterpolator extends AbstractEdgeElevationInterpolator {

    public TunnelElevationInterpolator(GraphHopperStorage storage,
                    DataFlagEncoder dataFlagEncoder) {
        super(storage, dataFlagEncoder);
    }

    @Override
    protected boolean isInterpolatableEdge(EdgeIteratorState edge) {
        return dataFlagEncoder.isTransportModeTunnel(edge);
    }
}
