package com.graphhopper.routing.util.parsers;

public interface TagParserFactory {
    TagParser create(String name);
}
