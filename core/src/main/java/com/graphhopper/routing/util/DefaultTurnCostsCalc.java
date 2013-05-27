package com.graphhopper.routing.util;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphTurnCosts;

public class DefaultTurnCostsCalc implements TurnCostCalculation {

    public static final int MODE_IGNORE_RESTRICTIONS = 0;
    
    public static final int MODE_CAR_COSTS = 1;
    public static final int MODE_CAR_RESTRICTIONS = 2;
    
    public static final int MODE_BIKE_COSTS = 3;
    public static final int MODE_BIKE_RESTRICTIONS = 4;
    
    private GraphTurnCosts graph;
    private TurnCostEncoder encoder;
    
    private final int mode;
    
    public DefaultTurnCostsCalc(int mode) {
        encoder = new TurnCostEncoder();
        this.mode = mode;
    }

    public DefaultTurnCostsCalc(EdgePropertyEncoder edgeEncoder, WeightCalculation weightCalc) {
        encoder = new TurnCostEncoder();
        if(edgeEncoder instanceof CarFlagEncoder){
            if(weightCalc instanceof FastestCalc){
                mode = MODE_CAR_COSTS;
            }else{
                mode = MODE_CAR_RESTRICTIONS;
            }
        }else if(edgeEncoder instanceof BikeFlagEncoder){
            if(weightCalc instanceof FastestCalc){
                mode = MODE_BIKE_COSTS;
            }else{
                mode = MODE_BIKE_RESTRICTIONS;
            }
        }else{
            mode = MODE_IGNORE_RESTRICTIONS;
        }
    }

    @Override
    public double getTurnCosts(int viaNode, int fromEdge, int toEdge) {
        if (graph != null && graph.isTurnCostSupport() && mode != MODE_IGNORE_RESTRICTIONS) {
            int costFlags = graph.turnCosts(viaNode, fromEdge, toEdge);
            if(mode == MODE_CAR_COSTS) {
                return encoder.getTurnCostsForCar(costFlags);
            }
            if (mode == MODE_CAR_RESTRICTIONS && encoder.isTurnRestrictedForCar(costFlags)){
                return Double.MAX_VALUE;
            }
            if(mode == MODE_BIKE_COSTS) {
                return encoder.getTurnCostsForBike(costFlags);
            }
            if (mode == MODE_BIKE_RESTRICTIONS && encoder.isTurnRestrictedForBike(costFlags)){
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
        if (graph != null && graph.isTurnCostSupport()) {
            return "turn costs for cars";
        } else {
            return "no turn costs for cars";
        }
    }
}
