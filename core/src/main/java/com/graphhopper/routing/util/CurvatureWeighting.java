package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Set;


/**
 * This Class uses Curvature Data to calculate curvy routes.
 */
public class CurvatureWeighting implements Weighting {

    private final FlagEncoder flagEncoder;
    private final double maxSpeed;
    private final Set<Integer> curvyEdges;

    public CurvatureWeighting(FlagEncoder flagEncoder, Set<Integer> curvyEdges) {
        this.flagEncoder = flagEncoder;
        this.maxSpeed = flagEncoder.getMaxSpeed();
        this.curvyEdges = curvyEdges;

        System.out.println("Curvature Weighting was initialized!");
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed;
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {

        // Return low weight on curvy roads
        if(curvyEdges.contains(edge.getEdge()))
            return 0;

        double speed = reverse ? flagEncoder.getReverseSpeed(edge.getFlags()) : flagEncoder.getSpeed(edge.getFlags());
        if (speed == 0)
            return Double.POSITIVE_INFINITY;

        double time = edge.getDistance() / speed;

        return time;

    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return flagEncoder;
    }

    @Override
    public String toString() {
        return "CURVATURE";
    }

}
