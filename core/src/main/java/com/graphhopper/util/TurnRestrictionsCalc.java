package com.graphhopper.util;

import com.graphhopper.routing.util.TurnCostCalculation;
import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphTurnCosts;

public class TurnRestrictionsCalc implements TurnCostCalculation {

    private GraphTurnCosts graph;
    private TurnCostEncoder encoder;

    public TurnRestrictionsCalc() {
        encoder = new TurnCostEncoder();
    }

    @Override
    public double getTurnCosts(int viaNode, int fromEdge, int toEdge) {
        if (graph != null) {
            int flags = graph.turnCosts(viaNode, fromEdge, toEdge);
            if(encoder.isTurnRestricted(flags)){
                return Double.MAX_VALUE;
            }
        }
        return 0;
    }

    @Override
    public TurnCostCalculation graph(Graph graph) {
        if (graph instanceof GraphTurnCosts) {
            this.graph = (GraphTurnCosts) graph;
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
    
    public static void main(String[] args) {
        Double inf = Double.POSITIVE_INFINITY;
        System.out.println(inf);
        System.out.println(inf+Double.MAX_VALUE);
    }

}
