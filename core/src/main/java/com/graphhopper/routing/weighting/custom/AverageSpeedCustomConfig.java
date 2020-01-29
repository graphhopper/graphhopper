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

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValueFactory;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.graphhopper.routing.weighting.custom.PriorityCustomConfig.pickMax;

public class AverageSpeedCustomConfig {
    private List<ConfigMapEntry> speedFactorList = new ArrayList<>();
    private List<ConfigMapEntry> avgSpeedList = new ArrayList<>();
    private DecimalEncodedValue avgSpeedEnc;
    private CustomModel customModel;
    private double maxSpeedFactor = 1;

    public AverageSpeedCustomConfig(CustomModel customModel, EncodedValueLookup lookup, EncodedValueFactory factory) {
        this.customModel = customModel;
        this.avgSpeedEnc = lookup.getDecimalEncodedValue(EncodingManager.getKey(customModel.getBase(), "average_speed"));
        // do as much as possible outside of the eval method
        for (Map.Entry<String, Object> entry : customModel.getAverageSpeed().entrySet()) {
            Object value = entry.getValue();
            if (!lookup.hasEncodedValue(entry.getKey()))
                throw new IllegalArgumentException("Cannot find '" + entry.getKey() + "' specified in 'average_speed'");

            if (value instanceof Map) {
                EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(entry.getKey(), Enum.class);
                Class<? extends Enum> enumClass = factory.findValues(entry.getKey());
                Double[] values = Helper.createEnumToDoubleArray("average_speed", 0,
                        customModel.getVehicleMaxSpeed(), enumClass, (Map<String, Object>) value);
                maxSpeedFactor = pickMax(values, maxSpeedFactor);
                avgSpeedList.add(new EnumToValue(enumEncodedValue, values));
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'average_speed'");
            }
        }

        for (Map.Entry<String, Object> entry : customModel.getSpeedFactor().entrySet()) {
            if (!lookup.hasEncodedValue(entry.getKey()))
                throw new IllegalArgumentException("Cannot find '" + entry.getKey() + "' specified in 'speed_factor'");
            Object value = entry.getValue();
            if (value instanceof Map) {
                EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(entry.getKey(), Enum.class);
                Class<? extends Enum> enumClass = factory.findValues(entry.getKey());
                Double[] values = Helper.createEnumToDoubleArray("speed_factor", 0, 2, enumClass, (Map<String, Object>) value);
                speedFactorList.add(new EnumToValue(enumEncodedValue, values));
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'speed_factor'");
            }
        }
    }

    public double getMaxSpeedFactor() {
        return maxSpeedFactor;
    }


    /**
     * @return speed in km/h
     */
    public double calcSpeed(EdgeIteratorState edge, boolean reverse) {
        // this code is interpreting the yaml. We could try to make it faster using ANTLR with which we can create AST and compile using janino
        double speed = Double.NaN;
        for (int i = 0; i < avgSpeedList.size(); i++) {
            ConfigMapEntry entry = avgSpeedList.get(i);
            Double value = entry.getValue(edge, reverse);
            // only first matches
            if (value != null) {
                speed = value;
                break;
            }
        }
        if (Double.isNaN(speed))
            speed = reverse ? edge.getReverse(avgSpeedEnc) : edge.get(avgSpeedEnc);
        if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
            throw new IllegalStateException("Invalid average_speed " + speed);

        for (int i = 0; i < speedFactorList.size(); i++) {
            ConfigMapEntry entry = speedFactorList.get(i);
            Double value = entry.getValue(edge, reverse);
            // include all matches
            if (value != null)
                speed *= value;
        }

        return Math.min(speed, customModel.getVehicleMaxSpeed());
    }
}
