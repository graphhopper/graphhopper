package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.IntsRef;

/**
 * This interface defines how parts of the information from 'way' is converted into IntsRef. In
 * OpenStreetMap one tag key corresponds to one EncodedValue, then you can associate them via 'EncodingManager.put'.
 * Other situations else like multiple tags into one EncodedValue are possible too. A list of 'TagParser's is currently
 * aggregated in one 'FlagEncoder' (subject to change).
 */
public interface TagParser {
    IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, long relationFlags);
}
