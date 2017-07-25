package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;

/**
 * tag parsing and filtering is highly related e.g. for parsing we detect a tag and if the value is parsable then the
 * same is necessary for filtering.
 * TODO We should create a streamed approach instead: property.filter(way).parse() and skip parse
 */
public interface ReaderWayFilter {
    boolean accept(ReaderWay way);
}
