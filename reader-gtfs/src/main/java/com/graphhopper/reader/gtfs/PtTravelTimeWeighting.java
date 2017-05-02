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

package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;

class PtTravelTimeWeighting extends AbstractWeighting {

    private final boolean reverse;
    private final double walkSpeedKmH;
    private final int transferFactor;

    PtTravelTimeWeighting(FlagEncoder encoder, double walkSpeedKmH) {
		this(encoder, false, walkSpeedKmH, 1);
    }

    private PtTravelTimeWeighting(FlagEncoder encoder, boolean reverse, double walkSpeedKmH, int transferFactor) {
        super(encoder);
        this.reverse = reverse;
        this.walkSpeedKmH = walkSpeedKmH;
        this.transferFactor = transferFactor;
    }

    PtTravelTimeWeighting reverse() {
        return new PtTravelTimeWeighting(flagEncoder, !reverse, walkSpeedKmH, transferFactor);
    }

    @Override
	public double getMinWeight(double distance) {
		return 0.0;
	}

	@Override
	public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
		throw new RuntimeException("Not supported.");
	}

    @Override
    public long calcMillis(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        GtfsStorage.EdgeType edgeType = ((PtFlagEncoder) getFlagEncoder()).getEdgeType(edge.getFlags());
        switch (edgeType) {
            case HIGHWAY:
                return (long) (getWalkDistance(edge) * 3.6 / walkSpeedKmH) * 1000;
            case ENTER_TIME_EXPANDED_NETWORK:
            case LEAVE_TIME_EXPANDED_NETWORK:
                return 0;
            default:
                return ((PtFlagEncoder) getFlagEncoder()).getTime(edge.getFlags());
        }
    }

	int calcNTransfers(EdgeIteratorState edge) {
        return transferFactor * ((PtFlagEncoder) getFlagEncoder()).getTransfers(edge.getFlags());
	}

    double getWalkDistance(EdgeIteratorState edge) {
        GtfsStorage.EdgeType edgeType = ((PtFlagEncoder) getFlagEncoder()).getEdgeType(edge.getFlags());
        switch (edgeType) {
            case HIGHWAY:
                return edge.getDistance();
            case ENTER_PT:
            case EXIT_PT:
                return 10.0;
            default:
                return 0.0;
        }
    }

    @Override
	public String getName() {
		return "pttraveltime";
	}

}
