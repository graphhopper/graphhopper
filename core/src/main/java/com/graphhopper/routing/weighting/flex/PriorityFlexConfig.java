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
package com.graphhopper.routing.weighting.flex;

import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.FlexModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PriorityFlexConfig {
    private final FlexModel config;
    private List<ConfigMapEntry> priorityList = new ArrayList<>();

    public PriorityFlexConfig(FlexModel flexModel, EncodedValueLookup lookup, EncodedValueFactory factory) {
        this.config = flexModel;
        if (flexModel.getWeight() != null && lookup.hasEncodedValue(MaxWeight.KEY))
            priorityList.add(new MaxValueConfigMapEntry("weight", lookup.getDecimalEncodedValue(MaxWeight.KEY), flexModel.getWeight()));
        if (flexModel.getWidth() != null && lookup.hasEncodedValue(MaxWidth.KEY))
            priorityList.add(new MaxValueConfigMapEntry("width", lookup.getDecimalEncodedValue(MaxWidth.KEY), flexModel.getWidth()));
        if (flexModel.getHeight() != null && lookup.hasEncodedValue(MaxHeight.KEY))
            priorityList.add(new MaxValueConfigMapEntry("height", lookup.getDecimalEncodedValue(MaxHeight.KEY), flexModel.getHeight()));
        if (flexModel.getLength() != null && lookup.hasEncodedValue(MaxLength.KEY))
            priorityList.add(new MaxValueConfigMapEntry("length", lookup.getDecimalEncodedValue(MaxLength.KEY), flexModel.getLength()));

        for (Map.Entry<String, Object> entry : flexModel.getPriority().entrySet()) {
            if (!lookup.hasEncodedValue(entry.getKey()))
                throw new IllegalArgumentException("Cannot find '" + entry.getKey() + "' specified in 'priority'");
            Object value = entry.getValue();
            if (value instanceof Map) {
                EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(entry.getKey(), Enum.class);
                Class<? extends Enum> enumClass = factory.findValues(entry.getKey());
                Double[] values = Helper.createEnumToDoubleArray("priority", 0, enumClass, (Map<String, Object>) value);
                priorityList.add(new EnumToValue(enumEncodedValue, values));
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'priority'");
            }
        }
    }

    /**
     * @return weight without unit. The lower it is the higher the priority of the specified edge should be.
     */
    public double calcPriority(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        double priority = 1;
        for (int i = 0; i < priorityList.size(); i++) {
            ConfigMapEntry entry = priorityList.get(i);
            Double value = entry.getValue(edge, reverse);
            if (value != null)
                priority = priority / Math.max(0, value);
        }
        return Math.min(priority, config.getMaxPriority());
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
    }
}
