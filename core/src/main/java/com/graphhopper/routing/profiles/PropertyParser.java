package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Collection;

public interface PropertyParser {

    void parse(ReaderWay way, EdgeIteratorState edge, Collection<Property> properties);
}
