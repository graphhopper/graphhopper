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

import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

/**
 * This class makes it possible to define the time calculation formula inside a yaml.
 */
public class ScriptWeighting implements Weighting {

    private final static double SPEED_CONV = 3600;
    private final double maxSpeed;
    private final ScriptInterface script;

    private String name;
    private FlagEncoder encoder;
    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue avSpeedEnc;

    public ScriptWeighting(String name, double maxSpeed, ScriptInterface script) {
        this.name = name;
        if (Helper.isEmpty(name))
            throw new IllegalArgumentException("No 'base' was specified");

        if (maxSpeed < 1)
            throw new IllegalArgumentException("max_speed too low: " + maxSpeed);
        this.maxSpeed = maxSpeed / SPEED_CONV;
        this.script = script;
    }

    public ScriptWeighting init(EncodingManager encodingManager) {
        String vehicle = name;
        // TODO deprecated. only used for getFlagEncoder method
        encoder = encodingManager.getEncoder(vehicle);

        accessEnc = encodingManager.getBooleanEncodedValue(EncodingManager.getKey(vehicle, "access"));
        avSpeedEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(vehicle, "average_speed"));

        ScriptInterface.HelperVariables helper = script.getHelperVariables();
        helper.road_environment = encodingManager.getEncodedValue(RoadEnvironment.KEY, EnumEncodedValue.class);
        helper.road_class = encodingManager.getEncodedValue(RoadClass.KEY, EnumEncodedValue.class);
        if (encodingManager.hasEncodedValue(Toll.KEY))
            helper.toll = encodingManager.getEncodedValue(Toll.KEY, IntEncodedValue.class);
        return this;
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed / 1e6;
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        if (reverse) {
            if (!edgeState.getReverse(accessEnc))
                return Double.POSITIVE_INFINITY;
        } else if (!edgeState.get(accessEnc)) {
            return Double.POSITIVE_INFINITY;
        }

        double time = calcMillis(edgeState, reverse, prevOrNextEdgeId);
        return time / 1e6;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        double speed = reverse ? edgeState.getReverse(avSpeedEnc) * 0.9 : edgeState.get(avSpeedEnc) * 0.9;
        if (speed == 0)
            return Long.MAX_VALUE;

        long timeInMillis = (long) (edgeState.getDistance() / speed * SPEED_CONV);
        timeInMillis *= script.getMillisFactor(edgeState, reverse);
        return timeInMillis;
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return encoder;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean matches(HintsMap reqMap) {
        return getName().equals(reqMap.getWeighting());
    }

}
