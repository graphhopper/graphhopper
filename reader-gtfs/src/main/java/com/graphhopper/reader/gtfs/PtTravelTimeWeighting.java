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

class PtTravelTimeWeighting extends AbstractWeighting {

    private final boolean reverse;

    PtTravelTimeWeighting(FlagEncoder encoder) {
		this(encoder, false);
    }

    private PtTravelTimeWeighting(FlagEncoder encoder, boolean reverse) {
        super(encoder);
        this.reverse = reverse;
    }

    PtTravelTimeWeighting reverse() {
        return new PtTravelTimeWeighting(flagEncoder, !reverse);
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
        throw new RuntimeException("Not supported.");
    }

    @Override
	public String getName() {
		return "pttraveltime";
	}

}
