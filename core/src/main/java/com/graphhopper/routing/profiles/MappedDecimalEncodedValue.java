package com.graphhopper.routing.profiles;

import com.carrotsearch.hppc.IntIntHashMap;

import java.util.List;

public class MappedDecimalEncodedValue extends IntEncodedValue {
    private final int toValueMap[];
    private final IntIntHashMap toStorageMap;
    private final double precision;

    /**
     * TODO should we really use precision here or use something like the already used 'factor'?
     */
    public MappedDecimalEncodedValue(String name, List<Double> values, double precision, Double defaultValue, boolean store2DirectedValues) {
        super(name, (int) Long.highestOneBit(values.size()), -1, store2DirectedValues);

        this.precision = precision;
        // store int-int mapping
        toValueMap = new int[values.size()];
        toStorageMap = new IntIntHashMap(values.size());

        int index = 0;
        for (double val : values) {
            int intVal = toInt(val);
            toValueMap[index] = intVal;
            toStorageMap.put(intVal, index);
            if (val == defaultValue)
                this.defaultValue = index;
            index++;
        }

        if (this.defaultValue < 0)
            throw new IllegalArgumentException("default value " + defaultValue + " not found");
    }

    private int toInt(double val) {
        return (int) Math.round(val / precision);
    }

    public final int toStorageFormatFromDouble(boolean reverse, int flags, double value) {
        int storageInt = toStorageMap.getOrDefault(toInt(value), -1);
        if (storageInt < 0)
            throw new IllegalArgumentException("Cannot find value " + value + " (" + toInt(value) + ") in map to store it");

        return super.uncheckToStorageFormat(reverse, flags, storageInt);
    }

    public final double fromStorageFormatToDouble(boolean reverse, int flags) {
        int value = fromStorageFormatToInt(reverse, flags);
        return toValueMap[value] * precision;
    }
}
