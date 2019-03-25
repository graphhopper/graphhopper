package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

/**
 * This class defines how and where to store an unsigned integer. It is important to note that: 1. the range of the
 * integer is highly limited (unlike the Java 32bit integer values) so that the storable part of it fits into the
 * specified number of bits (maximum 32) and 2. the default value is always 0.
 *
 * @see SimpleIntEncodedValue
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
}
