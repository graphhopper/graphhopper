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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.querygraph.VirtualEdgeIterator;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;

/**
 * The CustomWeighting allows adjusting the edge weights relative to those we'd obtain for a given base flag encoder.
 * For example a car flag encoder already provides speeds and access flags for every edge depending on certain edge
 * properties. By default the CustomWeighting simply makes use of these values, but it is possible to adjust them by
 * setting up rules that apply changes depending on the edges' encoded values.
 * <p>
 * The formula for the edge weights is as follows:
 * <p>
 * weight = distance/speed + distance_costs + stress_costs
 * <p>
 * The first term simply corresponds to the time it takes to travel along the edge.
 * The second term adds a fixed per-distance cost that is proportional to the distance but *independent* of the edge
 * properties, i.e. it reads
 * <p>
 * distance_costs = distance * distance_influence
 * <p>
 * The third term is also proportional to the distance but compared to the second it describes additional costs that *do*
 * depend on the edge properties. It can represent any kind of costs that depend on the edge (like inconvenience or
 * dangers encountered on 'high-stress' roads for bikes, toll roads (because they cost money), stairs (because they are
 * awkward when going by bike) etc.). This 'stress' term reads
 * <p>
 * stress_costs = distance * stress_per_meter
 * <p>
 * and just like the distance term it describes costs measured in seconds. When modelling it, one always has to 'convert'
 * the costs into some time equivalent (e.g. for toll roads one has to think about how much money can be spent to save
 * a certain amount of time). Note that the distance_costs described by the second term in general cannot be properly
 * described by the stress costs, because the distance term allows increasing the per-distance costs per-se (regardless
 * of the type of the road). Also note that both the second and third term are different to the first in that they can
 * increase the edge costs but do *not* modify the travel *time*.
 * <p>
 * Instead of letting you set the speed directly, `CustomWeighting` allows changing the speed relative to the speed we
 * get from the base flag encoder. The stress costs can be specified by using a factor between 0 and 1 that is called
 * 'priority'.
 * <p>
 * Therefore the full edge weight formula reads:
 * <pre>
 * weight = distance / (base_speed * speed_factor * priority)
 *        + distance * distance_influence
 * </pre>
 * <p>
 * The open parameters that we can adjust are therefore: speed_factor, priority and distance_influence and they are
 * specified via the `{@link CustomModel}`. The speed can also be restricted to a maximum value, in which case the value
 * calculated via the speed_factor is simply overwritten. Edges that are not accessible according to the access flags of
 * the base vehicle always get assigned an infinite weight and this cannot be changed (yet) using this weighting.
 */
public final class CustomWeighting implements Weighting {
    public static final String NAME = "custom";

    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
    private final static double SPEED_CONV = 3.6;
    private final double distanceInfluence;
    private final double headingPenaltySeconds;
    private final EdgeToDoubleMapping edgeToSpeedMapping;
    private final EdgeToDoubleMapping edgeToPriorityMapping;
    private final TurnCostProvider turnCostProvider;
    private final MaxCalc maxPrioCalc;
    private final MaxCalc maxSpeedCalc;

    public CustomWeighting(TurnCostProvider turnCostProvider, Parameters parameters) {
        if (!Weighting.isValidName(getName()))
            throw new IllegalStateException("Not a valid name for a Weighting: " + getName());
        this.turnCostProvider = turnCostProvider;

        this.edgeToSpeedMapping = parameters.getEdgeToSpeedMapping();
        this.maxSpeedCalc = parameters.getMaxSpeedCalc();

        this.edgeToPriorityMapping = parameters.getEdgeToPriorityMapping();
        this.maxPrioCalc = parameters.getMaxPrioCalc();

        this.headingPenaltySeconds = parameters.getHeadingPenaltySeconds();

        // given unit is s/km -> convert to s/m
        this.distanceInfluence = parameters.getDistanceInfluence() / 1000.0;
        if (this.distanceInfluence < 0)
            throw new IllegalArgumentException("distance_influence cannot be negative " + this.distanceInfluence);
    }

    @Override
    public double calcMinWeightPerDistance() {
        return 1d / (maxSpeedCalc.calcMax() / SPEED_CONV) / maxPrioCalc.calcMax() + distanceInfluence;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
//        if (edgeState.isVirtual())
//            throw new IllegalStateException("Edge state must not be virtual. You need to use graph.wrapWeighting");
        double priority = edgeToPriorityMapping.get(edgeState, reverse);
        if (priority == 0) return Double.POSITIVE_INFINITY;

        final double distance = edgeState.getDistance();
        double seconds = calcSeconds(distance, edgeState, reverse);
        if (Double.isInfinite(seconds)) return Double.POSITIVE_INFINITY;
        // add penalty at start/stop/via points
        if (edgeState.get(EdgeIteratorState.UNFAVORED_EDGE)) seconds += headingPenaltySeconds;
        double distanceCosts = distance * distanceInfluence;
        if (Double.isInfinite(distanceCosts)) return Double.POSITIVE_INFINITY;
        return seconds / priority + distanceCosts;
    }

    double calcSeconds(double distance, EdgeIteratorState edgeState, boolean reverse) {
        double speed = edgeToSpeedMapping.get(edgeState, reverse);
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative");

        return distance / speed * SPEED_CONV;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return Math.round(calcSeconds(edgeState.getDistance(), edgeState, reverse) * 1000);
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
    public String getName() {
        return NAME;
    }

    @FunctionalInterface
    public interface EdgeToDoubleMapping {
        double get(EdgeIteratorState edge, boolean reverse);
    }

    public interface TurnPenaltyMapping {
        double get(BaseGraph graph, EdgeIntAccess edgeIntAccess, int inEdge, int viaNode, int outEdge);
    }

    @FunctionalInterface
    public interface MaxCalc {
        double calcMax();
    }

    public static class Parameters {
        private final EdgeToDoubleMapping edgeToSpeedMapping;
        private final EdgeToDoubleMapping edgeToPriorityMapping;
        private final MaxCalc maxSpeedCalc;
        private final MaxCalc maxPrioCalc;
        private final TurnPenaltyMapping turnPenaltyMapping;
        private final double distanceInfluence;
        private final double headingPenaltySeconds;

        public Parameters(EdgeToDoubleMapping edgeToSpeedMapping, MaxCalc maxSpeedCalc,
                          EdgeToDoubleMapping edgeToPriorityMapping, MaxCalc maxPrioCalc,
                          TurnPenaltyMapping turnPenaltyMapping,
                          double distanceInfluence, double headingPenaltySeconds) {
            this.edgeToSpeedMapping = edgeToSpeedMapping;
            this.maxSpeedCalc = maxSpeedCalc;
            this.edgeToPriorityMapping = edgeToPriorityMapping;
            this.maxPrioCalc = maxPrioCalc;
            this.turnPenaltyMapping = turnPenaltyMapping;
            this.distanceInfluence = distanceInfluence;
            this.headingPenaltySeconds = headingPenaltySeconds;
        }

        public EdgeToDoubleMapping getEdgeToSpeedMapping() {
            return edgeToSpeedMapping;
        }

        public EdgeToDoubleMapping getEdgeToPriorityMapping() {
            return edgeToPriorityMapping;
        }

        public MaxCalc getMaxSpeedCalc() {
            return maxSpeedCalc;
        }

        public MaxCalc getMaxPrioCalc() {
            return maxPrioCalc;
        }

        public TurnPenaltyMapping getTurnPenaltyMapping() {
            return turnPenaltyMapping;
        }

        public double getDistanceInfluence() {
            return distanceInfluence;
        }

        public double getHeadingPenaltySeconds() {
            return headingPenaltySeconds;
        }
    }
}
