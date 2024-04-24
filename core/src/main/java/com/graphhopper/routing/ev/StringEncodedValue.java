package com.graphhopper.routing.ev;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

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
    private final Map<String, Integer> indexMap;

    public StringEncodedValue(String name, int expectedValueCount) {
        this(name, expectedValueCount, false);
    }

    public StringEncodedValue(String name, int expectedValueCount, boolean storeTwoDirections) {
        super(name, 32 - Integer.numberOfLeadingZeros(expectedValueCount), storeTwoDirections);

        this.maxValues = roundUp(expectedValueCount);
        this.values = new ArrayList<>(maxValues);
        this.indexMap = new HashMap<>(maxValues);
    }

    public StringEncodedValue(String name, int bits, List<String> values, boolean storeTwoDirections) {
        super(name, bits, storeTwoDirections);

        this.maxValues = (1 << bits) - 1;
        if (values.size() > maxValues)
            throw new IllegalArgumentException("Number of values is higher than the maximum value count: "
                    + values.size() + " > " + maxValues);

        this.values = new ArrayList<>(values);
        this.indexMap = new HashMap<>(values.size());
        int index = 1;
        for (String value : values) {
            indexMap.put(value, index++);
        }
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    StringEncodedValue(
            @JsonProperty("name") String name,
            @JsonProperty("bits") int bits,
            @JsonProperty("min_storable_value") int minStorableValue,
            @JsonProperty("max_storable_value") int maxStorableValue,
            @JsonProperty("max_value") int maxValue,
            @JsonProperty("negate_reverse_direction") boolean negateReverseDirection,
            @JsonProperty("store_two_directions") boolean storeTwoDirections,
            @JsonProperty("fwd_data_index") int fwdDataIndex,
            @JsonProperty("bwd_data_index") int bwdDataIndex,
            @JsonProperty("fwd_shift") int fwdShift,
            @JsonProperty("bwd_shift") int bwdShift,
            @JsonProperty("fwd_mask") int fwdMask,
            @JsonProperty("bwd_mask") int bwdMask,
            @JsonProperty("max_values") int maxValues,
            @JsonProperty("values") List<String> values,
            @JsonProperty("index_map") HashMap<String, Integer> indexMap) {
        // we need this constructor for Jackson
        super(name, bits, minStorableValue, maxStorableValue, maxValue, negateReverseDirection, storeTwoDirections, fwdDataIndex, bwdDataIndex, fwdShift, bwdShift, fwdMask, bwdMask);
        if (values.size() > maxValues)
            throw new IllegalArgumentException("Number of values is higher than the maximum value count: "
                    + values.size() + " > " + maxValues);
        this.maxValues = maxValues;
        this.values = values;
        this.indexMap = indexMap;
    }

    public void setString(boolean reverse, int edgeId, EdgeBytesAccess edgeAccess, String value) {
        if (value == null) {
            super.setInt(reverse, edgeId, edgeAccess, 0);
            return;
        }
        int index = indexMap.getOrDefault(value, 0);
        if (index == 0) {
            if (values.size() == maxValues)
                throw new IllegalStateException("Maximum number of values reached for " + getName() + ": " + maxValues);

            values.add(value);
            index = values.size();
            indexMap.put(value, index);
        }
        super.setInt(reverse, edgeId, edgeAccess, index);
    }

    public String getString(boolean reverse, int edgeId, EdgeBytesAccess edgeAccess) {
        int value = super.getInt(reverse, edgeId, edgeAccess);
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
        return indexMap.getOrDefault(value, 0);
    }

    /**
     * @return an unmodifiable List of the current values
     */
    public List<String> getValues() {
        return Collections.unmodifiableList(values);
    }

}
