package com.graphhopper.routing.weighting.custom;

import com.graphhopper.coll.MapEntry;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;

import java.util.*;

final class TurnCostCustomConfig {

    private static final Comparator<? super Map.Entry<Integer, Double>> ANGLE_CCW_COMP = new Comparator<Map.Entry<Integer, Double>>() {
        @Override
        public int compare(Map.Entry<Integer, Double> one, Map.Entry<Integer, Double> other) {
            return Double.compare(one.getValue(), other.getValue());
        }
    };

    private final double left, right, straight;
    private final NodeAccess nodeAccess;
    private final EdgeExplorer edgeExplorer;

    public TurnCostCustomConfig(EdgeExplorer edgeExplorer, NodeAccess nodeAccess, double left, double right, double straight) {
        this.edgeExplorer = edgeExplorer;
        this.nodeAccess = nodeAccess;
        this.left = left;
        this.right = right;
        this.straight = straight;
    }

    public double calcAdditionalTurnCost(int inEdge, int viaNode, int outEdge) {
        // ignore pillar nodes for now just pick adjacent nodes, also road_class changes are ignored.
        double baseLat = nodeAccess.getLatitude(viaNode), baseLon = nodeAccess.getLongitude(viaNode);
        EdgeIterator edgeIter = edgeExplorer.setBaseNode(viaNode);
        // sort by angle
        List<Map.Entry<Integer, Double>> edgeAndAngle = new ArrayList<>();
        while (edgeIter.next()) {
            int adjNode = edgeIter.getAdjNode();
            double lat = nodeAccess.getLatitude(adjNode), lon = nodeAccess.getLongitude(adjNode);
            double angle = Helper.ANGLE_CALC.calcOrientation(baseLat, baseLon, lat, lon);
            edgeAndAngle.add(new MapEntry<>(edgeIter.getEdge(), angle));
        }
        // sort it counter clockwise
        Collections.sort(edgeAndAngle, ANGLE_CCW_COMP);

        // if we number every road of the junction we can easily calculate difference: 0 => right, 1 => straight, 2 => left, >2 => left
        int inIndex = -1, outIndex = -1;
        for (int i = 0; i < edgeAndAngle.size(); i++) {
            int edge = edgeAndAngle.get(i).getKey();
            if (edge == inEdge)
                inIndex = i;
            if (edge == outEdge)
                outIndex = i;
        }

        // edges were not found. something is wrong
        if (inIndex < 0 || outIndex < 0)
            return 0;

        int difference = outIndex - inIndex;
        switch (difference) {
            case 0:
                // U-turn shouldn't happen here, should be already excluded earlier
                return Double.POSITIVE_INFINITY;
            case -3:
            case 1:
                // right turn
                return right;
            case -2:
            case 2:
                // hopefully straight
                return straight;
            default:
                // somehow left
                return left;
        }
    }
}
