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
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FlexModel;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.flex.AverageSpeedFlexConfig;
import com.graphhopper.routing.weighting.flex.DelayFlexConfig;
import com.graphhopper.routing.weighting.flex.PriorityFlexConfig;
import com.graphhopper.util.EdgeIteratorState;

public class FlexModelWeighting implements Weighting {

    private FlagEncoder deprecatedFlagEncoder;
    private BooleanEncodedValue baseVehicleProfileAccessEnc;
    private String baseVehicleProfile;

    private final String name;
    private final double maxPriority;
    private double maxSpeed;
    private double distanceFactor;
    private AverageSpeedFlexConfig speedConfig;
    private DelayFlexConfig delayConfig;
    private PriorityFlexConfig priorityConfig;

    public FlexModelWeighting(String name, FlexModel flexModel, FlagEncoder baseFlagEncoder, EncodedValueLookup lookup, EncodedValueFactory factory) {
        this.name = name;
        deprecatedFlagEncoder = baseFlagEncoder;
        baseVehicleProfileAccessEnc = baseFlagEncoder.getAccessEnc();
        baseVehicleProfile = flexModel.getBase();
        maxSpeed = flexModel.getMaxSpeed() / FlexModel.SPEED_CONV;
        if (maxSpeed < 2) {
            maxSpeed = baseFlagEncoder.getMaxSpeed();
            flexModel.setMaxSpeed(maxSpeed);
        }

        speedConfig = new AverageSpeedFlexConfig(flexModel, lookup, factory);
        delayConfig = new DelayFlexConfig(flexModel, lookup, factory);
        priorityConfig = new PriorityFlexConfig(flexModel, lookup, factory);

        distanceFactor = flexModel.getDistanceFactor();
        if (distanceFactor < 0)
            throw new IllegalArgumentException("distance_factor cannot be negative");

        maxPriority = flexModel.getMaxPriority();
        if (maxPriority <= 0)
            throw new IllegalArgumentException("max_priority cannot be zero or negative");
    }

    @Override
    public double getMinWeight(double distance) {
        return (distance / maxSpeed + distance * distanceFactor) / maxPriority;
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        double seconds = calcSeconds(edge, reverse, prevOrNextEdgeId);
        if (Double.isInfinite(seconds))
            return Double.POSITIVE_INFINITY;
        return (seconds + edge.getDistance() * distanceFactor) / priorityConfig.calcPriority(edge, reverse, prevOrNextEdgeId);
    }

    double calcSeconds(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        // special case for loop edges: since they do not have a meaningful direction we always need to read them in forward direction
        if (edge.getBaseNode() == edge.getAdjNode())
            reverse = false;

        // TODO see #1835
        if (reverse ? !edge.getReverse(baseVehicleProfileAccessEnc) : !edge.get(baseVehicleProfileAccessEnc))
            return Double.POSITIVE_INFINITY;

        double speed = speedConfig.calcSpeed(edge, reverse, prevOrNextEdgeId);
        // exit earlier without calculating delay
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative");

        double delay = delayConfig.calcDelay(edge, reverse, prevOrNextEdgeId);
        return edge.getDistance() / speed * FlexModel.SPEED_CONV + delay;
    }

    @Override
    public long calcMillis(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        return Math.round(calcSeconds(edge, reverse, prevOrNextEdgeId) * 1000);
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