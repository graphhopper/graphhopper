package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;

/**
 * This interface defines access to an edge property of type boolean. The default value is false.
 */
public interface BooleanEncodedValue extends EncodedValue {

    default void setBool(boolean reverse, IntsRef ref, boolean value) {
        setBool(-1, reverse, ref, value);
    }

    void setBool(int edgeId, boolean reverse, IntsRef ref, boolean value);

    default boolean getBool(boolean reverse, IntsRef ref) {
        return getBool(-1, reverse, ref);
    }

    boolean getBool(int edgeId, boolean reverse, IntsRef ref);
}
