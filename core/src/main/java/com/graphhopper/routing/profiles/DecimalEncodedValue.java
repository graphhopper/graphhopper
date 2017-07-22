package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

/**
 * This class holds a decimal value and stores it as an integer value via a fixed factor and a maximum number
 * of bits.
 */
public class DecimalEncodedValue extends IntEncodedValue {
    private final double factor;

    public DecimalEncodedValue(String name, int bits, double defaultValue, double factor, boolean store2DirectedValues) {
        super(name, bits, 0, store2DirectedValues);
        this.factor = factor;
        this.defaultValue = toInt(defaultValue);
    }

    /**
     * TODO This method is important to 'spread' an encoded value when a way is split due to e.g. a virtual node
     */
    public final boolean isLengthDependent() {
        return false;
    }

    private int toInt(double val) {
        return (int) Math.round(val / factor);
    }

    public final void setDecimal(boolean reverse, IntsRef ref, double value) {
        if (value > maxValue * factor)
            throw new IllegalArgumentException(getName() + " value too large for encoding: " + value + ", maxValue:" + maxValue);
        if (value < 0)
            throw new IllegalArgumentException("negative value for " + getName() + " not allowed! " + value);

        super.setInt(reverse, ref, toInt(value));
    }

    public final double getDecimal(boolean reverse, IntsRef ref) {
        int value = getInt(reverse, ref);
        return value * factor;
    }
}
