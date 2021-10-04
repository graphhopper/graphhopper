package com.graphhopper.routing.util;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.PMap;

// ORS-GH MOD - new class
public interface PathProcessorFactory {
    PathProcessorFactory DEFAULT = new DefaultPathProcessorFactory();

    PathProcessor createPathProcessor(PMap opts, FlagEncoder enc, GraphHopperStorage ghStorage);
}
