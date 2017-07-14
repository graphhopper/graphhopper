package com.graphhopper.routing.profiles;

/**
 * This class holds a decimal value and stores it as an integer value via a fixed factor and a maximum number
 * of bits.
 */
public final class DecimalEncodedValue extends IntEncodedValue {
    private final double factor;

    public DecimalEncodedValue(String name, int bits, double defaultValue, double factor) {
        super(name, bits);
        this.factor = factor;
        this.defaultValue = toInt(defaultValue);
    }

    /**
     * TODO This method is important to 'spread' a property when a way is splitted due to e.g. a virtual node
     */
    public final boolean isLengthDependent() {
        return false;
    }

    private int toInt(double val) {
        return (int) Math.round(val / factor);
    }

    public final int toStorageFormatFromDouble(int flags, double value) {
        if (value > maxValue * factor)
            throw new IllegalArgumentException(getName() + " value too large for encoding: " + value + ", maxValue:" + maxValue);
        if (value < 0)
            throw new IllegalArgumentException("negative value for " + getName() + " not allowed! " + value);

        return super.uncheckToStorageFormat(flags, toInt(value));
    }


    public final double fromStorageFormatToDouble(int flags) {
        int value = fromStorageFormatToInt(flags);
        return value * factor;
    }
}
