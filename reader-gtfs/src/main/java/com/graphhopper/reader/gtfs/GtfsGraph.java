package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;

public interface GtfsGraph {

    EdgeFilter ptEnterPositions();

    EdgeFilter ptExitPositions();

    EdgeFilter everythingButPt();

    Weighting fastestTravelTime();

}
