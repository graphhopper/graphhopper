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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PriorityCustomConfig {
    private List<EdgeToValueEntry> priorityList = new ArrayList<>();

    public PriorityCustomConfig(CustomModel customModel, EncodedValueLookup lookup, EncodedValueFactory factory) {
        add(lookup, customModel.getVehicleWeight(), "vehicle_weight", MaxWeight.KEY);
        add(lookup, customModel.getVehicleWidth(), "vehicle_width", MaxWidth.KEY);
        add(lookup, customModel.getVehicleHeight(), "vehicle_height", MaxHeight.KEY);
        add(lookup, customModel.getVehicleLength(), "vehicle_length", MaxLength.KEY);

        for (Map.Entry<String, Object> entry : customModel.getPriority().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                throw new IllegalArgumentException("Missing value for " + key + " in 'priority'");
            } else if (value instanceof Number) {
                if (key.startsWith(GeoToValueEntry.key(""))) {
                    Geometry geometry = GeoToValueEntry.pickGeometry(customModel, key);
                    priorityList.add(new GeoToValueEntry(new PreparedGeometryFactory().create(geometry), ((Number) value).doubleValue(), 1));
                } else {
                    BooleanEncodedValue encodedValue = getEV(lookup, "priority", key, BooleanEncodedValue.class);
                    priorityList.add(new BooleanToValueEntry(encodedValue, ((Number) value).doubleValue(), 1));
                }
            } else if (value instanceof Map) {
                EnumEncodedValue enumEncodedValue = getEV(lookup, "priority", key, EnumEncodedValue.class);
                Class<? extends Enum> enumClass = factory.findValues(key);
                double[] values = Helper.createEnumToDoubleArray("priority." + key, 1, 0, 100,
                        enumClass, (Map<String, Object>) value);
                normalizeFactor(values, 1);
                priorityList.add(new EnumToValueEntry(enumEncodedValue, values));
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for " + key + " in 'priority'");
            }
        }
    }

    static <T extends EncodedValue> T getEV(EncodedValueLookup lookup, String name, String key, Class<T> encodedValueType) {
        if (!lookup.hasEncodedValue(key))
            throw new IllegalArgumentException("Cannot find '" + key + "' specified in '" + name + "'");
        return lookup.getEncodedValue(key, encodedValueType);
    }

    /**
     * Pick the maximum value and if it is greater than the specified max we divide all values with it - i.e. normalize it.
     * <p>
     * The purpose of this method is to avoid factors above max which makes e.g. Weighting.getMinWeight simpler (as maximum priority is 1)
     * and also ensures that the landmark algorithm still works (weight can only increased without effecting optimality).
     */
    private static void normalizeFactor(double[] values, final double max) {
        double tmpMax = max;
        for (int i = 0; i < values.length; i++) {
            tmpMax = Math.max(tmpMax, values[i]);
        }
        if (tmpMax > max)
            for (int i = 0; i < values.length; i++) {
                values[i] = values[i] / tmpMax;
            }
    }

    void add(EncodedValueLookup lookup, Double value, String name, String encValue) {
        if (value == null)
            return;
        if (!lookup.hasEncodedValue(encValue))
            throw new IllegalArgumentException("You cannot use " + name + " as encoded value '" + encValue + "' was not enabled on the server.");
        priorityList.add(new MaxValueEntry(name, lookup.getDecimalEncodedValue(encValue), value));
    }

    /**
     * @return weight without unit. The lower the priority is the higher the weight of the specified edge will be.
     */
    public double calcPriority(EdgeIteratorState edge, boolean reverse) {
        double priority = 1;
        for (int i = 0; i < priorityList.size(); i++) {
            EdgeToValueEntry entry = priorityList.get(i);
            double value = entry.getValue(edge, reverse);
            priority *= value;
            if (priority <= 0)
                return priority;
        }
        return priority;
    }

    private static class MaxValueEntry implements EdgeToValueEntry {
        DecimalEncodedValue ev;
        double vehicleValue;

        public MaxValueEntry(String name, DecimalEncodedValue ev, double vehicleValue) {
            if (vehicleValue <= 0)
                throw new IllegalArgumentException(name + " cannot be 0 or negative");
            this.ev = ev;
            this.vehicleValue = vehicleValue;
        }

        @Override
        public double getValue(EdgeIteratorState iter, boolean reverse) {
            return (vehicleValue <= (reverse ? iter.getReverse(ev) : iter.get(ev))) ? 1 : 0.0;
        }

        @Override
        public String toString() {
            return ev.getName() + ": " + vehicleValue;
        }
    }
}
