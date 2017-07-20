package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.EdgeIteratorState;

public interface TagParser {

    String getName();

    // TODO Every tag parser has an EncodedValue associated but except for convenient usage in EncodingManager we currently do not need this method
    EncodedValue getEncodedValue();

    /**
     * This method picks and transform its necessary values from specified way to create a result that
     * can be stored in the associated edge.
     */
    void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState);
}
