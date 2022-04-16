package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.EncodedValueLookup;

public interface TagParserFactory {
    TagParser create(EncodedValueLookup lookup, String name);
}
