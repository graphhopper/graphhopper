package com.graphhopper.util.details;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

public class TurnCostsDetails extends AbstractPathDetailsBuilder {

    private Weighting weighting;
    private EdgeIteratorState prevEdge;
    private Double costs = null;

    public TurnCostsDetails(Weighting weighting) {
        super("turn_costs");

        this.weighting = weighting;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        final Double currCosts;
        if (prevEdge != null && prevEdge != edge) {
            currCosts = Math.round(weighting.calcTurnMillis(prevEdge.getEdge(), prevEdge.getAdjNode(), edge.getEdge()) / 100d) / 10d;
        } else {
            currCosts = 0d;
        }

        prevEdge = edge;
        costs = currCosts;
        return true;
    }

    @Override
    protected Object getCurrentValue() {
        return costs;
    }
}
