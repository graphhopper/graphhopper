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

import com.graphhopper.routing.profiles.EncodedValueFactory;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DistanceTermCustomConfig {
    private final List<ConfigMapEntry> distanceFactorList = new ArrayList<>();

    public DistanceTermCustomConfig(CustomModel customModel, EncodedValueLookup lookup, EncodedValueFactory factory) {
        for (Map.Entry<String, Object> entry : customModel.getDistanceTerm().entrySet()) {
            if (!lookup.hasEncodedValue(entry.getKey()))
                throw new IllegalArgumentException("Cannot find '" + entry.getKey() + "' specified in 'distance_term'");
            Object value = entry.getValue();
            if (value instanceof Map) {
                EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(entry.getKey(), Enum.class);
                Class<? extends Enum> enumClass = factory.findValues(entry.getKey());
                double[] values = Helper.createEnumToDoubleArray("distance_factor", 0, 0, 100,
                        enumClass, (Map<String, Object>) value);
                distanceFactorList.add(new EnumToValue(enumEncodedValue, values));
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'distance_term'");
            }
        }
    }

    public double calcDistanceTerm(EdgeIteratorState edge, boolean reverse) {
        double term = 0;
        for (int i = 0; i < distanceFactorList.size(); i++) {
            ConfigMapEntry entry = distanceFactorList.get(i);
            double value = entry.getValue(edge, reverse);
            term += value;
        }
        return term;
    }
}
