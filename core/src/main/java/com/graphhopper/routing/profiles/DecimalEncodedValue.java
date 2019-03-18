package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

/**
 * This class defines how and where to store an unsigned decimal value. It is important to note that:
 * 1. the range of the number is highly limited (unlike the Java 32bit float or 64bit double values)
 * so that the storable part of it fits into the specified number of bits
 * (maximum 32 at the moment for all implementations) and
 * 2. the default value is always 0.
 *
 * @see FactorizedDecimalEncodedValue
 * @see MappedDecimalEncodedValue
 */
public interface DecimalEncodedValue extends EncodedValue {

    /**
     * This method stores the specified double value (rounding with a previously defined factor) into the IntsRef.
     */
    void setDecimal(boolean reverse, IntsRef ref, double value);

    double getDecimal(boolean reverse, IntsRef ref);
}
