package com.graphhopper.routing;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Useful for a couple of tests that want the weight to be equal to the distance (e.g. for CH shortcuts)
 */
public class InternalShortestWeighting implements Weighting {

    final BooleanEncodedValue accessEnc;
    final DecimalEncodedValue speedEnc;
    final TurnCostProvider tcProvider;

    public InternalShortestWeighting(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, TurnCostProvider tcProvider) {
        this.accessEnc = accessEnc;
        this.speedEnc = speedEnc;
        this.tcProvider = tcProvider;
    }

    @Override
    public double getMinWeight(double distance) {
        return distance;
    }

    @Override
    public boolean edgeHasNoAccess(EdgeIteratorState edgeState, boolean reverse) {
        return reverse ? !edgeState.getReverse(accessEnc) : !edgeState.get(accessEnc);
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        return edgeState.getDistance();
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        if (edgeState.getBaseNode() == edgeState.getAdjNode()) reverse = false;
        double speed = reverse ? edgeState.getReverse(speedEnc) : edgeState.get(speedEnc);
        return Math.round(edgeState.getDistance() / speed * 3.6 * 1000);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return tcProvider.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return tcProvider.calcTurnMillis(inEdge, viaNode, outEdge);
    }

    @Override
    public boolean hasTurnCosts() {
        return tcProvider != TurnCostProvider.NO_TURN_COST_PROVIDER;
    }

    @Override
    public String getName() {
        return "internal_shortest";
    }
}
