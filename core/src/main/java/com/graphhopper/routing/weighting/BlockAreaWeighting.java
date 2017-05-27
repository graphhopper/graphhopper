package com.graphhopper.routing.weighting;

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.Shape;

import java.util.ArrayList;
import java.util.List;

/**
 * This weighting is a wrapper for every weighting to support block_area
 */
public class BlockAreaWeighting implements Weighting {

    private final Weighting superWeighting;
    private GHIntHashSet blockedEdges = new GHIntHashSet();
    private List<Shape> blockedShapes = new ArrayList<>();
    private NodeAccess na;

    public BlockAreaWeighting(Weighting superWeighting) {
        this.superWeighting = superWeighting;
    }

    @Override
    public double getMinWeight(double distance) {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        if (!blockedEdges.isEmpty() && blockedEdges.contains(edgeState.getEdge())) {
            return Double.POSITIVE_INFINITY;
        }

        if (!blockedShapes.isEmpty() && na != null) {
            for (Shape shape : blockedShapes) {
                if (shape.contains(na.getLatitude(edgeState.getAdjNode()), na.getLongitude(edgeState.getAdjNode()))) {
                    return Double.POSITIVE_INFINITY;
                }
            }
        }

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

    public BlockAreaWeighting init(Graph graph, String blockAreaStr, LocationIndex locationIndex) {
        na = graph.getNodeAccess();
        GraphEdgeIdFinder finder = new GraphEdgeIdFinder(graph, locationIndex);
        finder.parseBlockArea(blockAreaStr, new DefaultEdgeFilter(getFlagEncoder()));
        blockedEdges = finder.getBlockedEdges();
        blockedShapes = finder.getBlockedShapes();
        return this;
    }
}
