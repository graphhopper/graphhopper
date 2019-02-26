package com.graphhopper.routing.profiles;

/**
 * This interface ensures that at least one method is defined: the index the object should get in an array, to store
 * objects with only few keys in a compact form. We can't use enums as they are not extendable nor customizable.
 */
public interface IndexBased {
    int ordinal();
}
