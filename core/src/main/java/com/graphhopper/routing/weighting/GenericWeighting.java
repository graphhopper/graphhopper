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

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters.Routing;

import static com.graphhopper.util.EdgeIteratorState.UNFAVORED_EDGE;

/**
 * Calculates the best route according to a configurable weighting.
 *
 * @author Peter Karich
 */
public class GenericWeighting extends AbstractWeighting {

    public static final String HEIGHT_LIMIT = "height";
    public static final String WEIGHT_LIMIT = "weight";
    public static final String WIDTH_LIMIT = "width";
    /**
     * Convert to milliseconds for correct calcMillis.
     */
    protected final static double SPEED_CONV = 3600;
    protected final double headingPenalty;
    protected final long headingPenaltyMillis;
    protected final double maxSpeed;
    protected final DataFlagEncoder gEncoder;
    protected final DataFlagEncoder.WeightingConfig weightingConfig;
    protected final int uncertainAccessiblePenalty = 10;

    protected final double height;
    protected final double weight;
    protected final double width;
    private final IntEncodedValue accessClassEnc;
    private final BooleanEncodedValue accessEnc;
    private final DecimalEncodedValue maxSpeedEnc;
    private DecimalEncodedValue maxWeightEnc;
    private DecimalEncodedValue maxWidthEnc;
    private DecimalEncodedValue maxHeightEnc;

    public GenericWeighting(DataFlagEncoder encoder, PMap hintsMap) {
        super(encoder);
        gEncoder = encoder;
        headingPenalty = hintsMap.getDouble(Routing.HEADING_PENALTY, Routing.DEFAULT_HEADING_PENALTY);
        headingPenaltyMillis = Math.round(headingPenalty * 1000);

        weightingConfig = encoder.createWeightingConfig(hintsMap);
        double maxSpecifiedSpeed = weightingConfig.getMaxSpecifiedSpeed();
        if (maxSpecifiedSpeed > encoder.getMaxPossibleSpeed())
            throw new IllegalArgumentException("Some specified speed value bigger than maximum possible speed: " + maxSpecifiedSpeed + " > " + encoder.getMaxPossibleSpeed());

        this.maxSpeed = maxSpecifiedSpeed / SPEED_CONV;

        height = hintsMap.getDouble(HEIGHT_LIMIT, 0d);
        // TODO NOW replace with lookup.hasEncodedValue(key)?
        if (encoder.isStoreHeight()) maxHeightEnc = gEncoder.getDecimalEncodedValue(TagParserFactory.MAX_HEIGHT);

        weight = hintsMap.getDouble(WEIGHT_LIMIT, 0d);
        if (encoder.isStoreWeight()) maxWeightEnc = gEncoder.getDecimalEncodedValue(TagParserFactory.MAX_WEIGHT);

        width = hintsMap.getDouble(WIDTH_LIMIT, 0d);
        if (encoder.isStoreWidth()) maxWidthEnc = gEncoder.getDecimalEncodedValue(TagParserFactory.MAX_WIDTH);

        // TODO select the correct access type and max_speed via a provided access type (car, motor_vehicle, bike, ...) instead of encoder prefix
        // String prefix = hintsMap.get("access_type") + ".";

        // ugly: misusing average speed to store maximum values
        maxSpeedEnc = gEncoder.getDecimalEncodedValue(gEncoder.getPrefix() + "average_speed");
        accessEnc = gEncoder.getBooleanEncodedValue(gEncoder.getPrefix() + "access");
        accessClassEnc = gEncoder.getIntEncodedValue("access_class");
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed;
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        // handle oneways and removed edges via subnetwork removal (existing and allowed highway tags but 'island' edges)
        if (reverse && !edge.getReverse(accessEnc) || !reverse && !edge.get(accessEnc))
            return Double.POSITIVE_INFINITY;

        if (overLimit(edge, height, maxHeightEnc)
                || overLimit(edge, weight, maxWeightEnc)
                || overLimit(edge, width, maxWidthEnc))
            return Double.POSITIVE_INFINITY;

        long time = calcMillis(edge, reverse, prevOrNextEdgeId);
        if (time == Long.MAX_VALUE)
            return Double.POSITIVE_INFINITY;

        // add direction penalties at start/stop/via points
        boolean unfavoredEdge = edge.get(UNFAVORED_EDGE);
        if (unfavoredEdge)
            time += headingPenalty;

        switch (gEncoder.getAccess(edge)) {
            case NO:
                return Double.POSITIVE_INFINITY;
            case CONDITIONAL:
                time = time * uncertainAccessiblePenalty;
            default:
                // ignore
        }

        return time;
    }

    private boolean overLimit(EdgeIteratorState edge, double value, DecimalEncodedValue valueEnc) {
        if (valueEnc == null)
            return false;

        return value >= edge.get(valueEnc);
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        // TODO to avoid expensive reverse flags include oneway accessibility
        // but how to include e.g. maxspeed as it depends on direction? Does highway depend on direction?
        // reverse = edge.isReverse()? !reverse : reverse;
        double speed = weightingConfig.getSpeed(edgeState);
        if (speed == 0)
            return Long.MAX_VALUE;

        // TODO inner city guessing -> lit, maxspeed <= 50, residential etc => create new encoder.isInnerCity(edge)
        // See #472 use edge.getDouble((encoder), K_MAXSPEED_MOTORVEHICLE_FORWARD, _default) or edge.getMaxSpeed(...) instead?
        // encoder could be made optional via passing to EdgeExplorer
        double maxspeed = reverse ? edgeState.getReverse(maxSpeedEnc) : edgeState.get(maxSpeedEnc);
        if (maxspeed > 0 && speed > maxspeed)
            speed = maxspeed;

        // TODO test performance difference for rounding
        long timeInMillis = (long) (edgeState.getDistance() / speed * SPEED_CONV);

        // add direction penalties at start/stop/via points
        boolean unfavoredEdge = edgeState.get(EdgeIteratorState.UNFAVORED_EDGE);
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
