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

import java.util.HashMap;
import java.util.Map;

class EnumEncodedValueImpl<T extends Enum> extends IntEncodedValueImpl implements EnumEncodedValue<T> {
    private final T[] enums;
    private final Map<String, Integer> enumMap;

    EnumEncodedValueImpl(String name, T[] values, T _default) {
        super(name, 32 - Integer.numberOfLeadingZeros(values.length));
        enums = values;
        enumMap = new HashMap<>(values.length);
        int counter = 0;
        for (Enum v : values) {
            enumMap.put(v.toString(), counter++);
        }

        this.defaultValue = _default.ordinal();
    }

    @Override
    public final int size() {
        return enums.length;
    }

    /**
     * Provides an index based lookup of the specified value.
     */
    @Override
    public final int indexOf(String value) {
        if (value == null)
            return defaultValue;
        Integer res = enumMap.get(value);
        if (res == null)
            return defaultValue;
        return res;
    }

    @Override
    public T[] getEnums() {
        return enums;
    }

    @Override
    public final void setEnum(boolean reverse, IntsRef ref, T value) {
        super.setInt(reverse, ref, value.ordinal());
    }

    @Override
    public final T getEnum(boolean reverse, IntsRef ref) {
        int value = super.getInt(reverse, ref);
        if (value < 0 || value >= enums.length)
            return enums[defaultValue];
        return enums[value];
    }
}
