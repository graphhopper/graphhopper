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
        return 0.001 * distance;
    }

    @Override
    public double calcWeight( EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId )
    {

        double speed = getRoadSpeed(edge, reverse);
        double roadDistance = edge.getDistance();
        double beelineDistance = calcBeelineDist(edge);
        double bendiness = beelineDistance / roadDistance;
        double priority = flagEncoder.getDouble(edge.getFlags(), KEY);

        priority = discriminateSlowStreets(priority, speed);

        bendiness = correctErrors(bendiness);
        bendiness = increaseBendinessImpact(bendiness);

        // We use the log of the speed to decrease the impact of the speed, therefore we don't use the highway
        double regularWeight = (roadDistance / Math.log(speed));

        return (bendiness * regularWeight) / (0.5 + priority);
    }

    protected double getRoadSpeed( EdgeIteratorState edge, boolean reverse )
    {
        return reverse ? flagEncoder.getReverseSpeed(edge.getFlags()) : flagEncoder.getSpeed(edge.getFlags());
    }

    /**
     * Streets that slow are not fun and probably in a town.
     */
    protected double discriminateSlowStreets( double priority, double speed )
    {
        if (speed < 51)
        {
            return 1 + priority;
        }
        return priority;
    }

    /**
     * A really small bendiness or a bendiness greater than 1 indicates an error in the calculation. Just ignore them.
     * We use bendiness > 1.2 since the beelineDistance is only approximated,
     * therefore it can happen on straight roads, that the beeline is longer than the road.
     */
    protected double correctErrors( double bendiness )
    {
        if (bendiness < 0.01 || bendiness > 1.2)
        {
            logger.info("Corrected a bendiness of: " + bendiness);
            return 1;
        }
        return bendiness;
    }

    /**
     * A good bendiness should become a greater impact. A bendiness close to 1 should not be changed.
     */
    protected double increaseBendinessImpact( double bendiness )
    {
        return (Math.pow(bendiness, 2));
    }

    protected double calcBeelineDist( EdgeIteratorState edge )
    {
        try
        {
            double firstLat = getTmpLatitude(edge.getBaseNode()), firstLon = getTmpLongitude(edge.getBaseNode());
            double lastLat = getTmpLatitude(edge.getAdjNode()), lastLon = getTmpLongitude(edge.getAdjNode());
            double straight_line = distCalc.calcNormalizedDist(firstLat, firstLon, lastLat, lastLon);

            return distCalc.calcDenormalizedDist(straight_line);
        } catch (Exception e)
        {
            logger.error("Unable to calculate Distance for the Edge: " + edge);
            return edge.getDistance();
        }

    }

    protected double getTmpLatitude( int id )
    {
        return nodeAccess.getLatitude(id);
    }

    protected double getTmpLongitude( int id )
    {
        return nodeAccess.getLongitude(id);
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