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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Map;

import static com.graphhopper.routing.weighting.custom.CustomWeighting.CATCH_ALL;
import static com.graphhopper.routing.weighting.custom.EnumToValueEntry.getReturnValue;

final class BooleanToValueEntry implements EdgeToValueEntry {
    private final BooleanEncodedValue bev;
    private final double value, elseValue;

    private BooleanToValueEntry(BooleanEncodedValue bev, double value, double elseValue) {
        this.bev = bev;
        this.value = value;
        this.elseValue = elseValue;
    }

    /**
     * Example map:
     * <pre>
     * get_off_bike:
     *   true: 0.4
     *   false: 0.9 # optional and default is 1, equivalent to "*": 0.9
     * </pre>
     */
    static EdgeToValueEntry create(String name, BooleanEncodedValue encodedValue, Map<String, Object> map,
                                   double defaultValue, double minValue, double maxValue) {
        if (map.isEmpty())
            throw new IllegalArgumentException("Empty map for " + name);

        // the key can only be a String, i.e. "false" and not a boolean false -> this is properly done from jackson
        if (map.containsKey(CATCH_ALL) && map.containsKey("false"))
            throw new IllegalArgumentException(name + ": cannot contain false and catch-all key at the same time");

        double trueValue = Double.NaN;
        double falseValue = defaultValue;
        for (Map.Entry<String, Object> encValEntry : map.entrySet()) {
            if (encValEntry.getKey() == null)
                throw new IllegalArgumentException("key for " + name + " cannot be null, value: " + encValEntry.getValue());
            String key = encValEntry.getKey();

            double returnValue = getReturnValue(name, key, encValEntry.getValue(), minValue, maxValue);
            if ("true".equals(key)) {
                trueValue = returnValue;
            } else if ("false".equals(key) || CATCH_ALL.equals(key)) {
                falseValue = returnValue;
            } else {
                throw new IllegalArgumentException("key for " + name + " cannot be " + key + ", value: " + encValEntry.getValue());
            }
        }

        return new BooleanToValueEntry(encodedValue, trueValue, falseValue);
    }

    @Override
    public double getValue(EdgeIteratorState iter, boolean reverse) {
        if (Double.isNaN(value)) return elseValue; // special case if only catch-all key is present
        return iter.get(bev) ? value : elseValue;
    }

    @Override
    public String toString() {
        return bev.getName() + ": " + value + ", else:" + elseValue;
    }
}
