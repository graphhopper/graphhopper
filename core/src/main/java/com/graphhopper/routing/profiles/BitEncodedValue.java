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

    public final int toStorageFormatFromBool(int flags, boolean value) {
        // clear value bits
        flags &= ~mask;

        if (value) {
            int intValue = 1;
            intValue <<= shift;
            // set value
            return flags | intValue;
        }

        return flags;
    }

    public final boolean fromStorageFormatToBool(int flags) {
        flags &= mask;
        flags >>>= shift;
        return (flags & 1) == 1;
    }
}
