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
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Map;

/**
 * Every EncodedValue like road_environment can influence one or more aspects of this Weighting: the
 * speed_factor, the max_speed and the priority. The formula is basically as follows:
 * <pre>
 * if edge is not accessible for base vehicle then return infinity
 * speed = reduce_to_max_speed(estimated_average_speed * multiply_all(speed_factor_map))
 * weight = (toSeconds(distance / speed) + distance * distance_term_constant) / priority;
 * return weight
 * </pre>
 * Please note that the max_speed map is capped to the maximum allowed speed. Also values in the speed_factor and
 * priority maps are normalized via the maximum value to 1 if one of the values is bigger than 1
 * to avoid problems with the landmark algorithm, i.e. the edge weight is always increased and the heuristic always
 * underestimates the weight.
 */
public final class CustomWeighting extends AbstractWeighting {

    /**
     * @return the weighting name from the modelName
     */
    public static String key(String modelName) {
        return "custom_" + modelName;
    }

    /**
     * @return the modelName from the weighting
     */
    public static String modelName(String weighting) {
        return weighting.substring(key("").length());
    }

    private final BooleanEncodedValue baseVehicleProfileAccessEnc;
    private final String baseVehicleProfile;
    private final double maxSpeed;
    private final double minDistanceTerm;
    private final SpeedCustomConfig speedConfig;
    private final PriorityCustomConfig priorityConfig;

    public CustomWeighting(String name, FlagEncoder baseFlagEncoder, EncodedValueLookup lookup,
                           EncodedValueFactory factory, TurnCostProvider turnCostProvider, CustomModel customModel) {
        super(key(name), baseFlagEncoder, turnCostProvider);
        baseVehicleProfileAccessEnc = baseFlagEncoder.getAccessEnc();
        baseVehicleProfile = customModel.getBase();

        speedConfig = new SpeedCustomConfig(baseFlagEncoder.getMaxSpeed(), customModel, lookup, factory);
        maxSpeed = speedConfig.getMaxSpeed() / CustomModel.SPEED_CONV;

        priorityConfig = new PriorityCustomConfig(customModel, lookup, factory);

        minDistanceTerm = customModel.getDistanceTermConstant();
        if (minDistanceTerm < 0)
            throw new IllegalArgumentException("maximum distance_term cannot be negative " + minDistanceTerm);
    }

    /**
     * This method sets the vehicle of the specified request. It uses the importCustomModels if customModel is null.
     */
    public static CustomModel prepareRequest(GHRequest request, CustomModel customModel, Map<String, CustomModel> importCustomModels) {
        HintsMap hints = request.getHints();
        if (hints.getWeighting().startsWith(CustomWeighting.key(""))) {
            if (customModel == null) {
                String modelName = modelName(hints.getWeighting());
                if ((customModel = importCustomModels.get(modelName)) == null)
                    throw new IllegalArgumentException("unknown custom model " + modelName + " (" + hints.getWeighting() + ")");
            }
            request.setVehicle(customModel.getBase());
        }
        return customModel;
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed + distance * minDistanceTerm;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        double distance = edgeState.getDistance();
        double seconds = calcSeconds(distance, edgeState, reverse);
        if (Double.isInfinite(seconds))
            return Double.POSITIVE_INFINITY;
        double distanceInfluence = distance * minDistanceTerm;
        if (Double.isInfinite(distanceInfluence))
            return Double.POSITIVE_INFINITY;
        return (seconds + distanceInfluence) / priorityConfig.calcPriority(edgeState, reverse);
    }

    double calcSeconds(double distance, EdgeIteratorState edge, boolean reverse) {
        // special case for loop edges: since they do not have a meaningful direction we always need to read them in forward direction
        if (edge.getBaseNode() == edge.getAdjNode())
            reverse = false;

        // TODO see #1835
        if (reverse ? !edge.getReverse(baseVehicleProfileAccessEnc) : !edge.get(baseVehicleProfileAccessEnc))
            return Double.POSITIVE_INFINITY;

        double speed = speedConfig.calcSpeed(edge, reverse);
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative");

        return distance / speed * CustomModel.SPEED_CONV;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return Math.round(calcSeconds(edgeState.getDistance(), edgeState, reverse) * 1000);
    }

    @Override
    public boolean matches(HintsMap reqMap) {
        return (reqMap.getWeighting().isEmpty() || getName().equals(reqMap.getWeighting())) &&
                (reqMap.getVehicle().isEmpty() || baseVehicleProfile.equals(reqMap.getVehicle()));
    }

    @Override
    public String toString() {
        return getName();
    }
}