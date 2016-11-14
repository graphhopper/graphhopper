package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeIteratorState;

class EverythingButPt implements EdgeFilter {
    private final GtfsStorage gtfsStorage;

    EverythingButPt(GtfsStorage gtfsStorage) {
        this.gtfsStorage = gtfsStorage;
    }

    @Override
public boolean accept(EdgeIteratorState edgeState) {
AbstractPtEdge edge = gtfsStorage.getEdges().get(edgeState.getEdge());
return edge == null;
}
}
