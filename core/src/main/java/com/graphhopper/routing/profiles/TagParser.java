package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.IntsRef;

public interface TagParser {

    String getName();

    EncodedValue getEncodedValue();

    /**
     * Filter out ways before they reach the parse method.
     */
    ReaderWayFilter getReadWayFilter();

    /**
     * This method picks and transform its necessary values from specified way to create a result that
     * can be stored in the associated edge.
     */
    void parse(IntsRef ints, ReaderWay way);
}
