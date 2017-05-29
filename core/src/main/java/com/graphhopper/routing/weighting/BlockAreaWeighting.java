package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.EdgeIteratorState;

/**
 * This weighting is a wrapper for every weighting to support block_area
 */
public class BlockAreaWeighting implements Weighting {

    private final Weighting superWeighting;
    private GraphEdgeIdFinder.BlockArea blockArea;

    public BlockAreaWeighting(Weighting superWeighting, GraphEdgeIdFinder.BlockArea blockArea) {
        this.superWeighting = superWeighting;
        this.blockArea = blockArea;
    }

    @Override
    public double getMinWeight(double distance) {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        if (blockArea.contains(edgeState))
            return Double.POSITIVE_INFINITY;

        return superWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return superWeighting.calcMillis(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return superWeighting.getFlagEncoder();
    }

    @Override
    public String getName() {
        return "block_area|" + superWeighting.getName();
    }

    @Override
    public boolean matches(HintsMap map) {
        return superWeighting.matches(map);
    }
}
