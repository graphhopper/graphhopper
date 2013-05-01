package com.graphhopper.util;

import com.graphhopper.routing.util.TurnCostCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphNodeCosts;

public class TurnRestrictionsCalc implements TurnCostCalculation {

    private GraphNodeCosts graph;

    public TurnRestrictionsCalc() {
        //noop		
    }

    @Override
    public double getTurnCosts(int viaNode, int fromEdge, int toEdge) {
        if (graph != null) {
            return graph.getTurnCosts(viaNode, fromEdge, toEdge);
        }
        return 0;
    }

    @Override
    public TurnCostCalculation graph(Graph graph) {
        if (graph instanceof GraphNodeCosts) {
            this.graph = (GraphNodeCosts) graph;
        }
        return this;
    }

    @Override
    public String toString() {
        if (graph != null) {
            return "denies restricted turns";
        } else {
            return "allows any turns";
        }
    }

}
