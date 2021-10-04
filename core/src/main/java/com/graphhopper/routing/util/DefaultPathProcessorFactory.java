package com.graphhopper.routing.util;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.PMap;

// ORS-GH MOD
public class DefaultPathProcessorFactory implements PathProcessorFactory {

    @Override
    public PathProcessor createPathProcessor(PMap opts, FlagEncoder enc, GraphHopperStorage ghStorage) {
        return new DefaultPathProcessor();
    }
}
