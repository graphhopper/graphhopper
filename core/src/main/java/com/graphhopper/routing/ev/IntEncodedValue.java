package com.graphhopper.routing.ev;

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
    int getInt(boolean reverse, int edgeId, EdgeBytesAccess edgeAccess);

    /**
     * This method stores the specified integer value in the specified edge access.
     */
    void setInt(boolean reverse, int edgeId, EdgeBytesAccess edgeBytesAccess, int value);

    /**
     * The maximum int value this EncodedValue accepts for setInt without throwing an exception.
     */
    int getMaxStorableInt();

    /**
     * The minimum int value this EncodedValue accepts for setInt without throwing an exception.
     */
    int getMinStorableInt();

    /**
     * Returns the maximum value set using this encoded value or the physical storage limit if no value has been set
     * at all yet. Note that even when some values were set this is not equal to the global maximum across all values in
     * the graph if values are set multiple times for the same edge and they are decreasing. However, the returned value
     * will always be equal to or larger than the global maximum.
     */
    int getMaxOrMaxStorableInt();

    /**
     * @return true if this EncodedValue can store a different value for its reverse direction
     */
    boolean isStoreTwoDirections();
}
