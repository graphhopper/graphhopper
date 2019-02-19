package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

public interface DecimalEncodedValue extends EncodedValue {

    /**
     * This method stores the specified double value (rounding with a previously defined factor) into the IntsRef.
     */
    void setDecimal(boolean reverse, IntsRef ref, double value);

    double getDecimal(boolean reverse, IntsRef ref);
}
