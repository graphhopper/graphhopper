package com.graphhopper.routing.weighting;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.BaseGraphNodesAndEdges;
import com.graphhopper.util.EdgeIteratorState;

public class DirectAccessWeighting implements Weighting {
    private final BaseGraphNodesAndEdges store;
    private final TurnCostProvider turnCostProvider = TurnCostProvider.NO_TURN_COST_PROVIDER;
    private final DecimalEncodedValue carSpeedEnc;
    private final BooleanEncodedValue carAccessEnc;
    private final RoadAccess[] allRoadAccess = RoadAccess.values();
    private final RoadClass[] allRoadClasses = RoadClass.values();
    private final EnumEncodedValue<RoadAccess> roadAccessEnc;
    private final EnumEncodedValue<RoadClass> roadClassEnc;
    private final int maxEdges;

    public DirectAccessWeighting(EncodingManager em, BaseGraph graph) {
        store = graph.getStore();
        maxEdges = graph.getEdges();
        carSpeedEnc = em.getDecimalEncodedValue(VehicleSpeed.key("car"));
        carAccessEnc = em.getBooleanEncodedValue(VehicleAccess.key("car"));
        roadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
        roadClassEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);

        if (em.getEnumEncodedValue(Surface.KEY, Surface.class).getBits() != 4)
            throw new IllegalArgumentException("now " + em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class).getBits());

        if (roadAccessEnc.getBits() != 4)
            throw new IllegalArgumentException("now " + roadAccessEnc.getBits());

        if (roadClassEnc.getBits() != 5)
            throw new IllegalArgumentException("now " + roadClassEnc.getBits());
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
        byte b0 = 0;

        if (skip) {
            access = rawReverse ? edgeState.getReverse(carAccessEnc) : edgeState.get(carAccessEnc);
        } else {
            b0 = store.getByte(edgeId, 0);
            access = (reverse ? b0 & 2 : b0 & 1) != 0;
        }

        if (!access) return Double.POSITIVE_INFINITY; // infinity will abort loop

        double speed;
        RoadAccess roadAccess;
        RoadClass roadClass;

        if (skip) {
            speed = rawReverse ? edgeState.getReverse(carSpeedEnc) : edgeState.get(carSpeedEnc);
            roadAccess = edgeState.get(roadAccessEnc);
            roadClass = edgeState.get(roadClassEnc);
        } else {
            byte b1 = store.getByte(edgeId, 1); // 6 bits
            int rawSpeed = reverse ? extract(b1, 6, 0) : extract(b0, 6, 2);
            speed = rawSpeed * 5;

            if (speed != (rawReverse ? edgeState.getReverse(carSpeedEnc) : edgeState.get(carSpeedEnc)))
                throw new IllegalArgumentException(speed + " vs " + edgeState.get(carSpeedEnc) + " " +extract(b1, 6, 0) + " " + extract(b0, 6, 2) + ", b1=" + b1 + ", b0="+ b0);

            byte b2 = store.getByte(edgeId, 2); // 4 bits
            roadAccess = allRoadAccess[extract(b2, b1, 4, 6)];
            if (roadAccess != edgeState.get(roadAccessEnc))
                throw new IllegalArgumentException(roadAccess + " vs " + edgeState.get(roadAccessEnc));

            // skip 4 bits for surface in-between 0b00111100

            byte b3 = store.getByte(edgeId, 3); // 5 bits
            roadClass = allRoadClasses[extract(b3, b2, 5, 6)];
            if (roadClass != edgeState.get(roadClassEnc))
                throw new IllegalArgumentException(roadClass + " vs " + edgeState.get(roadClassEnc) + " " + extract(b3, b2, 5, 6)+ " " + b3 + " " + b2);

//            byte b1 = store.getByte(edgeId, 1); // 6 bits
//            int rawSpeed = reverse ? (b1 & 0b00111111) : ((b0 >> 2) & 0xFF);
//            speed = rawSpeed * 5;
//
//            byte b2 = store.getByte(edgeId, 2); // 4 bits
//            roadAccess = allRoadAccess[(b2 & 0b00000011) << 2 | (b1 & 0b11000000) >>> 6];
//
//            // skip 4 bits for surface in-between 0b00111100
//
//            byte b3 = store.getByte(edgeId, 3); // 5 bits
//            roadClass = allRoadClasses[(b3 & 0b00000111) << 2 | (b2 & 0b11000000) >>> 6];
        }

        if (roadAccess == RoadAccess.PRIVATE)
            speed = speed * 0.1;

        if (roadClass == RoadClass.SERVICE || roadClass == RoadClass.ROAD)
            speed = speed * 0.1;

        if (speed == 0) return Double.POSITIVE_INFINITY;
        return edgeState.getDistance() / speed;
    }

    public static int mask(int b, int bits) {
        return b & ((1 << bits) - 1);
    }

    // use it only if bits+shift <= 8
    public static int extract(int b0, int bits, int shift) {
        assert bits + shift <= 8;
        return mask(b0 >>> shift, bits);
    }

    // use it only if 8 < bits+shift <= 16
    public static int extract(int b1, int b0, int bits, int shift) {
        assert bits + shift <= 16;
        final int bits0 = 8 - shift;
        final int bits1 = bits - bits0;
        return (mask(b1, bits1) << bits0) | extract(b0, bits0, shift);
    }

    // use it only if 16 < bits+shift <= 24
    public static int extract(int b2, int b1, int b0, int bits, int shift) {
        assert bits + shift <= 24;
        final int bits1 = 8;
        final int bits0 = 8 - shift;
        final int bits2 = bits - bits1 - bits0;
        return (mask(b2, bits2) << (bits1 + bits0)) | (b1 & 0xFF) << bits0 | extract(b0, bits0, shift);
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
