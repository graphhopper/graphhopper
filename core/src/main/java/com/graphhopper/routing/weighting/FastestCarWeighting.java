package com.graphhopper.routing.weighting;

import com.graphhopper.routing.profiles.DoubleProperty;
import com.graphhopper.routing.profiles.EncodingManager2;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;

public class FastestCarWeighting implements Weighting {

    private static final double SPEED_CONV = 3.6;
    private String profile = "car";
    private final DoubleProperty maxSpeed;
    private final double maxSpeedValue;

    public FastestCarWeighting(EncodingManager2 em) {
        maxSpeed = em.getProperty("maxspeed", DoubleProperty.class);
        // TODO
        // maxSpeedValue = maxSpeed.getMaximum() / SPEED_CONV;
        maxSpeedValue = 100 / SPEED_CONV;
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeedValue;
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        double speed = edgeState.get(maxSpeed) * 0.9;
        if (speed == 0)
            return Double.POSITIVE_INFINITY;

        return edgeState.getDistance() / speed * SPEED_CONV;

//            // add direction penalties at start/stop/via points
//            boolean unfavoredEdge = edge.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
//            if (unfavoredEdge)
//                time += headingPenalty;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        // TODO access
//            long flags = edgeState.getFlags();
//            if (reverse && !flagEncoder.isBackward(flags)
//                    || !reverse && !flagEncoder.isForward(flags))
//                throw new IllegalStateException("Calculating time should not require to read speed from edge in wrong direction. "
//                        + "Reverse:" + reverse + ", fwd:" + flagEncoder.isForward(flags) + ", bwd:" + flagEncoder.isBackward(flags));

        // TODO reverse
        // double speed = reverse ? flagEncoder.getReverseSpeed(flags) : flagEncoder.getSpeed(flags);
        double speed = edgeState.get(maxSpeed) * 0.9;
        if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
            throw new IllegalStateException("Invalid speed stored in edge! " + speed);
        if (speed == 0)
            throw new IllegalStateException("Speed cannot be 0 for unblocked edge, use access properties to mark edge blocked! Should only occur for shortest path calculation. See #242.");

        return (long) (edgeState.getDistance() * 3600 / speed);
    }

    @Override
    public boolean matches(HintsMap reqMap) {
        return getName().equals(reqMap.getWeighting())
                && profile.equals(reqMap.getVehicle());
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        throw new IllegalArgumentException("Cannot access flag encoder for new encoding mechanism");
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + toString().hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Weighting other = (Weighting) obj;
        return toString().equals(other.toString());
    }

    @Override
    public String getName() {
        return profile;
    }

    @Override
    public String toString() {
        return getName() + "|" + profile;
    }
}
