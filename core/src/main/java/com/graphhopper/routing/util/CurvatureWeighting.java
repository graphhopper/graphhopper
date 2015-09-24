package com.graphhopper.routing.util;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;


/**
 * This Class uses Curvature Data to calculate curvy routes.
 */
public class CurvatureWeighting implements Weighting {

    private static final Logger logger = LoggerFactory.getLogger(CurvatureWeighting.class);

    private final FlagEncoder flagEncoder;
    private final double maxSpeed;
    private final Set<Integer> curvyEdges;
    private final NodeAccess nodeAccess;

    private final GraphHopperStorage ghStorage;
    private final DistanceCalc distCalc = Helper.DIST_EARTH;

    private double minCurvature = Double.POSITIVE_INFINITY;

    public CurvatureWeighting(FlagEncoder flagEncoder, Set<Integer> curvyEdges, GraphHopperStorage ghStorage) {
        this.flagEncoder = flagEncoder;
        this.maxSpeed = flagEncoder.getMaxSpeed();
        this.curvyEdges = curvyEdges;
        this.ghStorage = ghStorage;

        this.nodeAccess = ghStorage.getNodeAccess();
    }

    @Override
    public double getMinWeight(double distance) {
        // The formula for the Curvature Weighting should not return something that is smaller than this.
        return 0.001;
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {

        // Return low weight on curvy roads
        if (curvyEdges.contains(edge.getEdge()))
            return 0;

        return calcCurvateWeight(edge, reverse);
    }

    private double calcCurvateWeight(EdgeIteratorState edge, boolean reverse) {

        double speed = reverse ? flagEncoder.getReverseSpeed(edge.getFlags()) : flagEncoder.getSpeed(edge.getFlags());
        if (speed == 0)
            return Double.POSITIVE_INFINITY;

        double roadLength = edge.getDistance();
        double distance = calcDist(edge);

        double bendiness = distance / roadLength;

        if(0.01 > bendiness){
            bendiness = 1;
        }

        double regularWeight = roadLength / Math.log10(speed/2);

        double weight = (Math.pow(bendiness, 2) * regularWeight);

        if (bendiness < minCurvature) {
            minCurvature = bendiness;
        }

        //logger.info("Calculated a CurvatureWeighting of " + weight + " using the bendiness of " + bendiness + " the speed of " + speed +  " with min Curvature: " + minCurvature);

        return weight;
    }

    private double calcDist(EdgeIteratorState edge) {
        try {
            double firstLat = getTmpLatitude(edge.getBaseNode()), firstLon = getTmpLongitude(edge.getBaseNode());
            double lastLat = getTmpLatitude(edge.getAdjNode()), lastLon = getTmpLongitude(edge.getAdjNode());
            double straight_line = distCalc.calcNormalizedDist(firstLat, firstLon, lastLat, lastLon);

            return distCalc.calcDenormalizedDist(straight_line);
        } catch (Exception e) {
            logger.error("Unable to calculate Distance for the Edge: " + edge);
            return 0.000000000001;
        }

    }

    private double getTmpLatitude(int id) {
        return nodeAccess.getLatitude(id);
    }

    private double getTmpLongitude(int id) {
        return nodeAccess.getLongitude(id);
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