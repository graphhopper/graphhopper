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

import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PriorityCustomConfig {
    private final CustomModel config;
    private List<ConfigMapEntry> priorityList = new ArrayList<>();
    private double maxPriority;

    public PriorityCustomConfig(CustomModel customModel, EncodedValueLookup lookup, EncodedValueFactory factory) {
        this.config = customModel;
        add(lookup, customModel.getVehicleWeight(), "vehicle_weight", MaxWeight.KEY);
        add(lookup, customModel.getVehicleWidth(), "vehicle_width", MaxWidth.KEY);
        add(lookup, customModel.getVehicleHeight(), "vehicle_height", MaxHeight.KEY);
        add(lookup, customModel.getVehicleLength(), "vehicle_length", MaxLength.KEY);

        // default for priority is 1
        maxPriority = 1;
        for (Map.Entry<String, Object> entry : customModel.getPriority().entrySet()) {
            if (!lookup.hasEncodedValue(entry.getKey()))
                throw new IllegalArgumentException("Cannot find '" + entry.getKey() + "' specified in 'priority'");
            Object value = entry.getValue();
            if (value instanceof Map) {
                EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(entry.getKey(), Enum.class);
                Class<? extends Enum> enumClass = factory.findValues(entry.getKey());
                Double[] values = Helper.createEnumToDoubleArray("priority", 0, Double.POSITIVE_INFINITY, enumClass, (Map<String, Object>) value);
                maxPriority = pickMax(values, maxPriority);
                priorityList.add(new EnumToValue(enumEncodedValue, values));
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'priority'");
            }
        }
    }

    static double pickMax(Double[] values, double max) {
        for (Double val : values) {
            max = val == null ? max : Math.max(max, val);
        }
        return max;
    }


    public double getMaxPriority() {
        return maxPriority;
    }

    void add(EncodedValueLookup lookup, Double value, String name, String encValue) {
        if (value == null)
            return;
        if (!lookup.hasEncodedValue(encValue))
            throw new IllegalArgumentException("You cannot use " + name + " as encoded value '" + encValue + "' was not enabled on the server.");
        priorityList.add(new MaxValueConfigMapEntry(name, lookup.getDecimalEncodedValue(encValue), value));
    }

    /**
     * @return weight without unit. The lower the priority is the higher the weight of the specified edge will be.
     */
    public double calcPriority(EdgeIteratorState edge, boolean reverse) {
        double priority = 1;
        for (int i = 0; i < priorityList.size(); i++) {
            ConfigMapEntry entry = priorityList.get(i);
            Double value = entry.getValue(edge, reverse);
            if (value != null) {
                if (value < 0)
                    throw new IllegalStateException("Invalid priority_" + i + ": " + value);
                priority *= value;
            }
        }
        return Math.min(priority, maxPriority);
    }

    private static class MaxValueConfigMapEntry implements ConfigMapEntry {
        DecimalEncodedValue ev;
        double vehicleValue;

        public MaxValueConfigMapEntry(String name, DecimalEncodedValue ev, double vehicleValue) {
            if (vehicleValue < 0)
                throw new IllegalArgumentException(name + " cannot be negative");
            this.ev = ev;
            this.vehicleValue = vehicleValue;
        }

        @Override
        public Double getValue(EdgeIteratorState iter, boolean reverse) {
            return (vehicleValue < (reverse ? iter.getReverse(ev) : iter.get(ev))) ? null : 0.0;
        }

        @Override
        public String toString() {
            return ev.getName() + ": " + vehicleValue;
        }
    }
}
