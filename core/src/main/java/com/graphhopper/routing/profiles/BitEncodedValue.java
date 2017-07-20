package com.graphhopper.routing.profiles;

/**
 * This class provides easy access to just one bit.
 */
public final class BitEncodedValue extends IntEncodedValue {

    /**
     * The default value is false.
     */
    public BitEncodedValue(String name) {
        super(name, 1);
    }

    public BitEncodedValue(String name, boolean store2DirectedValues) {
        super(name, 1, 0, store2DirectedValues);
    }

    public final int toStorageFormatFromBool(boolean reverse, int flags, boolean value) {
        // clear value bits
        flags &= ~fwdMask;
        if (store2DirectedValues && reverse) {
            flags &= ~bwdMask;

            if (value) {
                int intValue = 1;
                intValue <<= bwdShift;
                // set value
                return flags | intValue;
            }
            return flags;
        }

        if (value) {
            int intValue = 1;
            intValue <<= fwdShift;
            // set value
            return flags | intValue;
        }

        return flags;
    }

    public final boolean fromStorageFormatToBool(boolean reverse, int flags) {
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
