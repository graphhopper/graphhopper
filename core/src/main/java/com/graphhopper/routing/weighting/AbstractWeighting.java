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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.DefaultSpeedCalculator;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.SpeedCalculator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;

/**
 * @author Peter Karich
 */
public abstract class AbstractWeighting implements Weighting {
    protected final FlagEncoder flagEncoder;
    protected final DecimalEncodedValue avSpeedEnc;
    protected final BooleanEncodedValue accessEnc;
    private final TurnCostProvider turnCostProvider;
    // ORS-GH MOD START - additional field
    protected SpeedCalculator speedCalculator;
    // ORS-GH MOD END

    protected AbstractWeighting(FlagEncoder encoder) {
        this(encoder, NO_TURN_COST_PROVIDER);
    }

    protected AbstractWeighting(FlagEncoder encoder, TurnCostProvider turnCostProvider) {
        this.flagEncoder = encoder;
        if (!flagEncoder.isRegistered())
            throw new IllegalStateException("Make sure you add the FlagEncoder " + flagEncoder + " to an EncodingManager before using it elsewhere");
        if (!isValidName(getName()))
            throw new IllegalStateException("Not a valid name for a Weighting: " + getName());

        avSpeedEnc = encoder.getAverageSpeedEnc();
        accessEnc = encoder.getAccessEnc();
        this.turnCostProvider = turnCostProvider;
        // ORS-GH MOD START
        speedCalculator = new DefaultSpeedCalculator(encoder);
        // ORS_GH MOD END
    }

    // ORS-gh MOD - additional method
    // needed for time-dependent routing
    @Override
    public double calcEdgeWeight(EdgeIteratorState edge, boolean reverse, long edgeEnterTime) {
        return calcEdgeWeight(edge, reverse);
    }
    // ORS-GH MOD END

    /**
     * In most cases subclasses should only override this method to change the edge-weight. The turn cost handling
     * should normally be changed by passing another {@link TurnCostProvider} implementation to the constructor instead.
     */
    public abstract double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse);

    // ORS-GH MOD START - modifications for time-dependent routing
    // ORS-GH MOD - mimic old method signature
    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return calcEdgeMillis(edgeState, reverse, -1);
    }

    // ORS-GH MOD - add parameter to method signature
    // GH orig: public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse, long edgeEnterTime) {
        // ORS-GH MOD END
        // special case for loop edges: since they do not have a meaningful direction we always need to read them in
        // forward direction
        if (edgeState.getBaseNode() == edgeState.getAdjNode()) {
            reverse = false;
        }

        if (reverse && !edgeState.getReverse(accessEnc) || !reverse && !edgeState.get(accessEnc))
            throw new IllegalStateException("Calculating time should not require to read speed from edge in wrong direction. " +
                    "(" + edgeState.getBaseNode() + " - " + edgeState.getAdjNode() + ") "
                    + edgeState.fetchWayGeometry(FetchMode.ALL) + ", dist: " + edgeState.getDistance() + " "
                    + "Reverse:" + reverse + ", fwd:" + edgeState.get(accessEnc) + ", bwd:" + edgeState.getReverse(accessEnc) + ", fwd-speed: " + edgeState.get(avSpeedEnc) + ", bwd-speed: " + edgeState.getReverse(avSpeedEnc));

        // ORS-GH MOD START
        //double speed = reverse ? edgeState.getReverse(avSpeedEnc) : edgeState.get(avSpeedEnc);
        double speed = speedCalculator.getSpeed(edgeState, reverse, edgeEnterTime);
        // ORS-GH MOD END
        if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
            throw new IllegalStateException("Invalid speed stored in edge! " + speed);
        if (speed == 0)
            throw new IllegalStateException("Speed cannot be 0 for unblocked edge, use access properties to mark edge blocked! Should only occur for shortest path calculation. See #242.");

        return (long) (edgeState.getDistance() * 3600 / speed);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return turnCostProvider.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return turnCostProvider.calcTurnMillis(inEdge, viaNode, outEdge);
    }

    @Override
    public boolean hasTurnCosts() {
        return turnCostProvider != NO_TURN_COST_PROVIDER;
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

    static boolean isValidName(String name) {
        if (name == null || name.isEmpty())
            return false;

        return name.matches("[\\|_a-z]+");
    }

    @Override
    public String toString() {
        return getName() + "|" + flagEncoder;
    }

    // ORS-GH MOD START - additional methods
    @Override
    public boolean isTimeDependent() {
        return false;
    }

    @Override
    public SpeedCalculator getSpeedCalculator() {
        return speedCalculator;
    }

    @Override
    public void setSpeedCalculator(SpeedCalculator speedCalculator) {
        this.speedCalculator = speedCalculator;
    }
    // ORS-GH MOD END
}
