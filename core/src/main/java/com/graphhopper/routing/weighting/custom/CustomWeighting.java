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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Every EncodedValue like road_environment can influence one or more aspects of this Weighting: the
 * speed_factor, the max_speed and the priority. The formula is as follows:
 * <pre>
 * if the edge is not accessible for the base vehicle then return 'infinity'
 * speed = reduce_to_max_speed(estimated_average_speed * multiply_all(speed_factor_map))
 * weight = toSeconds(distance / speed) / multiply_all(priority_map) + distance * distance_influence;
 * return weight
 * </pre>
 * Please note that the max_speed map is capped to the maximum allowed speed. Also values in the speed_factor and
 * priority maps are normalized via the maximum value to 1 if one of the values is bigger than 1
 * to avoid problems with the landmark algorithm, i.e. the edge weight is always increased so that the heuristic always
 * underestimates the weight.
 */
public final class CustomWeighting extends AbstractWeighting {
    public static final String NAME = "custom";
    public static final String CATCH_ALL = "*";

    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
    private final static double SPEED_CONV = 3.6;
    private final BooleanEncodedValue baseVehicleAccessEnc;
    private final double maxSpeed;
    private final double distanceInfluence;
    private final double headingPenaltySeconds;
    private final SpeedCalculator speedCalculator;
    private final PriorityCalculator priorityCalculator;

    public CustomWeighting(FlagEncoder baseFlagEncoder, EncodedValueLookup lookup,
                           TurnCostProvider turnCostProvider, CustomModel customModel) {
        super(baseFlagEncoder, turnCostProvider);
        if (customModel == null)
            throw new IllegalStateException("CustomModel cannot be null");

        headingPenaltySeconds = customModel.getHeadingPenalty();
        baseVehicleAccessEnc = baseFlagEncoder.getAccessEnc();
        speedCalculator = new SpeedCalculator(baseFlagEncoder.getMaxSpeed(), customModel, baseFlagEncoder.getAverageSpeedEnc(), lookup);
        maxSpeed = speedCalculator.getMaxSpeed() / SPEED_CONV;

        priorityCalculator = new PriorityCalculator(customModel, lookup);

        // unit is "seconds per 1km"
        distanceInfluence = customModel.getDistanceInfluence() / 1000;
        if (distanceInfluence < 0)
            throw new IllegalArgumentException("maximum distance_influence cannot be negative " + distanceInfluence);
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed + distance * distanceInfluence;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        double distance = edgeState.getDistance();
        double seconds = calcSeconds(distance, edgeState, reverse);
        if (Double.isInfinite(seconds))
            return Double.POSITIVE_INFINITY;
        double distanceInfluence = distance * this.distanceInfluence;
        if (Double.isInfinite(distanceInfluence))
            return Double.POSITIVE_INFINITY;
        return seconds / priorityCalculator.calcPriority(edgeState, reverse) + distanceInfluence;
    }

    double calcSeconds(double distance, EdgeIteratorState edgeState, boolean reverse) {
        // special case for loop edges: since they do not have a meaningful direction we always need to read them in forward direction
        if (edgeState.getBaseNode() == edgeState.getAdjNode())
            reverse = false;

        // TODO see #1835
        if (reverse ? !edgeState.getReverse(baseVehicleAccessEnc) : !edgeState.get(baseVehicleAccessEnc))
            return Double.POSITIVE_INFINITY;

        double speed = speedCalculator.calcSpeed(edgeState, reverse);
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative");

        double seconds = distance / speed * SPEED_CONV;
        // add penalty at start/stop/via points
        return edgeState.get(EdgeIteratorState.UNFAVORED_EDGE) ? seconds + headingPenaltySeconds : seconds;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return Math.round(calcSeconds(edgeState.getDistance(), edgeState, reverse) * 1000);
    }

    @Override
    public String getName() {
        return NAME;
    }
}