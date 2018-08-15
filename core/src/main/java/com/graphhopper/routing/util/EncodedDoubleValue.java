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
package com.graphhopper.routing.util;

import com.graphhopper.storage.IntsRef;

/**
 * Encapsulates a bit-encoded value.
 * <p>
 *
 * @author Nop
 */
public class EncodedDoubleValue extends EncodedValue {

    public EncodedDoubleValue(String name, int shift, int bits, double factor, long defaultValue, int maxValue) {
        this(name, shift, bits, factor, defaultValue, maxValue, true);
    }

    public EncodedDoubleValue(String name, int shift, int bits, double factor, long defaultValue, int maxValue, boolean allowZero) {
        super(name, shift, bits, factor, defaultValue, maxValue, allowZero);
    }

    @Override
    public void setValue(IntsRef flags, long value) {
        throw new IllegalStateException("Use setDoubleValue instead");
    }

    @Override
    public long getValue(IntsRef flags) {
        throw new IllegalStateException("Use setDoubleValue instead");
    }

    @Override
    public void setDefaultValue(IntsRef flags) {
        setDoubleValue(flags, defaultValue);
    }

    public void setDoubleValue(IntsRef intsRef, double value) {
        if (Double.isNaN(value))
            throw new IllegalArgumentException("Value cannot be NaN");

        // scale value
        long tmpValue = Math.round(value / factor);
        checkValue((long) (tmpValue * factor));
        tmpValue <<= shift;

        // clear value bits
        intsRef.flags &= ~mask;

        // set value
        intsRef.flags |= tmpValue;
    }

    public double getDoubleValue(IntsRef intsRef) {
        // find value
        long flags = intsRef.flags;
        flags &= mask;
        flags >>>= shift;
        return flags * factor;
    }
}
