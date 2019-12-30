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

import com.graphhopper.routing.profiles.EncodedValueFactory;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.util.FlexModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DelayFlexConfig {
    private List<ConfigMapEntry> delayList = new ArrayList<>();

    public DelayFlexConfig(FlexModel flexModel, EncodedValueLookup lookup, EncodedValueFactory factory) {
        for (Map.Entry<String, Object> entry : flexModel.getDelay().entrySet()) {
            Object value = entry.getValue();
            if (!lookup.hasEncodedValue(entry.getKey()))
                throw new IllegalArgumentException("Cannot find '" + entry.getKey() + "' specified in 'delay'");

            if (value instanceof Map) {
                EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(entry.getKey(), Enum.class);
                Class<? extends Enum> enumClass = factory.findValues(entry.getKey());
                delayList.add(new EnumToValue(enumEncodedValue, Helper.createEnumToDoubleArray(enumClass, (Map<String, Object>) value)));
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'delay'");
            }
        }
    }

    /**
     * @return delay in seconds
     */
    public double calcDelay(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        double delay = 0;
        for (int i = 0; i < delayList.size(); i++) {
            ConfigMapEntry entry = delayList.get(i);
            Double value = entry.getValue(edge, reverse);
            if (value != null)
                delay += value;
        }
        return delay;
    }
}
