package com.graphhopper.routing.util;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Class uses Curvature Data to prefer curvy routes.
 */
public class CurvatureWeighting extends PriorityWeighting
{

    private static final Logger logger = LoggerFactory.getLogger(CurvatureWeighting.class);

    private final FlagEncoder flagEncoder;
    private final NodeAccess nodeAccess;

    private final DistanceCalc distCalc = Helper.DIST_EARTH;

    public CurvatureWeighting( FlagEncoder flagEncoder, PMap pMap, GraphHopperStorage ghStorage )
    {
        super(flagEncoder, pMap);
        this.flagEncoder = flagEncoder;
        this.nodeAccess = ghStorage.getNodeAccess();
    }

    @Override
    public double getMinWeight( double distance )
    {
        return 0.1 * distance;
    }

    @Override
    public double calcWeight( EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId )
    {

        double priority = flagEncoder.getDouble(edge.getFlags(), KEY);
        double bendiness = flagEncoder.getDouble(edge.getFlags(), MotorcycleFlagEncoder.CURVATURE_KEY);
        double speed = getRoadSpeed(edge, reverse);
        double roadDistance = edge.getDistance();

        // We use the log of the speed to decrease the impact of the speed, therefore we don't use the highway
        double regularWeight = (roadDistance / Math.log(speed));

        return (bendiness * regularWeight) / (0.5 + priority);
    }

    protected double getRoadSpeed( EdgeIteratorState edge, boolean reverse )
    {
        return reverse ? flagEncoder.getReverseSpeed(edge.getFlags()) : flagEncoder.getSpeed(edge.getFlags());
    }

    @Override
    public FlagEncoder getFlagEncoder()
    {
        return flagEncoder;
    }

    @Override
    public String toString()
    {
        return "CURVATURE";
    }

}