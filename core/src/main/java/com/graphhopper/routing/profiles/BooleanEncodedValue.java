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
        super(name, 1);
    }

    public BooleanEncodedValue(String name, boolean store2DirectedValues) {
        super(name, 1, 0, store2DirectedValues);
    }

    public final void setBool(boolean reverse, IntsRef ref, boolean value) {
        int flags = ref.ints[getDataIndex() + ref.offset];
        // clear value bits
        flags &= ~fwdMask;
        if (store2DirectedValues && reverse) {
            flags &= ~bwdMask;

            if (value) {
                int intValue = 1;
                intValue <<= bwdShift;
                // set value
                flags = flags | intValue;
            }
        } else {
            if (value) {
                int intValue = 1;
                intValue <<= fwdShift;
                // set value
                flags = flags | intValue;
            }
        }

        ref.ints[getDataIndex()] = flags;
    }

    public final boolean getBool(boolean reverse, IntsRef ref) {
        int flags = ref.ints[getDataIndex() + ref.offset];
        if (store2DirectedValues && reverse) {
            flags &= bwdMask;
            flags >>>= bwdMask;
        } else {
            flags &= fwdMask;
            flags >>>= fwdShift;
        }
        return (flags & 1) == 1;
    }
}
