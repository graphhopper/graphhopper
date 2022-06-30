package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;

/**
 * This class defines how and where to store an unsigned integer. It is important to note that: 1. the range of the
 * integer is highly limited (unlike the Java 32bit integer values) so that the storable part of it fits into the
 * specified number of bits (maximum 32) and 2. the default value is always 0.
 *
 * @see IntEncodedValueImpl
 */
public interface IntEncodedValue extends EncodedValue {

    /**
     * This method restores the integer value from the specified 'flags' taken from the storage.
     */
    int getInt(boolean reverse, IntsRef ref);

    /**
     * This method stores the specified integer value in the specified IntsRef.
     */
    void setInt(boolean reverse, IntsRef ref, int value);

    /**
     * The maximum int value this EncodedValue accepts for setInt without throwing an exception.
     */
    int getMaxInt();

    /**
     * The minimum int value this EncodedValue accepts for setInt without throwing an exception.
     */
    int getMinInt();

    /**
     * Returns the lowest known upper bound for all values of this encoded value. Initially this is just the physical
     * storage limit, and afterwards it is the maximum value set using this encoded value. Note that this is not equal
     * to the global maximum if values are set multiple times for the same edge and they are decreasing.
     * However, the returned value will always be equal to or larger than the global maximum.
     */
    int getLowestUpperBoundInt();

    /**
     * @return true if this EncodedValue can store a different value for its reverse direction
     */
    boolean isStoreTwoDirections();
}