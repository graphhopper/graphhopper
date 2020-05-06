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

import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Arrays;
import java.util.Map;

import static com.graphhopper.routing.weighting.custom.CustomWeighting.CATCH_ALL;

final class EnumToValueEntry implements EdgeToValueEntry {
    private final IntEncodedValue eev;
    private final double[] values;

    private EnumToValueEntry(EnumEncodedValue eev, double[] values) {
        this.eev = eev;
        this.values = values;
    }

    @Override
    public double getValue(EdgeIteratorState iter, boolean reverse) {
        int enumOrdinal = iter.get(eev);
        return values[enumOrdinal];
    }

    /**
     * Example map:
     * <pre>
     * road_class:
     *   motorway: 0.4
     *   "*": 0.9      # optional and default is 1
     * </pre>
     */
    static EnumToValueEntry create(String name, EnumEncodedValue enumEncodedValue, Map<String, Object> map,
                                   double defaultValue, double minValue, double maxValue) {
        Enum[] enumValues = enumEncodedValue.getValues();
        if (map.isEmpty())
            throw new IllegalArgumentException("Empty map for " + name);

        Object evEntryValue = map.get(CATCH_ALL);
        if (evEntryValue != null)
            defaultValue = getReturnValue(name, CATCH_ALL, evEntryValue, minValue, maxValue);

        double[] tmp = new double[enumValues.length];
        Arrays.fill(tmp, defaultValue);
        for (Map.Entry<String, Object> encValEntry : map.entrySet()) {
            if (encValEntry.getKey() == null)
                throw new IllegalArgumentException("key for " + name + " cannot be null, value: " + encValEntry.getValue());
            String key = encValEntry.getKey();
            if (CATCH_ALL.equals(key))
                continue;

            Enum enumValue = getValueOf(enumValues, key);
            double returnValue = getReturnValue(name, key, encValEntry.getValue(), minValue, maxValue);
            tmp[enumValue.ordinal()] = returnValue;
        }

        return new EnumToValueEntry(enumEncodedValue, tmp);
    }

    static double getReturnValue(String name, String key, Object valueObject, double minValue, double maxValue) {
        if (valueObject == null)
            throw new IllegalArgumentException("value for " + name + " cannot be null, key: " + key);
        if (!(valueObject instanceof Number))
            throw new IllegalArgumentException("value for " + name + " has to be a number but was: " + valueObject.getClass().getSimpleName());
        double value = ((Number) valueObject).doubleValue();
        if (value < minValue)
            throw new IllegalArgumentException(name + " cannot be smaller than " + minValue + ", was " + value);
        if (value > maxValue)
            throw new IllegalArgumentException(name + " cannot be bigger than " + maxValue + ", was " + value);
        return value;
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

    @Override
    public String toString() {
        return eev.getName() + ": " + Arrays.toString(values);
    }
}
