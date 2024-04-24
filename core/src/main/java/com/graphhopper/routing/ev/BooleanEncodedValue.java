package com.graphhopper.routing.ev;

/**
 * This interface defines access to an edge property of type boolean. The default value is false.
 */
public interface BooleanEncodedValue extends EncodedValue {
    void setBool(boolean reverse, int edgeId, EdgeBytesAccess edgeAccess, boolean value);

    boolean getBool(boolean reverse, int edgeId, EdgeBytesAccess edgeAccess);
}
