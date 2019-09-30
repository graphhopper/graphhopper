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

import com.graphhopper.routing.ev.EncodedValueFactory;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.profiles.FlexConfig;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

public class FlexConfigWeighting extends AbstractWeighting {

    private final double maxSpeed;
    private final double maxPriority;
    private double distanceFactor;
    private FlexConfig.AverageSpeedConfig speedConfig;
    private FlexConfig.DelayFlexConfig delayConfig;
    private FlexConfig.PriorityFlexConfig priorityConfig;

    public FlexConfigWeighting(FlagEncoder encoder, FlexConfig config, EncodedValueFactory factory) {
        super(encoder);
        maxSpeed = config.getMaxSpeed() / FlexConfig.SPEED_CONV;
        EncodedValueLookup lookup = encoder;
        speedConfig = config.createAverageSpeedConfig(lookup, factory);
        delayConfig = config.createDelayConfig(lookup, factory);
        priorityConfig = config.createPriorityConfig(lookup, factory);

        distanceFactor = config.getDistanceFactor();
        if (distanceFactor < 0)
            throw new IllegalArgumentException("distance_factor cannot be negative");

        maxPriority = config.getMaxPriority();
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
        return (seconds + edge.getDistance() * distanceFactor) / priorityConfig.eval(edge, reverse, prevOrNextEdgeId);
    }

    double calcSeconds(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        // special case for loop edges: since they do not have a meaningful direction we always need to read them in forward direction
        if (edge.getBaseNode() == edge.getAdjNode())
            reverse = false;

        // TODO this should be controllable e.g. via speed in the config too
        // => currently we cannot unblock a blocked road but can block an unblocked road via speed == 0
        if (reverse ? !edge.getReverse(accessEnc) : !edge.get(accessEnc))
            return Double.POSITIVE_INFINITY;

        double speed = speedConfig.eval(edge, reverse, prevOrNextEdgeId);
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative");

        double delay = delayConfig.eval(edge, reverse, prevOrNextEdgeId);
        return edge.getDistance() / speed * FlexConfig.SPEED_CONV + delay;
    }

    @Override
    public long calcMillis(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        return Math.round(calcSeconds(edge, reverse, prevOrNextEdgeId) * 1000);
    }

    @Override
    public String getName() {
        return "fastest";
    }
}
