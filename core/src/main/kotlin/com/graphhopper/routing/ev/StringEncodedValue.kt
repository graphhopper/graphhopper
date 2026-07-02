package com.graphhopper.routing.ev

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Collections

/**
 * This class holds a List of up to [maxValues] encountered Strings and stores
 * *index+1* to indicate a string is set or *0* if no value is assigned
 *
 * @author Peter Karich
 * @author Thomas Butz
 */
class StringEncodedValue : IntEncodedValueImpl {
    // field names are part of the storage format (see IntEncodedValueImpl) — do not rename!
    private val maxValues: Int
    private val values: MutableList<String>
    private val indexMap: MutableMap<String, Int>

    constructor(name: String, expectedValueCount: Int) : this(name, expectedValueCount, false)

    constructor(name: String, expectedValueCount: Int, storeTwoDirections: Boolean) :
            super(name, 32 - Integer.numberOfLeadingZeros(expectedValueCount), storeTwoDirections) {
        this.maxValues = roundUp(expectedValueCount)
        this.values = ArrayList(maxValues)
        this.indexMap = HashMap(maxValues)
    }

    constructor(name: String, bits: Int, values: List<String>, storeTwoDirections: Boolean) :
            super(name, bits, storeTwoDirections) {
        this.maxValues = (1 shl bits) - 1
        if (values.size > maxValues)
            throw IllegalArgumentException("Number of values is higher than the maximum value count: " +
                    "${values.size} > $maxValues")

        this.values = ArrayList(values)
        this.indexMap = HashMap(values.size)
        var index = 1
        for (value in values) {
            indexMap[value] = index++
        }
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    internal constructor(
            @JsonProperty("name") name: String,
            @JsonProperty("bits") bits: Int,
            @JsonProperty("min_storable_value") minStorableValue: Int,
            @JsonProperty("max_storable_value") maxStorableValue: Int,
            @JsonProperty("max_value") maxValue: Int,
            @JsonProperty("negate_reverse_direction") negateReverseDirection: Boolean,
            @JsonProperty("store_two_directions") storeTwoDirections: Boolean,
            @JsonProperty("fwd_data_index") fwdDataIndex: Int,
            @JsonProperty("bwd_data_index") bwdDataIndex: Int,
            @JsonProperty("fwd_shift") fwdShift: Int,
            @JsonProperty("bwd_shift") bwdShift: Int,
            @JsonProperty("fwd_mask") fwdMask: Int,
            @JsonProperty("bwd_mask") bwdMask: Int,
            @JsonProperty("max_values") maxValues: Int,
            @JsonProperty("values") values: MutableList<String>,
            @JsonProperty("index_map") indexMap: HashMap<String, Int>) :
            super(name, bits, minStorableValue, maxStorableValue, maxValue, negateReverseDirection, storeTwoDirections,
                    fwdDataIndex, bwdDataIndex, fwdShift, bwdShift, fwdMask, bwdMask) {
        // we need this constructor for Jackson
        if (values.size > maxValues)
            throw IllegalArgumentException("Number of values is higher than the maximum value count: " +
                    "${values.size} > $maxValues")
        this.maxValues = maxValues
        this.values = values
        this.indexMap = indexMap
    }

    fun setString(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: String?) {
        if (value == null) {
            super.setInt(reverse, edgeId, edgeIntAccess, 0)
            return
        }
        var index = indexMap.getOrDefault(value, 0)
        if (index == 0) {
            if (values.size == maxValues)
                throw IllegalStateException("Maximum number of values reached for $name: $maxValues")

            values.add(value)
            index = values.size
            indexMap[value] = index
        }
        super.setInt(reverse, edgeId, edgeIntAccess, index)
    }

    fun getString(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess): String? {
        val value = super.getInt(reverse, edgeId, edgeIntAccess)
        if (value == 0) {
            return null
        }
        return values[value - 1]
    }

    /**
     * @param value the String to retrieve the index
     * @return the non-zero index of the String or *0* if it couldn't be found
     */
    fun indexOf(value: String?): Int {
        return if (value == null) 0 else indexMap.getOrDefault(value, 0)
    }

    /**
     * @return an unmodifiable List of the current values
     */
    fun getValues(): List<String> = Collections.unmodifiableList(values)

    companion object {
        /**
         * @param value the value to be rounded
         * @return the value rounded to the highest integer with the same number of leading zeros
         */
        private fun roundUp(value: Int): Int = -1 ushr Integer.numberOfLeadingZeros(value)
    }
}
