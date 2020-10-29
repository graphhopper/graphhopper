package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.EdgeIteratorState;
import org.codehaus.janino.*;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CustomWeighting extends AbstractWeighting {
    public static final String NAME = "custom";

    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
    private final static double SPEED_CONV = 3.6;
    private final BooleanEncodedValue baseVehicleAccessEnc;
    private final double maxSpeed;
    private final double distanceInfluence;
    private final double headingPenaltySeconds;
    private final ScriptHelper scriptHelper;

    public CustomWeighting(FlagEncoder baseFlagEncoder, EncodedValueLookup lookup,
                           TurnCostProvider turnCostProvider, CustomModel customModel) {
        super(baseFlagEncoder, turnCostProvider);
        if (customModel == null)
            throw new IllegalStateException("CustomModel cannot be null");

        headingPenaltySeconds = customModel.getHeadingPenalty();
        baseVehicleAccessEnc = baseFlagEncoder.getAccessEnc();

        scriptHelper = ScriptHelper.create(customModel, lookup, baseFlagEncoder.getMaxSpeed(), baseFlagEncoder.getAverageSpeedEnc());
        maxSpeed = baseFlagEncoder.getMaxSpeed() / SPEED_CONV;

        // given unit is s/km -> convert to s/m
        distanceInfluence = customModel.getDistanceInfluence() / 1000;
        if (distanceInfluence < 0)
            throw new IllegalArgumentException("maximum distance_influence cannot be negative " + distanceInfluence);
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed + distance * distanceInfluence;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        final double distance = edgeState.getDistance();
        double seconds = calcSeconds(distance, edgeState, reverse);
        if (Double.isInfinite(seconds))
            return Double.POSITIVE_INFINITY;
        double distanceCosts = distance * distanceInfluence;
        if (Double.isInfinite(distanceCosts))
            return Double.POSITIVE_INFINITY;
        return seconds / scriptHelper.getPriority(edgeState, reverse) + distanceCosts;
    }

    double calcSeconds(double distance, EdgeIteratorState edgeState, boolean reverse) {
        // special case for loop edges: since they do not have a meaningful direction we always need to read them in forward direction
        if (edgeState.getBaseNode() == edgeState.getAdjNode())
            reverse = false;

        // TODO see #1835
        if (reverse ? !edgeState.getReverse(baseVehicleAccessEnc) : !edgeState.get(baseVehicleAccessEnc))
            return Double.POSITIVE_INFINITY;

        double speed = scriptHelper.getSpeed(edgeState, reverse);
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative");

        double seconds = distance / speed * SPEED_CONV;
        // add penalty at start/stop/via points
        return edgeState.get(EdgeIteratorState.UNFAVORED_EDGE) ? seconds + headingPenaltySeconds : seconds;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return Math.round(calcSeconds(edgeState.getDistance(), edgeState, reverse) * 1000);
    }

    @Override
    public String getName() {
        return NAME;
    }
}