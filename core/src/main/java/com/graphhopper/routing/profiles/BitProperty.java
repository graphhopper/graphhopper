package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;

/**
 * This class provides easy access to just one bit.
 */
public class BitProperty extends AbstractProperty {

    public BitProperty(String name) {
        super(name, 1);
    }

    public int toStorageFormatFromBool(int flags, boolean value) {
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

    public boolean fromStorageFormatToBool(int flags) {
        flags &= mask;
        flags >>>= shift;
        return (flags & 1) == 1;
    }

    @Override
    public Object parse(ReaderWay way) {
        String value = way.getTag(name);
        try {
            return Boolean.parseBoolean(value);
        } catch (Exception ex) {
            return Boolean.FALSE;
        }
    }
}
