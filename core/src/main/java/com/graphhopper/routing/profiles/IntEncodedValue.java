package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;

/**
 * TODO add description, similar to EncodedValue08
 */
public class IntEncodedValue extends AbstractEncodedValue {

    // TODO allowZero and min value?
    protected int defaultValue;

    public IntEncodedValue(String name, int bits) {
        this(name, bits, 0);
    }

    public IntEncodedValue(String name, int bits, int defaultValue) {
        super(name, bits);
        this.defaultValue = defaultValue;
    }

    public int toStorageFormat(int flags, int value) {
        checkValue(value);

        value <<= shift;

        // clear value bits
        flags &= ~mask;

        // set value
        return flags | value;
    }

    public int fromStorageFormatToInt(int flags) {
        flags &= mask;
        flags >>>= shift;
        return flags;
    }

    @Override
    public Object parse(ReaderWay way) {
        String value = way.getTag(name);
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}
