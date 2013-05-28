package com.graphhopper.routing.util;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphTurnCosts;

/**
 * 
 * @author Karl Hübner
 */
public class DefaultTurnCostsCalc implements TurnCostCalculation {

    public static final int MODE_IGNORE_RESTRICTIONS = 0;
    
    public static final int MODE_CAR_COSTS = 1;
    public static final int MODE_CAR_RESTRICTIONS = 2;
    
    public static final int MODE_BIKE_COSTS = 3;
    public static final int MODE_BIKE_RESTRICTIONS = 4;
    
    private GraphTurnCosts graph;
    private TurnCostEncoder encoder;
    
    private final int mode;
    
    /**
     * Creates a new {@link DefaultTurnCostsCalc} by the given mode of operation.
     * 
     * @see MODE_IGNORE_RESTRICTIONS
     * @see MODE_CAR_COSTS
     * @see MODE_CAR_RESTRICTIONS
     * @see MODE_BIKE_COSTS
     * @see MODE_BIKE_RESTRICTIONS 
     * 
     * @param mode
     */
    public DefaultTurnCostsCalc(int mode) {
        encoder = new TurnCostEncoder();
        this.mode = mode;
    }

    /**
     * Creates a new {@link DefaultTurnCostsCalc} which determines its mode of operation by
     * {@link EdgePropertyEncoder} and {@link WeightCalculation}. 
     * When using {@link BikeFlagEncoder} turn costs for bike will be considered, when using
     * {@link CarFlagEncoder} turn costs for cars will be considered. If we use {@link FastestCalc}
     * as weight calculation (i.e. we are searching for fastest routes), we consider 
     * turn restrictions AND costs. If we use another weight calculation (e.g. we are searching for 
     * shortest routes) we do not need to consider turn costs but turn restrictions only.
     * 
     * @param edgeEncoder
     * @param weightCalc
     */
    public DefaultTurnCostsCalc(EdgePropertyEncoder edgeEncoder, WeightCalculation weightCalc) {
        encoder = new TurnCostEncoder();
        if(edgeEncoder instanceof CarFlagEncoder){
            if(weightCalc instanceof FastestCalc){
                //we consider restrictions AND costs when searching fastest routes
                mode = MODE_CAR_COSTS;
            }else{
                //we consider restrictions only when searching shortest routes
                mode = MODE_CAR_RESTRICTIONS;
            }
        }else if(edgeEncoder instanceof BikeFlagEncoder){
            if(weightCalc instanceof FastestCalc){
                //we consider restrictions AND costs when searching fastest routes
                mode = MODE_BIKE_COSTS;
            }else{
                //we consider restrictions only when searching shortest routes
                mode = MODE_BIKE_RESTRICTIONS;
            }
        }else{
            //we do not consider costs or restriction when searching routes for pedestrians
            mode = MODE_IGNORE_RESTRICTIONS;
        }
    }

    @Override
    public double getTurnCosts(int viaNode, int fromEdge, int toEdge) {
        if (graph != null && graph.isTurnCostSupport() && mode != MODE_IGNORE_RESTRICTIONS) {
            final int costFlags = graph.turnCosts(viaNode, fromEdge, toEdge);
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
