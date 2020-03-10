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

import com.graphhopper.GHRequest;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.EncodedValueFactory;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.QueryGraphRequired;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Map;

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
public final class CustomWeighting extends AbstractWeighting implements QueryGraphRequired {
    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
    private final static double SPEED_CONV = 3.6;
    private final BooleanEncodedValue baseVehicleAccessEnc;
    private final String baseVehicle;
    private final double maxSpeed;
    private final double distanceInfluence;
    private final SpeedCustomConfig speedConfig;
    private final PriorityCustomConfig priorityConfig;

    public CustomWeighting(FlagEncoder baseFlagEncoder, Graph storage, EncodedValueLookup lookup,
                           EncodedValueFactory factory, TurnCostProvider turnCostProvider, CustomModel customModel) {
        super(baseFlagEncoder, turnCostProvider);
        if (customModel == null)
            throw new IllegalStateException("CustomModel cannot be null");

        baseVehicleAccessEnc = baseFlagEncoder.getAccessEnc();
        baseVehicle = customModel.getProfile();
        if (!baseVehicle.equals(baseFlagEncoder.toString()))
            throw new IllegalStateException("profile '" + baseVehicle + "' must be identical to encoder " + baseFlagEncoder.toString());

        speedConfig = new SpeedCustomConfig(baseFlagEncoder.getMaxSpeed(), customModel, baseFlagEncoder.getAverageSpeedEnc(), storage, lookup, factory);
        maxSpeed = speedConfig.getMaxSpeed() / SPEED_CONV;

        priorityConfig = new PriorityCustomConfig(customModel, storage, lookup, factory);

        // unit is "seconds per 1km"
        distanceInfluence = customModel.getDistanceInfluence() / 1000;
        if (distanceInfluence < 0)
            throw new IllegalArgumentException("maximum distance_influence cannot be negative " + distanceInfluence);
    }

    @Override
    public CustomWeighting setQueryGraph(QueryGraph queryGraph) {
        speedConfig.setQueryGraph(queryGraph);
        priorityConfig.setQueryGraph(queryGraph);
        return this;
    }

    /**
     * This method sets the vehicle of the specified request using the profiles.
     */
    public static CustomModel prepareRequest(GHRequest request, CustomModel customModel, Map<String, CustomModel> models) {
        if (customModel == null)
            customModel = models.get(request.getProfile());
        else
            request.getHints().put("ch.disable", true);

        if (customModel != null)
            request.setVehicle(customModel.getProfile());

        return customModel;
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
        return seconds / priorityConfig.calcPriority(edgeState, reverse) + distanceInfluence;
    }

    double calcSeconds(double distance, EdgeIteratorState edge, boolean reverse) {
        // special case for loop edges: since they do not have a meaningful direction we always need to read them in forward direction
        if (edge.getBaseNode() == edge.getAdjNode())
            reverse = false;

        // TODO see #1835
        if (reverse ? !edge.getReverse(baseVehicleAccessEnc) : !edge.get(baseVehicleAccessEnc))
            return Double.POSITIVE_INFINITY;

        double speed = speedConfig.calcSpeed(edge, reverse);
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative");

        return distance / speed * SPEED_CONV;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return Math.round(calcSeconds(edgeState.getDistance(), edgeState, reverse) * 1000);
    }

    @Override
    public boolean matches(HintsMap reqMap) {
        return (reqMap.getWeighting().isEmpty() || getName().equals(reqMap.getWeighting())) &&
                (reqMap.getVehicle().isEmpty() || baseVehicle.equals(reqMap.getVehicle()));
    }

    @Override
    public String getName() {
        return "custom";
    }

    @Override
    public String toString() {
        return getName();
    }
}