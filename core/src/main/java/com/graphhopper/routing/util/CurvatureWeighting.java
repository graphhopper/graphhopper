package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.reader.PillarInfo;
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

    private final FlagEncoder flagEncoder;
    private final double maxSpeed;
    private final Set<Integer> curvyEdges;
    private final NodeAccess nodeAccess;
    private final PillarInfo pillarInfo;
    GraphHopperStorage ghStorage;

    protected static final int EMPTY = -1;
    // pillar node is >= 3
    protected static final int PILLAR_NODE = 1;
    // tower node is <= -3
    protected static final int TOWER_NODE = -2;
    private static final Logger logger = LoggerFactory.getLogger(CurvatureWeighting.class);

    private final DistanceCalc distCalc = Helper.DIST_EARTH;

    public CurvatureWeighting(FlagEncoder flagEncoder, Set<Integer> curvyEdges, GraphHopperStorage ghStorage) {
        this.flagEncoder = flagEncoder;
        this.maxSpeed = flagEncoder.getMaxSpeed();
        this.curvyEdges = curvyEdges;
        this.ghStorage = ghStorage;

        this.nodeAccess = ghStorage.getNodeAccess();
        this.pillarInfo = new PillarInfo(nodeAccess.is3D(), ghStorage.getDirectory());

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

        double roadLength = edge.getDistance();
        double distance = calcDist(edge);

        double weight = (roadLength/(speed*distance))*100;

        logger.info("Calculated a CurvatureWeighting of " + weight + " using the roadLenght of " + roadLength + " the speed of " + speed + " and the distance of " + distance);

        return weight;

    }

    private double calcDist(EdgeIteratorState edge){
        int baseNode = edge.getBaseNode();
        double firstLat = getTmpLatitude(edge.getBaseNode()), firstLon = getTmpLongitude(edge.getBaseNode());
        double lastLat = getTmpLatitude(edge.getAdjNode()), lastLon = getTmpLongitude(edge.getAdjNode());
        double straight_line = distCalc.calcNormalizedDist(firstLat, firstLon, lastLat, lastLon);

        return distCalc.calcDenormalizedDist(straight_line);
    }

    // TODO remove this ugly stuff via better preparsing phase! E.g. putting every tags etc into a helper file!
    private double getTmpLatitude( int id )
    {
            return nodeAccess.getLatitude(id);
    }

    private double getTmpLongitude( int id )
    {
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