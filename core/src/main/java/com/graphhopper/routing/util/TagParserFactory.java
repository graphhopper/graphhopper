package com.graphhopper.routing.util;

import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.util.PMap;

public interface TagParserFactory {
    TagParser create(String name, PMap configuration);
}
