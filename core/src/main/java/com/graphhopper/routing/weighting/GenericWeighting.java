/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.util.ConfigMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters.Routing;

/**
 * Calculates the best route according to a configurable weighting.
 * <p>
 *
 * @author Peter Karich
 */
public class GenericWeighting extends AbstractWeighting {
    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
    protected final static double SPEED_CONV = 3.6;
    private final double headingPenalty;
    private final long headingPenaltyMillis;
    private final double maxSpeed;
    private final DataFlagEncoder gEncoder;
    private final double[] speedArray;
    private final int accessType;

    public GenericWeighting(DataFlagEncoder encoder, ConfigMap cMap) {
        super(encoder);
        gEncoder = encoder;
        headingPenalty = cMap.getDouble(Routing.HEADING_PENALTY, Routing.DEFAULT_HEADING_PENALTY);
        headingPenaltyMillis = Math.round(headingPenalty * 1000);

        speedArray = gEncoder.getHighwaySpeedMap(cMap.getMap("highways", Double.class));
        double tmpSpeed = 0;
        for (double speed : speedArray) {
            if (speed > tmpSpeed)
                tmpSpeed = speed;
        }
        if (tmpSpeed > encoder.getMaxPossibleSpeed())
            throw new IllegalArgumentException("Speed bigger than maximum speed: " + tmpSpeed + " > " + encoder.getMaxPossibleSpeed());

        maxSpeed = tmpSpeed / SPEED_CONV;
        accessType = gEncoder.getAccessType("motor_vehicle");
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed;
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        // handle oneways and removed edges via subnetwork removal (existing and allowed highway tags but 'island' edges)
        if (reverse) {
            if (!gEncoder.isBackward(edgeState, accessType))
                return Double.POSITIVE_INFINITY;
        } else if (!gEncoder.isForward(edgeState, accessType))
            return Double.POSITIVE_INFINITY;

        long time = calcMillis(edgeState, reverse, prevOrNextEdgeId);
        if (time == Long.MAX_VALUE)
            return Double.POSITIVE_INFINITY;
        return time;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        // TODO to avoid expensive reverse flags include oneway accessibility
        // but how to include e.g. maxspeed as it depends on direction? Does highway depend on direction?
        // reverse = edge.isReverse()? !reverse : reverse;
        int highwayVal = gEncoder.getHighway(edgeState);
        double speed = speedArray[highwayVal];
        if (speed < 0)
            throw new IllegalStateException("speed was negative? " + edgeState.getEdge()
                    + ", highway:" + highwayVal + ", reverse:" + reverse);
        if (speed == 0)
            return Long.MAX_VALUE;

        // TODO inner city guessing -> lit, maxspeed <= 50, residential etc => create new encoder.isInnerCity(edge)
        // See #472 use edge.getDouble((encoder), K_MAXSPEED_MOTORVEHICLE_FORWARD, _default) or edge.getMaxSpeed(...) instead?
        // encoder could be made optional via passing to EdgeExplorer
        double maxspeed = gEncoder.getMaxspeed(edgeState, accessType, reverse);
        if (maxspeed > 0 && speed > maxspeed)
            speed = maxspeed;

        // TODO test performance difference for rounding
        long timeInMillis = (long) (edgeState.getDistance() / speed * SPEED_CONV);

        // add direction penalties at start/stop/via points
        boolean unfavoredEdge = edgeState.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
        if (unfavoredEdge)
            timeInMillis += headingPenaltyMillis;

        // TODO avoid a certain (or multiple) bounding boxes (less efficient for just a few edges) or a list of edgeIDs (not good for large areas)
        // bbox.contains(nodeAccess.getLatitude(edge.getBaseNode()), nodeAccess.getLongitude(edge.getBaseNode())) time+=avoidPenalty;
        // TODO surfaces can reduce average speed
        // TODO prefer or avoid bike and hike routes
        if (timeInMillis < 0)
            throw new IllegalStateException("Some problem with weight calculation: time:"
                    + timeInMillis + ", speed:" + speed);

        return timeInMillis;
    }

    @Override
    public String getName() {
        return "generic";
    }
}
