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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class PriorityCalculator {
    private final List<EdgeToValueEntry> priorityList = new ArrayList<>();

    public PriorityCalculator(CustomModel customModel, EncodedValueLookup lookup) {
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
                if (key.startsWith(GeoToValueEntry.AREA_PREFIX)) {
                    Geometry geometry = GeoToValueEntry.pickGeometry(customModel, key);
                    priorityList.add(new GeoToValueEntry(new PreparedGeometryFactory().create(geometry), ((Number) value).doubleValue(), 1));
                } else {
                    throw new IllegalArgumentException("encoded value requires a value or range not a number: " + value);
                }
            } else if (value instanceof Map) {
                // TODO NOW check values of number like we do for area!
                EncodedValue encodedValue = getEV(lookup, "priority", key);
                if (encodedValue instanceof EnumEncodedValue) {
                    EnumEncodedValue enumEncodedValue = (EnumEncodedValue) encodedValue;
                    Enum[] enumValues = enumEncodedValue.getValues();
                    // TODO NOW: move this method to EnumToValueEntry
                    double[] values = createEnumToDoubleArray("priority." + key, 1, 0, 100,
                            enumValues, (Map<String, Object>) value);
                    // TODO NOW remove normalize
                    normalizeFactor(values, 1);
                    priorityList.add(new EnumToValueEntry(enumEncodedValue, values));
                } else if (encodedValue instanceof DecimalEncodedValue) {
                    priorityList.add(DecimalToValueEntry.create((DecimalEncodedValue) encodedValue,
                            "priority." + key, 1, 0, 1, (Map<String, Object>) value));
                } else if (encodedValue instanceof BooleanEncodedValue) {
                    BooleanEncodedValue bev = (BooleanEncodedValue) encodedValue;
                    priorityList.add(new BooleanToValueEntry(bev, ((Number) value).doubleValue(), 1));
                } else if (encodedValue instanceof IntEncodedValue) {
                    // TODO NOW
                } else {
                    throw new IllegalArgumentException("encoded value class '" + encodedValue.getClass().getSimpleName()
                            + "' not supported. For '" + key + "' specified in 'priority'.");
                }
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for " + key + " in 'priority'");
            }
        }
    }

    static EncodedValue getEV(EncodedValueLookup lookup, String name, String key) {
        if (!lookup.hasEncodedValue(key))
            throw new IllegalArgumentException("Cannot find encoded value '" + key + "' specified in '" + name + "'. Available: " + lookup.getAllShared());
        return lookup.getEncodedValue(key, EncodedValue.class);
    }

    /**
     * This method finds the enum in the enumClass via enum.toString
     */
    private static Enum getValueOf(Enum[] enumValues, String enumToString) {
        for (Enum e : enumValues) {
            if (e.toString().equals(enumToString)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Cannot find enum " + enumToString + " in " + Arrays.toString(enumValues));
    }

    static double[] createEnumToDoubleArray(String name, double defaultValue, double minValue, double maxValue,
                                            Enum[] enumValues, Map<String, Object> map) {
        double[] tmp = new double[enumValues.length];
        Arrays.fill(tmp, defaultValue);
        for (Map.Entry<String, Object> encValEntry : map.entrySet()) {
            if (encValEntry.getKey() == null)
                throw new IllegalArgumentException("key for " + name + " cannot be null, value: " + encValEntry.getValue());
            if (encValEntry.getValue() == null)
                throw new IllegalArgumentException("value for " + name + " cannot be null, key: " + encValEntry.getKey());

            Enum enumValue = getValueOf(enumValues, encValEntry.getKey());
            tmp[enumValue.ordinal()] = ((Number) encValEntry.getValue()).doubleValue();
            if (tmp[enumValue.ordinal()] < minValue)
                throw new IllegalArgumentException(name + " cannot be smaller than " + minValue + ", was " + tmp[enumValue.ordinal()]);
            if (tmp[enumValue.ordinal()] > maxValue)
                throw new IllegalArgumentException(name + " cannot be bigger than " + maxValue + ", was " + tmp[enumValue.ordinal()]);
        }
        return tmp;
    }

    /**
     * Pick the maximum value and if it is greater than the specified max we divide all values with it - i.e. normalize it.
     * <p>
     * The purpose of this method is to avoid factors above max which makes e.g. Weighting.getMinWeight simpler (as maximum priority is 1)
     * and also ensures that the landmark algorithm still works (weights may only be increased without affecting optimality).
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
            if (priority == 0) return 0;
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
