package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;

public interface TagParser {

    String getName();

    /**
     * This method picks and transform its necessary values from specified way to create a result that
     * can be stored in the associated edge.
     */
    Object parse(ReaderWay way);
}
