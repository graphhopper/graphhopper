package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

import java.util.List;

/**
 * This interface defines how parts of the information from 'way' is converted into IntsRef. A TagParser usually
 * has one corresponding EncodedValue. Other situations else like multiple tags into one EncodedValue are possible too.
 */
public interface TagParser {

    void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue);

    IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, long relationFlags);
}
