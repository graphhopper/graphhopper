package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This property class holds a string array and stores just the index.
 */
public class StringEncodedValue extends IntEncodedValue {
    private final String[] map;

    public StringEncodedValue(String name, List<String> values, String defaultValue) {
        super(name, (int) Long.highestOneBit(values.size()));

        // we want to use binarySearch so we need to sort the list
        // TODO should we simply use a separate Map<String, Int>?
        Collections.sort(values);
        map = values.toArray(new String[]{});
        this.defaultValue = Arrays.binarySearch(map, defaultValue);
        if (this.defaultValue < 0)
            throw new IllegalArgumentException("default value " + defaultValue + " not found");
    }

    @Override
    public Object parse(ReaderWay way) {
        String res = way.getTag(name);
        return map[getIndex(res)];
    }

    private int getIndex(String value) {
        if (value == null)
            return defaultValue;
        int res = Arrays.binarySearch(map, value);
        if (res < 0)
            return defaultValue;
        return res;
    }

    public final int toStorageFormat(int flags, String value) {
        int intValue = getIndex(value);
        return super.toStorageFormat(flags, intValue);
    }

    public final String fromStorageFormatToString(int flags) {
        int value = super.fromStorageFormatToInt(flags);
        if (value < 0 || value >= map.length)
            return map[defaultValue];
        return map[value];
    }
}
