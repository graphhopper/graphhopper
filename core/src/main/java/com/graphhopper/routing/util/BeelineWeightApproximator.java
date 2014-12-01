package com.graphhopper.routing.util;

import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.DistancePlaneProjection;

/**
 * Approximates the distance to the goalNode by weighting the beeline distance according to the distance weighting
 * @author Jan Soe
 */
public class BeelineWeightApproximator implements WeightApproximator {

    private NodeAccess nodeAccess;
    private Weighting weighting;
    private DistanceCalc distanceCalc;

    public BeelineWeightApproximator(NodeAccess nodeAccess, Weighting weighting) {
        this.nodeAccess = nodeAccess;
        this.weighting = weighting;
        setDistanceCalc(new DistanceCalcEarth());
    }

    @Override
    public double approximate(int fromNode, int toNode) {

        double fromLat, fromLon, toLat, toLon, dist2goal, weight2goal;
        fromLat  = nodeAccess.getLatitude(fromNode);
        fromLon = nodeAccess.getLongitude(fromNode);
        toLat = nodeAccess.getLatitude(toNode);
        toLon = nodeAccess.getLongitude(toNode);
        dist2goal = distanceCalc.calcDist(toLat, toLon, fromLat, fromLon);
        weight2goal = weighting.getMinWeight(dist2goal);

        return weight2goal;
    }

    public void setDistanceCalc(DistanceCalc distanceCalc) {
        this.distanceCalc = distanceCalc;
    }
}
