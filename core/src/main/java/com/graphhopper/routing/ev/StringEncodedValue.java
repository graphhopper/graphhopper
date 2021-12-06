package com.graphhopper.routing.ev;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.carrotsearch.hppc.ObjectIntMap;
import com.graphhopper.storage.IntsRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * This class holds a List of up to {@link #maxValues} encountered Strings and stores
 * <i>index+1</i> to indicate a string is set or <i>0</i> if no value is assigned
 *
 * @author Peter Karich
 * @author Thomas Butz
 */
public final class StringEncodedValue extends IntEncodedValueImpl {
    private final int maxValues;
    private final List<String> values;
    private final ObjectIntMap<String> indexMap;

    public StringEncodedValue(String name, int expectedValueCount) {
        this(name, expectedValueCount, false);
    }

    public StringEncodedValue(String name, int expectedValueCount, boolean storeTwoDirections) {
        super(name, 32 - Integer.numberOfLeadingZeros(expectedValueCount), storeTwoDirections);

        this.maxValues = roundUp(expectedValueCount);
        this.values = new ArrayList<>(maxValues);
        this.indexMap = new ObjectIntHashMap<>(maxValues);
    }

    public StringEncodedValue(String name, int bits, List<String> values, boolean storeTwoDirections) {
        super(name, bits, storeTwoDirections);

        this.maxValues = (1 << bits) - 1;
        if (values.size() > maxValues)
            throw new IllegalArgumentException("Number of values is higher than the maximum value count: "
                    + values.size() + " > " + maxValues);

        this.values = new ArrayList<>(values);
        this.indexMap = new ObjectIntHashMap<>(values.size());
        int index = 1;
        for (String value : values) {
            indexMap.put(value, index++);
        }
    }

    public final void setString(boolean reverse, IntsRef ref, String value) {
        if (value == null) {
            super.setInt(reverse, ref, 0);
            return;
        }
        int index = indexMap.get(value);
        if (index == 0) {
            if (values.size() == maxValues)
                throw new IllegalStateException("Maximum number of values reached for " + getName() + ": " + maxValues);

            values.add(value);
            index = values.size();
            indexMap.put(value, index);
        }
        super.setInt(reverse, ref, index);
    }

    public final String getString(boolean reverse, IntsRef ref) {
        int value = super.getInt(reverse, ref);
        if (value == 0) {
            return null;
        }
        return values.get(value - 1);
    }

    /**
     * @param value the value to be rounded
     * @return the value rounded to the highest integer with the same number of leading zeros
     */
    private static int roundUp(int value) {
        return -1 >>> Integer.numberOfLeadingZeros(value);
    }

    /**
     * @param value the String to retrieve the index
     * @return the non-zero index of the String or <i>0</i> if it couldn't be found
     */
    public int indexOf(String value) {
        return indexMap.get(value);
    }

    /**
     * @return an unmodifiable List of the current values
     */
    public List<String> getValues() {
        return Collections.unmodifiableList(values);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StringEncodedValue)) {
            return false;
        }
        StringEncodedValue other = (StringEncodedValue) obj;
        if (this.bits != other.bits) {
            return false;
        }
        return Objects.equals(values, other.values);
    }

    @Override
    public int getVersion() {
        return 31 * super.getVersion() + staticHashCode(values);
    }
}
