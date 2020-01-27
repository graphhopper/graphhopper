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

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.EncodedValueFactory;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.custom.AverageSpeedCustomConfig;
import com.graphhopper.routing.weighting.custom.PriorityCustomConfig;
import com.graphhopper.util.EdgeIteratorState;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;

/**
 * Every EncodedValue like road_environment can influence one or more aspects of this Weighting: the
 * speed_factor, the average_speed and the priority. The formula is basically:
 * <pre>
 * if no access to edge then return infinity
 * speed = pick_first(average_speed_map) * multiply_all(speed_factor_map)
 * distanceInfluence = distance * distanceFactor
 * weight = (toSeconds(distance / speed) + distanceInfluence) / priority;
 * return weight
 * </pre>
 */
public class CustomWeighting implements Weighting {

    private FlagEncoder deprecatedFlagEncoder;
    private BooleanEncodedValue baseVehicleProfileAccessEnc;
    private String baseVehicleProfile;

    private final String name;
    private final double maxPriority;
    private double maxSpeed;
    private double distanceFactor;
    private AverageSpeedCustomConfig speedConfig;
    private PriorityCustomConfig priorityConfig;

    public CustomWeighting(String name, CustomModel customModel, FlagEncoder baseFlagEncoder, EncodedValueLookup lookup, EncodedValueFactory factory) {
        this.name = name;
        deprecatedFlagEncoder = baseFlagEncoder;
        baseVehicleProfileAccessEnc = baseFlagEncoder.getAccessEnc();
        baseVehicleProfile = customModel.getBase();
        if (customModel.getVehicleMaxSpeed() == null || customModel.getVehicleMaxSpeed() < 2)
            customModel.setVehicleMaxSpeed(baseFlagEncoder.getMaxSpeed());

        speedConfig = new AverageSpeedCustomConfig(customModel, lookup, factory);
        maxSpeed = speedConfig.getMaxSpeedFactor() * customModel.getVehicleMaxSpeed() / CustomModel.SPEED_CONV;

        priorityConfig = new PriorityCustomConfig(customModel, lookup, factory);
        maxPriority = priorityConfig.getMax();
        if (maxPriority < 1)
            throw new IllegalArgumentException("maximum priority cannot be smaller than 1 but was " + maxPriority);

        distanceFactor = customModel.getDistanceFactor();
        if (distanceFactor < 0)
            throw new IllegalArgumentException("distance_factor cannot be negative");
    }

    @Override
    public double getMinWeight(double distance) {
        return (distance / maxSpeed + distance * distanceFactor) / maxPriority;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        return calcWeight(edgeState, reverse, NO_EDGE);
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        double distance = edge.getDistance();
        double seconds = calcSeconds(distance, edge, reverse, prevOrNextEdgeId);
        if (Double.isInfinite(seconds))
            return Double.POSITIVE_INFINITY;
        return (seconds + distance * distanceFactor) / priorityConfig.calcPriority(edge, reverse, prevOrNextEdgeId);
    }

    double calcSeconds(double distance, EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        // special case for loop edges: since they do not have a meaningful direction we always need to read them in forward direction
        if (edge.getBaseNode() == edge.getAdjNode())
            reverse = false;

        // TODO see #1835
        if (reverse ? !edge.getReverse(baseVehicleProfileAccessEnc) : !edge.get(baseVehicleProfileAccessEnc))
            return Double.POSITIVE_INFINITY;

        double speed = speedConfig.calcSpeed(edge, reverse, prevOrNextEdgeId);
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative");

        return distance / speed * CustomModel.SPEED_CONV;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return calcMillis(edgeState, reverse, NO_EDGE);
    }

    @Override
    public long calcMillis(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        return Math.round(calcSeconds(edge.getDistance(), edge, reverse, prevOrNextEdgeId) * 1000);
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return deprecatedFlagEncoder;
    }

    @Override
    public boolean matches(HintsMap reqMap) {
        return (reqMap.getWeighting().isEmpty() || getName().equals(reqMap.getWeighting())) &&
                (reqMap.getVehicle().isEmpty() || baseVehicleProfile.equals(reqMap.getVehicle()));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}