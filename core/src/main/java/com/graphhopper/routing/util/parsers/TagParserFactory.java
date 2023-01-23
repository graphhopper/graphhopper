package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.util.PMap;

public interface TagParserFactory {
    TagParser create(EncodedValueLookup lookup, String name, PMap properties);
}
