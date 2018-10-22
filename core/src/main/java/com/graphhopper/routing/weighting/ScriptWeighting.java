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
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.lang.reflect.Field;
import java.util.Arrays;

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

    public ScriptWeighting(String name, double maxSpeed, ScriptInterface scriptInterface) {
        this.script = scriptInterface;
        this.name = name;
        if (Helper.isEmpty(name))
            throw new IllegalArgumentException("No 'base' was specified");

        if (maxSpeed < 1)
            throw new IllegalArgumentException("max_speed too low: " + maxSpeed);
        this.maxSpeed = maxSpeed / SPEED_CONV * 1000;
    }

    public ScriptWeighting init(EncodingManager encodingManager) {
        String vehicle = name;
        // TODO deprecated. only used for getFlagEncoder method
        encoder = encodingManager.getEncoder(vehicle);

        accessEnc = encodingManager.getEncodedValue(vehicle + ".access", BooleanEncodedValue.class);
        avSpeedEnc = encodingManager.getEncodedValue(vehicle + ".average_speed", DecimalEncodedValue.class);

        // make same EncodedValues available as variables. At the moment a change here requires a change in FlexResource.createScriptWeighting too
        try {
            for (String key : Arrays.asList(EncodingManager.ROAD_ENV, EncodingManager.ROAD_CLASS, EncodingManager.TOLL)) {
                EncodedValue value = encodingManager.getEncodedValue(key, EncodedValue.class);
                Field field = script.getClass().getDeclaredField(key);
                field.set(script, value);
            }
            return this;
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed;
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
        return time;
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
