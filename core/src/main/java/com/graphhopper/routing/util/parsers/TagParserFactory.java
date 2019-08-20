package com.graphhopper.routing.util.parsers;

import com.graphhopper.util.PMap;

public interface TagParserFactory {
    TagParser create(String name, PMap configuration);
}
