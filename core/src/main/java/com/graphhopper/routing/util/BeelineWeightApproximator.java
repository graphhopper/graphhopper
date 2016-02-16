package com.graphhopper.routing.util;

import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;

/**
 * Approximates the distance to the goal node by weighting the beeline distance according to the
 * distance weighting
 * <p>
 * @author jansoe
 */
public class BeelineWeightApproximator implements WeightApproximator
{
    private final NodeAccess nodeAccess;
    private final Weighting weighting;
    private DistanceCalc distanceCalc = Helper.DIST_EARTH;
    private double toLat, toLon;
    private double epsilon = 1;

    public BeelineWeightApproximator( NodeAccess nodeAccess, Weighting weighting )
    {
        this.nodeAccess = nodeAccess;
        this.weighting = weighting;
    }

    @Override
    public void setGoalNode( int toNode )
    {
        toLat = nodeAccess.getLatitude(toNode);
        toLon = nodeAccess.getLongitude(toNode);
    }

    public WeightApproximator setEpsilon( double epsilon )
    {
        this.epsilon = epsilon;
        return this;
    }

    @Override
    public WeightApproximator duplicate()
    {
        return new BeelineWeightApproximator(nodeAccess, weighting).setDistanceCalc(distanceCalc).setEpsilon(epsilon);
    }

    @Override
    public double approximate( int fromNode )
    {
        double fromLat = nodeAccess.getLatitude(fromNode);
        double fromLon = nodeAccess.getLongitude(fromNode);
        double dist2goal = distanceCalc.calcDist(toLat, toLon, fromLat, fromLon);
        double weight2goal = weighting.getMinWeight(dist2goal);
        return weight2goal * epsilon;
    }

    public BeelineWeightApproximator setDistanceCalc( DistanceCalc distanceCalc )
    {
        this.distanceCalc = distanceCalc;
        return this;
    }
}
