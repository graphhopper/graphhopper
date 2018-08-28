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
package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class holds a string array and stores only a number (typically the array index via IntEncodedValue)
 * to restore this information.
 */
public final class StringEncodedValue extends IntEncodedValue {
    private final String[] map;

    public StringEncodedValue(String name, List<String> values, String defaultValue) {
        super(name, 32 - Integer.numberOfLeadingZeros(values.size()));

        // we want to use binarySearch so we need to sort the list
        // TODO should we simply use a separate Map<String, Int>?
        Collections.sort(values);
        map = values.toArray(new String[]{});
        this.defaultValue = Arrays.binarySearch(map, defaultValue);
        if (this.defaultValue < 0)
            throw new IllegalArgumentException("default value " + defaultValue + " not found");
    }

    private StringEncodedValue() {
        super();
        map = new String[0];
    }

    public final int getMapSize() {
        return map.length;
    }

    public final int indexOf(String value) {
        if (value == null)
            return defaultValue;
        int res = Arrays.binarySearch(map, value);
        if (res < 0)
            return defaultValue;
        return res;
    }

    public final void setString(boolean reverse, IntsRef ref, String value) {
        int intValue = indexOf(value);
        super.setInt(reverse, ref, intValue);
    }

    public final String getString(boolean reverse, IntsRef ref) {
        int value = super.getInt(reverse, ref);
        if (value < 0 || value >= map.length)
            return map[defaultValue];
        return map[value];
    }
}
