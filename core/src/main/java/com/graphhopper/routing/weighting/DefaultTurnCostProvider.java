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
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.TurnCostsConfig;

import static com.graphhopper.util.AngleCalc.ANGLE_CALC;
import static com.graphhopper.util.TurnCostsConfig.INFINITE_U_TURN_COSTS;

public class DefaultTurnCostProvider implements TurnCostProvider {
    private final BooleanEncodedValue turnRestrictionEnc;
    private final TurnCostStorage turnCostStorage;
    private final int uTurnCostsInt;
    private final double uTurnCosts;

    public DefaultTurnCostProvider(BooleanEncodedValue turnRestrictionEnc, TurnCostStorage turnCostStorage) {
        this(turnRestrictionEnc, turnCostStorage, TurnCostsConfig.INFINITE_U_TURN_COSTS);
    }

    /**
     * @param uTurnCosts the costs of a u-turn in seconds, for {@link TurnCostsConfig#INFINITE_U_TURN_COSTS} the u-turn costs
     *                   will be infinite
     */
    public DefaultTurnCostProvider(BooleanEncodedValue turnRestrictionEnc, TurnCostStorage turnCostStorage, int uTurnCosts) {
        if (uTurnCosts < 0 && uTurnCosts != INFINITE_U_TURN_COSTS) {
            throw new IllegalArgumentException("u-turn costs must be positive, or equal to " + INFINITE_U_TURN_COSTS + " (=infinite costs)");
        }
        this.uTurnCostsInt = uTurnCosts;
        this.uTurnCosts = uTurnCosts < 0 ? Double.POSITIVE_INFINITY : uTurnCosts;
        if (turnCostStorage == null) {
            throw new IllegalArgumentException("No storage set to calculate turn weight");
        }
        // if null the TurnCostProvider can be still useful for edge-based routing
        this.turnRestrictionEnc = turnRestrictionEnc;
        this.turnCostStorage = turnCostStorage;
    }

    public BooleanEncodedValue getTurnRestrictionEnc() {
        return turnRestrictionEnc;
    }

    @Override
    public double calcTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
        if (!EdgeIterator.Edge.isValid(edgeFrom) || !EdgeIterator.Edge.isValid(edgeTo)) {
            return 0;
        }
        double tCost = 0;
        if (edgeFrom == edgeTo) {
            // note that the u-turn costs overwrite any turn costs set in TurnCostStorage
            tCost = uTurnCosts;
        } else {
            if (turnRestrictionEnc != null)
                tCost = turnCostStorage.get(turnRestrictionEnc, edgeFrom, nodeVia, edgeTo) ? Double.POSITIVE_INFINITY : 0;
        }
        return tCost;
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return (long) (1000 * calcTurnWeight(inEdge, viaNode, outEdge));
    }

    @Override
    public String toString() {
        return "default_tcp_" + uTurnCostsInt;
    }

    public static TurnCostProvider createFromConfig(TurnCostProvider turnCostProvider,
                                                    DecimalEncodedValue orientationEnc,
                                                    Graph graph, TurnCostsConfig tcConfig) {
        return new TurnCostProvider() {

            final double minRightInRad = Math.toRadians(tcConfig.getMinRightAngle());
            final double maxRightInRad = Math.toRadians(tcConfig.getMaxRightAngle());
            final double minLeftInRad = Math.toRadians(tcConfig.getMinLeftAngle());
            final double maxLeftInRad = Math.toRadians(tcConfig.getMaxLeftAngle());
            final double rightCost = tcConfig.getRightCost();
            final double leftCost = tcConfig.getLeftCost();
            final double straightCost = tcConfig.getStraightCost();
            final BaseGraph baseGraph = graph.getBaseGraph();
            final EdgeIntAccess edgeIntAccess = graph.getBaseGraph().getEdgeAccess();

            @Override
            public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
                double weight = turnCostProvider.calcTurnWeight(inEdge, viaNode, outEdge);
                if (Double.isInfinite(weight)) return weight;
                double changeAngle = calcChangeAngle(inEdge, viaNode, outEdge, baseGraph, edgeIntAccess, orientationEnc);
                if (changeAngle > minRightInRad && changeAngle < minLeftInRad)
                    return straightCost + weight;
                else if (changeAngle >= minLeftInRad && changeAngle <= maxLeftInRad)
                    return leftCost + weight;
                else if (changeAngle <= minRightInRad && changeAngle >= maxRightInRad)
                    return rightCost + weight;
                else return Double.POSITIVE_INFINITY; // too sharp turn
            }

            @Override
            public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
                long millis = (long) (1000 * calcTurnWeight(inEdge, viaNode, outEdge));
                return millis;
            }
        };
    }

    public static double calcChangeAngle(int inEdge, int viaNode, int outEdge, BaseGraph graph, EdgeIntAccess access, DecimalEncodedValue orientationEnc) {
        // this is slightly faster than calling getEdgeIteratorState as it avoids creating a new
        // object and accesses only one node but is slightly less safe as it cannot check that at
        // least one node must be identical (the case where getEdgeIteratorState returns null)
        boolean inEdgeReverse = !graph.isAdjNode(inEdge, viaNode);
        double prevOrientation = orientationEnc.getDecimal(inEdgeReverse, inEdge, access);

        boolean outEdgeReverse = !graph.isAdjNode(outEdge, viaNode);
        double orientation = orientationEnc.getDecimal(outEdgeReverse, outEdge, access);

        // bring parallel to prevOrientation
        if (orientation >= 0) orientation -= Math.PI;
        else orientation += Math.PI;
        prevOrientation = ANGLE_CALC.alignOrientation(orientation, prevOrientation);
        double changeAngle = orientation - prevOrientation;
        if (changeAngle > Math.PI) changeAngle -= 2 * Math.PI;
        else if (changeAngle < -Math.PI) changeAngle += 2 * Math.PI;
        return changeAngle;
    }
}
