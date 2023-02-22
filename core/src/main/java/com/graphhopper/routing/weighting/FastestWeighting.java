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

import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TransportationMode;
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
    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
    protected final static double SPEED_CONV = 3.6;
    private final double headingPenalty;
    private final long headingPenaltyMillis;
    private final double maxSpeed;
    private final EnumEncodedValue<RoadAccess> roadAccessEnc;
    // this factor puts a penalty on roads with a "destination"-only or private access, see #733 and #1936
    private final double destinationPenalty, privatePenalty;

    public FastestWeighting(FlagEncoder encoder) {
        this(encoder, new PMap(0));
    }

    public FastestWeighting(FlagEncoder encoder, TurnCostProvider turnCostProvider) {
        this(encoder, new PMap(0), turnCostProvider);
    }

    public FastestWeighting(FlagEncoder encoder, PMap map) {
        this(encoder, map, NO_TURN_COST_PROVIDER);
    }

    public FastestWeighting(FlagEncoder encoder, PMap map, TurnCostProvider turnCostProvider) {
        super(encoder, turnCostProvider);
        headingPenalty = map.getDouble(Routing.HEADING_PENALTY, Routing.DEFAULT_HEADING_PENALTY);
        headingPenaltyMillis = Math.round(headingPenalty * 1000);
        maxSpeed = encoder.getMaxSpeed() / SPEED_CONV;

        if (!encoder.hasEncodedValue(RoadAccess.KEY))
            throw new IllegalArgumentException("road_access is not available but expected for FastestWeighting");

        // ensure that we do not need to change getMinWeight, i.e. road_access_factor >= 1
        double defaultDestinationFactor = encoder.getTransportationMode().isMotorVehicle()? 10 : 1;
        destinationPenalty = checkBounds("road_access_destination_factor", map.getDouble("road_access_destination_factor", defaultDestinationFactor), 1, 10);
        double defaultPrivateFactor = encoder.getTransportationMode().isMotorVehicle()? 10 : 1.2;
        privatePenalty = checkBounds("road_access_private_factor", map.getDouble("road_access_private_factor", defaultPrivateFactor), 1, 10);
        roadAccessEnc = destinationPenalty > 1 || privatePenalty > 1 ? encoder.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class) : null;
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edge, boolean reverse) {
        return calcEdgeWeight(edge, reverse, -1);
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse, long edgeEnterTime) {
        // ORS-GH MOD START: use speed calculator rather than average speed encoder to obtain edge speed
        double speed = speedCalculator.getSpeed(edgeState, reverse, edgeEnterTime);
        // ORS-GH MOD END
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

    @Override
    // ORS-GH MOD START
    //public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse, long edgeEnterTime) {
    // ORS-GH MOD END
        // TODO move this to AbstractWeighting? see #485
        long time = 0;
        boolean unfavoredEdge = edgeState.get(EdgeIteratorState.UNFAVORED_EDGE);
        if (unfavoredEdge)
            time += headingPenaltyMillis;

        // ORS-GH MOD START
        //return time + super.calcEdgeMillis(edgeState, reverse);
        return time + super.calcEdgeMillis(edgeState, reverse, edgeEnterTime);
        // ORS-GH MOD END
    }

    static double checkBounds(String key, double val, double from, double to) {
        if (val < from || val > to)
            throw new IllegalArgumentException(key + " has invalid range should be within [" + from + ", " + to + "]");

        return val;
    }

    // ORS-GH MOD START - additional method for TD routing
    @Override
    public boolean isTimeDependent() {
        return speedCalculator.isTimeDependent();
    }
    // ORS-GH MOD END

    @Override
    public String getName() {
        return "fastest";
    }
}
