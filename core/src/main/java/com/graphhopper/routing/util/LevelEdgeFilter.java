package com.graphhopper.routing.util;

import com.graphhopper.util.*;
import com.graphhopper.routing.ev.DecimalEncodedValue;


public class LevelEdgeFilter implements EdgeFilter {
    private final EdgeFilter edgeFilter;
    private final DecimalEncodedValue levelEnc;
    private final double level;

    public LevelEdgeFilter(EdgeFilter edgeFilter, DecimalEncodedValue levelEnc, double level) {
        this.edgeFilter = edgeFilter;
        this.levelEnc = levelEnc;
        this.level = level;
    }

    @Override
    public boolean accept(EdgeIteratorState edgeState) {
        if (!edgeFilter.accept(edgeState))
            return false;
        if (Double.isNaN(level))
            return true;
        
        double edgeLevel = edgeState.get(levelEnc);
        // Level EV factor is 0.1, check within precision tolerance
        return Math.abs(edgeLevel - level) < 0.05;
    }
}