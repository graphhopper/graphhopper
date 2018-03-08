package com.graphhopper;

import com.graphhopper.Penalties.Penalty;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class WeightingWithPenalties extends FastestWeighting {
    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     * speed is in km / hr
     * distance is in meters convert it to km    ->   / 1000
     * so time is in hours -> convert it to sec  ->   * 60 * 60
     */
    private final static double SPEED_CONV = 3.6;
    private static final int MIN_TO_SEC = 60;
    private final double headingPenalty;
    private final long headingPenaltySec;
    private final Map<Integer, WayData> visitedEdgesCoordinates = new HashMap<>();
    private final Collection<Penalty> penalties;
    private static final Logger logger = LoggerFactory.getLogger(WeightingWithPenalties.class);


    public WeightingWithPenalties(FlagEncoder encoder, HintsMap hintsMap, Collection<Penalty> penalties) {
        super(encoder);
        this.headingPenalty = hintsMap.getDouble(Parameters.Routing.HEADING_PENALTY, Parameters.Routing.DEFAULT_HEADING_PENALTY);
        this.penalties = penalties;
        headingPenaltySec = Math.round(headingPenalty);
    }

    /**
     * This method calculates the weighting a certain edgeState should be associated. E.g. a high
     * value indicates that the edge should be avoided. Make sure that this method is very fast and
     * optimized as this is called potentially millions of times for one route or a lot more for
     * nearly any preprocessing phase.
     *
     * @param edge             the edge for which the weight should be calculated
     * @param reverse          if the specified edge is specified in reverse direction e.g. from the reverse
     *                         case of a bidirectional search.
     * @param prevOrNextEdgeId if reverse is false this has to be the previous edgeId, if true it
     *                         has to be the next edgeId in the direction from start to end.
     * @return the calculated weight with the specified velocity has to be in the range of 0 and
     * +Infinity. Make sure your method does not return NaN which can e.g. occur for 0/0.
     */
    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        long flags = edge.getFlags();

        double speed = reverse ? super.flagEncoder.getReverseSpeed(flags) : super.flagEncoder.getSpeed(flags);// km/h
        if (speed == 0) {
            return Double.POSITIVE_INFINITY;
        }

        double distance = edge.getDistance(); //in meters
        double time = distance / speed * SPEED_CONV; //sec

        boolean unfavoredEdge = edge.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
        if (unfavoredEdge) {
            time += headingPenalty;
        }

        return time + updateVisitedEdgesAndGetPenalty(edge, reverse, prevOrNextEdgeId);
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        long time = 0;
        boolean unfavoredEdge = edgeState.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
        if (unfavoredEdge) {
            time += headingPenaltySec * 1000;
        }

        final double penalty = updateVisitedEdgesAndGetPenalty(edgeState, reverse, prevOrNextEdgeId) * 1000;
        return (long) (time + penalty + super.calcMillis(edgeState, reverse, prevOrNextEdgeId));
    }

    @Override
    public String getName() {
        return "weighting_with_penalties";
    }


    public static final class WayData {
        public final double firstWayPointLat;
        public final double firstWayPointLng;
        public final double lastWayPointLat;
        public final double lastWayPointLng;

        public WayData(double firstWayPointLat, double firstWayPointLng, double lastWayPointLat, double lastWayPointLng) {
            this.firstWayPointLat = firstWayPointLat;
            this.firstWayPointLng = firstWayPointLng;
            this.lastWayPointLat = lastWayPointLat;
            this.lastWayPointLng = lastWayPointLng;
        }
    }

    private double updateVisitedEdgesAndGetPenalty(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {

        PointList pointList = edge.fetchWayGeometry(3);
        WayData wayData = new WayData(
                pointList.getLat(0),
                pointList.getLon(0),
                pointList.getLat(pointList.size() - 1),
                pointList.getLon(pointList.size() - 1));
        visitedEdgesCoordinates.put(edge.getEdge(), wayData);
        WayData prevWayData;
        if (reverse) { //if reverse is true prevOrNextEdgeId has to be the next edgeId in the direction from start to end.
            prevWayData = wayData;
            wayData = visitedEdgesCoordinates.get(prevOrNextEdgeId);
        } else {
            prevWayData = visitedEdgesCoordinates.get(prevOrNextEdgeId);
        }

        double penaltyCost = .0;
        for (Penalty penalty : penalties) {
            penaltyCost += penalty.getPenalty(edge, reverse, prevOrNextEdgeId, prevWayData, wayData);
        }
        return penaltyCost;
    }
}