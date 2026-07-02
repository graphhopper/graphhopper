package com.graphhopper.routing.ev

/**
 * This class defines how and where to store an unsigned decimal value. It is important to note that:
 * 1. the range of the number is highly limited (unlike the Java 32bit float or 64bit double values)
 * so that the storable part of it fits into the specified number of bits (maximum 32 at the moment
 * for all implementations) and 2. the default value is always 0.
 *
 * @see DecimalEncodedValueImpl
 */
interface DecimalEncodedValue : EncodedValue {

    /**
     * This method stores the specified double value (rounding with a previously defined factor) into the IntsRef.
     *
     * @see maxStorableDecimal
     */
    fun setDecimal(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: Double)

    fun getDecimal(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess): Double

    /**
     * The maximum double value this EncodedValue accepts for setDecimal without throwing an exception.
     */
    val maxStorableDecimal: Double

    /**
     * The minimum double value this EncodedValue accepts for setDecimal without throwing an exception.
     */
    val minStorableDecimal: Double

    /**
     * @see IntEncodedValue.maxOrMaxStorableInt
     */
    val maxOrMaxStorableDecimal: Double

    /**
     * @return the smallest decimal value that is larger or equal to the given value and that can be stored exactly,
     * i.e. for which [getDecimal] returns the same value that we put in using [setDecimal].
     * For example if the internal scaling factor is 3 calling getDecimal after setDecimal(reverse, ref, 5) will return
     * 6 not 5! The value returned by this method is guaranteed to be storable without such a modification.
     */
    fun getNextStorableValue(value: Double): Double

    val smallestNonZeroValue: Double
}
