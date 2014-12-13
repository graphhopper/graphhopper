package com.graphhopper.routing.util;

import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.DistancePlaneProjection;

/**
 * Approximates the distance to the goalNode by weighting the beeline distance according to the distance weighting
 * @author jansoe
 */
public class BeelineWeightApproximator implements WeightApproximator {

    private NodeAccess nodeAccess;
    private Weighting weighting;
    private DistanceCalc distanceCalc;
    double toLat, toLon;

    public BeelineWeightApproximator(NodeAccess nodeAccess, Weighting weighting) {
        this.nodeAccess = nodeAccess;
        this.weighting = weighting;
        setDistanceCalc(new DistanceCalcEarth());
    }

    public void setGoalNode(int toNode){
        toLat = nodeAccess.getLatitude(toNode);
        toLon = nodeAccess.getLongitude(toNode);
    }

    @Override
    public WeightApproximator duplicate() {
        return new BeelineWeightApproximator(nodeAccess, weighting).setDistanceCalc(distanceCalc);
    }


    @Override
    public double approximate(int fromNode) {

        double fromLat, fromLon, dist2goal, weight2goal;
        fromLat  = nodeAccess.getLatitude(fromNode);
        fromLon = nodeAccess.getLongitude(fromNode);
        dist2goal = distanceCalc.calcDist(toLat, toLon, fromLat, fromLon);
        weight2goal = weighting.getMinWeight(dist2goal);

        return weight2goal;
    }

    public BeelineWeightApproximator setDistanceCalc(DistanceCalc distanceCalc) {
        this.distanceCalc = distanceCalc;
        return this;
    }
}
