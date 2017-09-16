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
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 */
public abstract class AbstractWeighting implements Weighting {
    private final BooleanEncodedValue accessEnc;
    protected final DecimalEncodedValue averageSpeedEnc;
    private final String profile;
    protected final FlagEncoder flagEncoder;

    // TODO NOW remove FlagEncoder parameter
    protected AbstractWeighting(FlagEncoder encoder) {
        this.flagEncoder = encoder;
        this.profile = encoder.toString();
        if (!isValidName(getName()))
            throw new IllegalStateException("Not a valid name for a Weighting: " + getName());

        accessEnc = encoder.getBooleanEncodedValue(encoder.getPrefix() + "access");
        averageSpeedEnc = encoder.getDecimalEncodedValue(encoder.getPrefix() + "average_speed");
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        if (reverse && !edgeState.getReverse(accessEnc)
                || !reverse && !edgeState.get(accessEnc))
            throw new IllegalStateException("Calculating time should not require to read speed from edge in wrong direction. "
                    + "Reverse:" + reverse + ", fwd:" + edgeState.get(accessEnc) + ", bwd:" + edgeState.getReverse(accessEnc));

        double speed = reverse ? edgeState.getReverse(averageSpeedEnc) : edgeState.get(averageSpeedEnc);
        if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
            throw new IllegalStateException("Invalid speed stored in edge! " + speed);
        if (speed == 0)
            throw new IllegalStateException("Speed cannot be 0 for unblocked edge, use access properties to mark edge blocked! Should only occur for shortest path calculation. See #242.");

        return (long) (edgeState.getDistance() * 3600 / speed);
    }

    @Override
    public boolean matches(HintsMap reqMap) {
        return getName().equals(reqMap.getWeighting())
                && flagEncoder.toString().equals(reqMap.getVehicle());
    }

    @Override
    public EdgeFilter createEdgeFilter(boolean forward, boolean reverse) {
        return new DefaultEdgeFilter(accessEnc, forward, reverse);
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return flagEncoder;
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

    static final boolean isValidName(String name) {
        if (name == null || name.isEmpty())
            return false;

        return name.matches("[\\|_a-z]+");
    }

    /**
     * Replaces all characters which are not numbers, characters or underscores with underscores
     */
    public static String weightingToFileName(Weighting w) {
        return w.toString().toLowerCase().replaceAll("\\|", "_");
    }

    @Override
    public String toString() {
        return getName() + "|" + profile;
    }
}
