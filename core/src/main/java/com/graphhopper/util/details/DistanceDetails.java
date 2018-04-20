package com.graphhopper.util.details;

import com.graphhopper.util.EdgeIteratorState;

import static com.graphhopper.util.Parameters.DETAILS.DISTANCE;

public class DistanceDetails extends AbstractPathDetailsBuilder {

    private int edgeId = -1;
    private double distance = 0;

    public DistanceDetails() {
        super(DISTANCE);
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        if (edge.getEdge() != edgeId) {
            edgeId = edge.getEdge();
            distance = edge.getDistance();
            return true;
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        return this.distance;
    }
}