package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

/**
 * This interface provides access to an EncodedValue of an enum-alike type
 *
 * @see EnumAlike
 */
public interface EnumEncodedValue extends EncodedValue {

    void setEnum(boolean reverse, IntsRef ref, EnumAlike value);

    EnumAlike getEnum(boolean reverse, IntsRef ref);
}
