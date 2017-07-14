package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Map;

public interface TagsParser {

    void parse(ReaderWay way, EdgeIteratorState edgeState, Map<TagParser, EncodedValue> parsers);
}
