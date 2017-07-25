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
