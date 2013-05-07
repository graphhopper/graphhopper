package com.graphhopper.util;

import com.graphhopper.routing.util.TurnCostCalculation;
import com.graphhopper.storage.Graph;

public class TurnCostsIgnoreCalc implements TurnCostCalculation {

    @Override
    public double getTurnCosts(int viaNode, int fromEdge, int toEdge) {
        return 0;
    }

    @Override
    public TurnCostCalculation graph(Graph graph) {
        return this;
    }

}
