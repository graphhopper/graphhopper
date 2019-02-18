package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

/**
 * This interface defines access to an edge property of type boolean. The default value is false.
 */
public interface BooleanEncodedValue extends EncodedValue {
    void setBool(boolean reverse, IntsRef ref, boolean value);

    boolean getBool(boolean reverse, IntsRef ref);
}
