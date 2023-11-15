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
package com.graphhopper.routing.weighting;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters.Routing;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;

/**
 * Calculates the fastest route with the specified vehicle (VehicleEncoder). Calculates the weight
 * in seconds.
 * <p>
 *
 * @author Peter Karich
 */
public class FastestWeighting extends AbstractWeighting {
    public static String DESTINATION_FACTOR = "road_access_destination_factor";
    public static String PRIVATE_FACTOR = "road_access_private_factor";
    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
    protected final static double SPEED_CONV = 3.6;
    private final double headingPenalty;
    private final double maxSpeed;
    private final EnumEncodedValue<RoadAccess> roadAccessEnc;
    // this factor puts a penalty on roads with a "destination"-only or private access, see #733 and #1936
    private final double destinationPenalty, privatePenalty;

    public FastestWeighting(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        this(accessEnc, speedEnc, NO_TURN_COST_PROVIDER);
    }

    public FastestWeighting(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, TurnCostProvider turnCostProvider) {
        this(accessEnc, speedEnc, null, new PMap(0), turnCostProvider);
    }

    public FastestWeighting(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, EnumEncodedValue<RoadAccess> roadAccessEnc, PMap map, TurnCostProvider turnCostProvider) {
        super(accessEnc, speedEnc, turnCostProvider);
        headingPenalty = map.getDouble(Routing.HEADING_PENALTY, Routing.DEFAULT_HEADING_PENALTY);
        maxSpeed = speedEnc.getMaxOrMaxStorableDecimal() / SPEED_CONV;

        destinationPenalty = map.getDouble(DESTINATION_FACTOR, 1);
        privatePenalty = map.getDouble(PRIVATE_FACTOR, 1);
        // ensure that we do not need to change getMinWeight, i.e. both factors need to be >= 1
        checkBounds(DESTINATION_FACTOR, destinationPenalty, 1, 10);
        checkBounds(PRIVATE_FACTOR, privatePenalty, 1, 10);
        if (destinationPenalty > 1 || privatePenalty > 1) {
            if (roadAccessEnc == null)
                throw new IllegalArgumentException("road_access must not be null when destination or private penalties are > 1");
            this.roadAccessEnc = roadAccessEnc;
        } else
            this.roadAccessEnc = null;
    }

    @Override
    public double calcMinWeight(double distance) {
        return distance / maxSpeed;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        if (reverse ? !edgeState.getReverse(accessEnc) : !edgeState.get(accessEnc))
            return Double.POSITIVE_INFINITY;
        double speed = reverse ? edgeState.getReverse(speedEnc) : edgeState.get(speedEnc);
        if (speed == 0)
            return Double.POSITIVE_INFINITY;

        double time = edgeState.getDistance() / speed * SPEED_CONV;
        if (roadAccessEnc != null) {
            RoadAccess access = edgeState.get(roadAccessEnc);
            if (access == RoadAccess.DESTINATION)
                time *= destinationPenalty;
            else if (access == RoadAccess.PRIVATE)
                time *= privatePenalty;
        }
        // add direction penalties at start/stop/via points
        boolean unfavoredEdge = edgeState.get(EdgeIteratorState.UNFAVORED_EDGE);
        if (unfavoredEdge)
            time += headingPenalty;

        return time;
    }

    static double checkBounds(String key, double val, double from, double to) {
        if (val < from || val > to)
            throw new IllegalArgumentException(key + " has invalid range should be within [" + from + ", " + to + "]");

        return val;
    }

    @Override
    public String getName() {
        return "fastest";
    }
}
