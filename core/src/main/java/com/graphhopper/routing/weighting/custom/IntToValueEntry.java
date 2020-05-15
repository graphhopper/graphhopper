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

import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.graphhopper.routing.weighting.custom.CustomWeighting.CATCH_ALL;
import static com.graphhopper.routing.weighting.custom.DecimalToValueEntry.parseRange;
import static com.graphhopper.routing.weighting.custom.EnumToValueEntry.getReturnValue;

final class IntToValueEntry implements EdgeToValueEntry {
    private final IntEncodedValue iev;
    private final double minExclusive, maxExclusive;
    private final double rangeValue;
    private final double fallback;

    private IntToValueEntry(IntEncodedValue iev, DecimalToValueEntry.Range range, double fallback) {
        this.iev = iev;
        this.minExclusive = range.min;
        this.maxExclusive = range.max;
        this.rangeValue = range.value;
        this.fallback = fallback;
    }

    @Override
    public double getValue(EdgeIteratorState iter, boolean reverse) {
        double edgeValue = iter.get(iev);
        return edgeValue < maxExclusive && edgeValue > minExclusive ? rangeValue : fallback;
    }

    static EdgeToValueEntry create(String name, IntEncodedValue dev, Map<String, Object> map,
                                   double defaultValue, double minValue, double maxValue) {
        if (map.isEmpty())
            throw new IllegalArgumentException("Empty map for " + name);

        Object evEntryValue = map.get(CATCH_ALL);
        if (evEntryValue != null)
            defaultValue = getReturnValue(name, CATCH_ALL, evEntryValue, minValue, maxValue);

        List<DecimalToValueEntry.Range> ranges = new ArrayList<>();
        for (Map.Entry<String, Object> encValEntry : map.entrySet()) {
            if (encValEntry.getKey() == null)
                throw new IllegalArgumentException("key for " + name + " cannot be null, value: " + encValEntry.getValue());
            String key = encValEntry.getKey();
            if (CATCH_ALL.equals(key))
                continue;

            double returnValue = getReturnValue(name, key, encValEntry.getValue(), minValue, maxValue);
            DecimalToValueEntry.Range range = parseRange(name, key, returnValue);
            ranges.add(range);
        }
        if (ranges.size() != 1)
            throw new IllegalArgumentException("Currently only one range can be specified but was " + ranges.size());
        return new IntToValueEntry(dev, ranges.get(0), defaultValue);
    }

    @Override
    public String toString() {
        return iev.getName() + ", range: min:" + minExclusive + ", max:" + maxExclusive + ", value:" + rangeValue;
    }
}
