package com.graphhopper.routing.profiles;

/**
 * As enums are not extendable use a class with similar methods.
 * The static methods like valueOf or values() should be provided by the MappedEnumEncodedValue instead, if at all.
 */
public interface EnumAlike {
    int ordinal();
}
