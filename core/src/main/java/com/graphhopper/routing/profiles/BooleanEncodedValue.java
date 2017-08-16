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

/**
 * This class provides easy access to just one bit.
 */
public final class BooleanEncodedValue extends IntEncodedValue {

    /**
     * The default value is false.
     */
    public BooleanEncodedValue(String name) {
        this(name, false);
    }

    public BooleanEncodedValue(String name, boolean store2DirectedValues) {
        super(name, 1, 0, store2DirectedValues);
    }

    public final void setBool(boolean reverse, IntsRef ref, boolean value) {
        int flags = ref.ints[dataIndex + ref.offset];
        if (store2DirectedValues && reverse) {
            flags &= ~bwdMask;
            // set value
            if (value)
                flags = flags | (1 << bwdShift);

        } else {
            // clear value bits
            flags &= ~fwdMask;
            // set value
            if (value)
                flags = flags | (1 << fwdShift);
        }

        ref.ints[dataIndex + ref.offset] = flags;
    }

    public final boolean getBool(boolean reverse, IntsRef ref) {
        int flags = ref.ints[dataIndex + ref.offset];
        if (store2DirectedValues && reverse)
            return (((flags & bwdMask) >>> bwdShift) & 0x1) == 0x1;

        return (((flags & fwdMask) >>> fwdShift) & 0x1) == 0x1;
    }
}
