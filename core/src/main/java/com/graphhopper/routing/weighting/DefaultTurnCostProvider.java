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

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.TurnCostsConfig;

import static com.graphhopper.routing.ev.RoadAccess.PRIVATE;
import static com.graphhopper.util.TurnCostsConfig.INFINITE_U_TURN_COSTS;

public class DefaultTurnCostProvider implements TurnCostProvider {
    private final BooleanEncodedValue turnRestrictionEnc;
    private final TurnCostStorage turnCostStorage;
    private final int uTurnCostsInt;
    private final double uTurnCosts;

    private final double minTurnAngle;
    private final double minSharpTurnAngle;
    private final double minUTurnAngle;

    private final double leftTurnCosts;
    private final double sharpLeftTurnCosts;
    private final double straightCosts;
    private final double rightTurnCosts;
    private final double sharpRightTurnCosts;
    private final BaseGraph graph;
    private final EdgeIntAccess edgeIntAccess;
    private final DecimalEncodedValue orientationEnc;
    private TurnTimeMapping turnTimeMapping;
    public DefaultTurnCostProvider(BooleanEncodedValue turnRestrictionEnc,
                                   DecimalEncodedValue orientationEnc,
                                   Graph graph, TurnCostsConfig tcConfig) {
        this.uTurnCostsInt = tcConfig.getUTurnCosts();
        if (uTurnCostsInt < 0 && uTurnCostsInt != INFINITE_U_TURN_COSTS) {
            throw new IllegalArgumentException("u-turn costs must be positive, or equal to " + INFINITE_U_TURN_COSTS + " (=infinite costs)");
        }
        this.uTurnCosts = uTurnCostsInt < 0 ? Double.POSITIVE_INFINITY : uTurnCostsInt;
        if (graph.getTurnCostStorage() == null) {
            throw new IllegalArgumentException("No storage set to calculate turn weight");
        }
        // if null the TurnCostProvider can be still useful for edge-based routing
        this.turnRestrictionEnc = turnRestrictionEnc;
        this.turnCostStorage = graph.getTurnCostStorage();

        this.orientationEnc = orientationEnc;
        if (tcConfig.getMinUTurnAngle() > 180)
            throw new IllegalArgumentException("Illegal min_u_turn_angle = " + tcConfig.getMinUTurnAngle());
        if (tcConfig.getMinSharpTurnAngle() > tcConfig.getMinUTurnAngle())
            throw new IllegalArgumentException("Illegal min_sharp_turn_angle = " + tcConfig.getMinSharpTurnAngle());
        if (tcConfig.getMinTurnAngle() > tcConfig.getMinSharpTurnAngle() || tcConfig.getMinTurnAngle() < 0)
            throw new IllegalArgumentException("Illegal min_turn_angle = " + tcConfig.getMinTurnAngle());
        if (tcConfig.getLeftTurnCosts() > tcConfig.getSharpLeftTurnCosts())
            throw new IllegalArgumentException("The costs for 'left_turn_costs' (" + tcConfig.getLeftTurnCosts()
                    + ") must be lower than for 'sharp_left_turn_costs' (" + tcConfig.getSharpLeftTurnCosts() + ")");
        if (tcConfig.getRightTurnCosts() > tcConfig.getSharpRightTurnCosts())
            throw new IllegalArgumentException("The costs for 'right_turn_costs' (" + tcConfig.getRightTurnCosts()
                    + ") must be lower than for 'sharp_right_turn_costs' (" + tcConfig.getSharpRightTurnCosts() + ")");

        this.minTurnAngle = tcConfig.getMinTurnAngle();
        this.minSharpTurnAngle = tcConfig.getMinSharpTurnAngle();
        this.minUTurnAngle = tcConfig.getMinUTurnAngle();

        this.leftTurnCosts = tcConfig.getLeftTurnCosts();
        this.sharpLeftTurnCosts = tcConfig.getSharpLeftTurnCosts();
        this.straightCosts = tcConfig.getStraightCosts();
        this.rightTurnCosts = tcConfig.getRightTurnCosts();
        this.sharpRightTurnCosts = tcConfig.getSharpRightTurnCosts();

        this.graph = graph.getBaseGraph();
        this.edgeIntAccess = graph.getBaseGraph().getEdgeAccess();
    }

    @Override
    public void setTurnTimeMapping(TurnTimeMapping turnTimeMapping) {
        this.turnTimeMapping = turnTimeMapping;
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return 0;
        }

        double weight = 0;
        if (inEdge == outEdge) {
            // note that the u-turn costs overwrite any turn costs set in TurnCostStorage
            weight += uTurnCosts;
        } else {
            if (turnRestrictionEnc != null) {
                if (turnCostStorage.get(turnRestrictionEnc, inEdge, viaNode, outEdge))
                    return Double.POSITIVE_INFINITY;
                else if (turnTimeMapping != null) {
                    weight = turnTimeMapping.calcTurnMillis(graph, edgeIntAccess, inEdge, viaNode, outEdge);
                    if (Double.isInfinite(weight)) return weight;
                }
            }
        }

        if (orientationEnc != null) {
            double changeAngle = calcChangeAngle(inEdge, viaNode, outEdge);
            if (changeAngle > -minTurnAngle && changeAngle < minTurnAngle)
                return weight + straightCosts;
            else if (changeAngle >= minTurnAngle && changeAngle < minSharpTurnAngle)
                return weight + rightTurnCosts;
            else if (changeAngle >= minSharpTurnAngle && changeAngle <= minUTurnAngle)
                return weight + sharpRightTurnCosts;
            else if (changeAngle <= -minTurnAngle && changeAngle > -minSharpTurnAngle)
                return weight + leftTurnCosts;
            else if (changeAngle <= -minSharpTurnAngle && changeAngle >= -minUTurnAngle)
                return weight + sharpLeftTurnCosts;

            // Too sharp turn is like an u-turn.
            return weight + uTurnCosts;
        }
        return weight;
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        // Making a proper assumption about the turn time is very hard. Assuming zero is the
        // simplest way to deal with this. This also means the u-turn time is zero. Provided that
        // the u-turn weight is large enough, u-turns only occur in special situations like curbsides
        // pointing to the end of dead-end streets where it is unclear if a finite u-turn time would
        // be a good choice.
        return 0;
    }

    @Override
    public String toString() {
        return "default_tcp_" + uTurnCostsInt;
    }

    double calcChangeAngle(int inEdge, int viaNode, int outEdge) {
        // this is slightly faster than calling getEdgeIteratorState as it avoids creating a new
        // object and accesses only one node but is slightly less safe as it cannot check that at
        // least one node must be identical (the case where getEdgeIteratorState returns null)
        boolean inEdgeReverse = !graph.isAdjNode(inEdge, viaNode);
        double prevAzimuth = orientationEnc.getDecimal(inEdgeReverse, inEdge, edgeIntAccess);

        boolean outEdgeReverse = !graph.isAdjNode(outEdge, viaNode);
        double azimuth = orientationEnc.getDecimal(outEdgeReverse, outEdge, edgeIntAccess);

        // bring parallel to prevOrientation
        if (azimuth >= 180) azimuth -= 180;
        else azimuth += 180;

        double changeAngle = azimuth - prevAzimuth;

        // keep in [-180, 180]
        if (changeAngle > 180) changeAngle -= 360;
        else if (changeAngle < -180) changeAngle += 360;
        return changeAngle;
    }
}
