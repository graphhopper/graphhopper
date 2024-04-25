package com.graphhopper.routing.weighting;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.querygraph.VirtualEdgeIterator;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.BaseGraphNodesAndEdges;
import com.graphhopper.util.EdgeIteratorState;

public class DirectAccessWeighting implements Weighting {
    private final BaseGraphNodesAndEdges store;
    private final TurnCostProvider turnCostProvider = TurnCostProvider.NO_TURN_COST_PROVIDER;
    private final DecimalEncodedValue carSpeedEnc;
    private final BooleanEncodedValue carAccessEnc;
    private final int maxEdges;

    public DirectAccessWeighting(EncodingManager em, BaseGraph graph) {
        store = graph.getStore();
        maxEdges = graph.getEdges();
        carSpeedEnc = em.getDecimalEncodedValue(VehicleSpeed.key("car"));
        carAccessEnc = em.getBooleanEncodedValue(VehicleAccess.key("car"));
    }

    /***
     * Assume a very simplistic custom model:
     *
     * <pre>
     * {
     *     "priority" : [
     *        {      "if": "!car_access",      "multiply_by": "0"    }
     *     ],
     *     "speed": [
     *        {      "if": "true",      "limit_to": "car_average_speed"    }
     *     ]
     * }
     * </pre>
     */

    @Override
    public double calcMinWeightPerDistance() {
        return 1 / 120.0;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, final boolean rawReverse) {
        int edgeId = edgeState.getEdge();

        // car_access: 2 * 1 bit
        // car_average_speed: 2 * 6 bits
        // 1. byte: 1,1,6
        // 2. byte: 6

        final boolean reverseState = edgeState.get(EdgeIteratorState.REVERSE_STATE);
        final boolean reverse = reverseState ? !rawReverse : rawReverse;

        boolean skip = edgeId >= maxEdges;

        boolean access;
        byte b1 = 0;

        if (skip) {
            access = rawReverse ? edgeState.getReverse(carAccessEnc) : edgeState.get(carAccessEnc);
        } else {
            b1 = store.getByte(edgeId, 0);
            access = (reverse ? b1 & 2 : b1 & 1) != 0;
        }

//        if (debug) {
//            if (access != tmpAccess)
//                throw new IllegalArgumentException("not equal " + access + " vs " + tmpAccess);
//        }

        if (!access) return Double.POSITIVE_INFINITY; // infinity will abort loop

        double speed;

        if (skip) {
            speed = rawReverse ? edgeState.getReverse(carSpeedEnc) : edgeState.get(carSpeedEnc);
        } else {
            int rawSpeed = reverse
                    ? (store.getByte(edgeId, 1) & 0b00111111)
                    : ((b1 >> 2) & 0xFF);
            speed = rawSpeed * 5;
        }

//        if(debug) {
//            if (speed != tmpSpeed)
//                throw new IllegalArgumentException("not equal " + speed + " vs " + tmpSpeed);
//        }

        if (speed == 0) return Double.POSITIVE_INFINITY;
        return edgeState.getDistance() / speed;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return (long) (1000 * calcEdgeWeight(edgeState, reverse));
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return turnCostProvider.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return turnCostProvider.calcTurnMillis(inEdge, viaNode, outEdge);
    }

    @Override
    public boolean hasTurnCosts() {
        return turnCostProvider != TurnCostProvider.NO_TURN_COST_PROVIDER;
    }

    @Override
    public String getName() {
        return "speed";
    }
}
