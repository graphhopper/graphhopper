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
package com.graphhopper.routing.ev;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.util.StdConverter;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;

/**
 * This class allows to store distinct values via an enum. I.e. it stores just the indices
 */
@JsonDeserialize(converter = EnumEncodedValue.EnumConstantsSetter.class)
public final class EnumEncodedValue<E extends Enum> extends IntEncodedValueImpl {
    public Class<E> enumType;
    @JsonIgnore
    public E[] arr;

    EnumEncodedValue() {
        // for jackson
    }

    public EnumEncodedValue(String name, Class<E> enumType) {
        this(name, enumType, false);
    }

    public EnumEncodedValue(String name, Class<E> enumType, boolean storeTwoDirections) {
        super(name, 32 - Integer.numberOfLeadingZeros(enumType.getEnumConstants().length - 1), storeTwoDirections);
        this.enumType = enumType;
        arr = enumType.getEnumConstants();
    }

    public E[] getValues() {
        return arr;
    }

    public final void setEnum(boolean reverse, IntsRef ref, E value) {
        int intValue = value.ordinal();
        super.setInt(reverse, ref, intValue);
    }

    public final E getEnum(boolean reverse, IntsRef ref) {
        int value = super.getInt(reverse, ref);
        return arr[value];
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        EnumEncodedValue that = (EnumEncodedValue) o;
        return Arrays.equals(arr, that.arr);
    }

    @Override
    public int getVersion() {
        return 31 * super.getVersion() + staticHashCode(arr);
    }

    public static class EnumConstantsSetter<T extends Enum> extends StdConverter<EnumEncodedValue<T>, EnumEncodedValue<T>> {
        @Override
        public EnumEncodedValue<T> convert(EnumEncodedValue<T> value) {
            value.arr = value.enumType.getEnumConstants();
            return value;
        }
    }
}
