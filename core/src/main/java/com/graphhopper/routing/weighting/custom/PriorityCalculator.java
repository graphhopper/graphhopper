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

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PriorityCalculator {
    private final List<EdgeToValueEntry> priorityList = new ArrayList<>();

    public PriorityCalculator(CustomModel customModel, EncodedValueLookup lookup) {
        for (Map.Entry<String, Object> entry : customModel.getPriority().entrySet()) {
            String key = entry.getKey();
            String priorityKey = "priority." + key;
            Object value = entry.getValue();
            if (value == null)
                throw new IllegalArgumentException("Missing value for " + key + " in 'priority'");

            if (key.startsWith(GeoToValueEntry.AREA_PREFIX)) {
                if (!(value instanceof Number))
                    throw new IllegalArgumentException(priorityKey + ": area entry requires number value but was: " + value.getClass().getSimpleName());
                Geometry geometry = GeoToValueEntry.pickGeometry(customModel, key);
                priorityList.add(GeoToValueEntry.create(priorityKey, new PreparedGeometryFactory().create(geometry),
                        (Number) value, 1, 0, 1));
            } else {
                if (!(value instanceof Map))
                    throw new IllegalArgumentException(priorityKey + ": non-root entries require a map but was: " + value.getClass().getSimpleName());
                final double defaultPriority = 1, minPriority = 0, maxPriority = 1;
                EncodedValue encodedValue = getEV(lookup, "priority", key);
                if (encodedValue instanceof EnumEncodedValue) {
                    priorityList.add(EnumToValueEntry.create(priorityKey, (EnumEncodedValue) encodedValue,
                            (Map) value, defaultPriority, minPriority, maxPriority));
                } else if (encodedValue instanceof DecimalEncodedValue) {
                    priorityList.add(DecimalToValueEntry.create(priorityKey, (DecimalEncodedValue) encodedValue,
                            (Map) value, defaultPriority, minPriority, maxPriority));
                } else if (encodedValue instanceof BooleanEncodedValue) {
                    priorityList.add(BooleanToValueEntry.create(priorityKey, (BooleanEncodedValue) encodedValue,
                            (Map) value, defaultPriority, minPriority, maxPriority));
                } else if (encodedValue instanceof IntEncodedValue) {
                    priorityList.add(IntToValueEntry.create(priorityKey, (IntEncodedValue) encodedValue,
                            (Map) value, defaultPriority, minPriority, maxPriority));
                } else {
                    throw new IllegalArgumentException("The encoded value '" + key + "' used in 'priority' is of type "
                            + encodedValue.getClass().getSimpleName() + ", but only types enum, decimal and boolean are supported.");
                }
            }
        }
    }

    static EncodedValue getEV(EncodedValueLookup lookup, String name, String key) {
        if (!lookup.hasEncodedValue(key))
            throw new IllegalArgumentException("Cannot find encoded value '" + key + "' specified in '" + name
                    + "'. Available: " + names(lookup.getAllShared()));
        return lookup.getEncodedValue(key, EncodedValue.class);
    }

    private static String names(List<EncodedValue> allShared) {
        String nameStr = "";
        for (EncodedValue ev : allShared) {
            nameStr += ev.getName() + ",";
        }
        return nameStr;
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
}
