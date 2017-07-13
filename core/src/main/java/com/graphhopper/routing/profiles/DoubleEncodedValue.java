package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;

/**
 * This stores an int and converts to a double via a fixed factor.
 */
public class DoubleEncodedValue extends IntEncodedValue {
    private final double factor;

    public DoubleEncodedValue(String name, int bits, double defaultValue, double factor) {
        super(name, bits);
        this.factor = factor;
        this.defaultValue = toInt(defaultValue);
    }

    /**
     * TODO This method is important to 'spread' a property when a way is splitted due to e.g. a virtual node
     */
    public boolean isLengthDependent() {
        return false;
    }

    private int toInt(double val) {
        return (int) Math.round(val / factor);
    }

    public int toStorageFormatFromDouble(int flags, double val) {
        return super.toStorageFormat(flags, toInt(val));
    }

    public double fromStorageFormatToDouble(int flags) {
        int value = fromStorageFormatToInt(flags);
        return value * factor;
    }

    @Override
    public Object parse(ReaderWay way) {
        String value = way.getTag(name);
        try {
            return Double.parseDouble(value);
        } catch (Exception ex) {
            return defaultValue * factor;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}
