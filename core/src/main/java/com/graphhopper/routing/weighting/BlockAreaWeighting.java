package com.graphhopper.routing.weighting;

import com.graphhopper.routing.querygraph.QueryGraph;
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
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        if (blockArea.intersects(edgeState))
            return Double.POSITIVE_INFINITY;

        return superWeighting.calcEdgeWeight(edgeState, reverse);
    }

    @Override
    public String getName() {
        return "block_area";
    }
}
