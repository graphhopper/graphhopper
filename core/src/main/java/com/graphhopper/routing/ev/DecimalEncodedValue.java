package com.graphhopper.routing.ev;

/**
 * This class defines how and where to store an unsigned decimal value. It is important to note that:
 * 1. the range of the number is highly limited (unlike the Java 32bit float or 64bit double values)
 * so that the storable part of it fits into the specified number of bits (maximum 32 at the moment
 * for all implementations) and 2. the default value is always 0.
 *
 * @see DecimalEncodedValueImpl
 */
public interface DecimalEncodedValue extends EncodedValue {

    /**
     * This method stores the specified double value (rounding with a previously defined factor) into the IntsRef.
     *
     * @see #getMaxStorableDecimal()
     */
    void setDecimal(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess, double value);

    double getDecimal(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess);

    /**
     * The maximum double value this EncodedValue accepts for setDecimal without throwing an exception.
     */
    double getMaxStorableDecimal();

    /**
     * The minimum double value this EncodedValue accepts for setDecimal without throwing an exception.
     */
    double getMinStorableDecimal();

    /**
     * @see IntEncodedValue#getMaxOrMaxStorableInt()
     */
    double getMaxOrMaxStorableDecimal();

    /**
     * @return the smallest decimal value that is larger or equal to the given value and that can be stored exactly,
     * i.e. for which {@link #getDecimal} returns the same value that we put in using {@link #setDecimal}.
     * For example if the internal scaling factor is 3 calling getDecimal after setDecimal(reverse, ref, 5) will return
     * 6 not 5! The value returned by this method is guaranteed to be storable without such a modification.
     */
    double getNextStorableValue(double value);

    double getSmallestNonZeroValue();

}
