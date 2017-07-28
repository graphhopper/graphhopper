package com.graphhopper.routing.weighting;

import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.util.EdgeIteratorState;

/**
 * This weighting is a wrapper for every weighting to support block_area
 */
public class BlockAreaWeighting extends AbstractAdjustedWeighting {

    private GraphEdgeIdFinder.BlockArea blockArea;

    public BlockAreaWeighting(Weighting superWeighting, GraphEdgeIdFinder.BlockArea blockArea) {
        super(superWeighting);
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
    public String getName() {
        return "block_area";
    }
}
