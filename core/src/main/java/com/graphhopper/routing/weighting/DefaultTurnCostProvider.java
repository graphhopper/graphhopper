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

import static com.graphhopper.util.TurnCostsConfig.INFINITE_U_TURN_COSTS;

public class DefaultTurnCostProvider implements TurnCostProvider {
    private final BooleanEncodedValue turnRestrictionEnc;
    private final TurnCostStorage turnCostStorage;
    private final int uTurnCostsInt;
    private final double uTurnCosts;

    private final double minAngle;
    private final double minSharpAngle;
    private final double minUTurnAngle;

    private final double leftCosts;
    private final double leftSharpCosts;
    private final double straightCosts;
    private final double rightCosts;
    private final double rightSharpCosts;
    private final BaseGraph graph;
    private final EdgeIntAccess edgeIntAccess;
    private final DecimalEncodedValue orientationEnc;

    public DefaultTurnCostProvider(BooleanEncodedValue turnRestrictionEnc, DecimalEncodedValue orientationEnc,
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
        if (tcConfig.getMinSharpAngle() > tcConfig.getMinUTurnAngle())
            throw new IllegalArgumentException("Illegal min_sharp_angle = " + tcConfig.getMinSharpAngle());
        if (tcConfig.getMinAngle() > tcConfig.getMinSharpAngle() || tcConfig.getMinAngle() < 0)
            throw new IllegalArgumentException("Illegal min_angle = " + tcConfig.getMinAngle());
        if (tcConfig.getLeftCosts() > tcConfig.getLeftSharpCosts())
            throw new IllegalArgumentException("The costs for 'left_costs' (" + tcConfig.getLeftCosts()
                    + ") must be lower than for 'left_sharp_costs' (" + tcConfig.getLeftSharpCosts() + ")");
        if (tcConfig.getRightCosts() > tcConfig.getRightSharpCosts())
            throw new IllegalArgumentException("The costs for 'right_costs' (" + tcConfig.getRightCosts()
                    + ") must be lower than for 'right_sharp_costs' (" + tcConfig.getRightSharpCosts() + ")");

        this.minAngle = tcConfig.getMinAngle();
        this.minSharpAngle = tcConfig.getMinSharpAngle();
        this.minUTurnAngle = tcConfig.getMinUTurnAngle();

        this.leftCosts = tcConfig.getLeftCosts();
        this.leftSharpCosts = tcConfig.getLeftSharpCosts();
        this.straightCosts = tcConfig.getStraightCosts();
        this.rightCosts = tcConfig.getRightCosts();
        this.rightSharpCosts = tcConfig.getRightSharpCosts();

        this.graph = graph.getBaseGraph();
        this.edgeIntAccess = graph.getBaseGraph().getEdgeAccess();
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return 0;
        }

        if (inEdge == outEdge) {
            // note that the u-turn costs overwrite any turn costs set in TurnCostStorage
            return uTurnCosts;
        } else {
            if (turnRestrictionEnc != null && turnCostStorage.get(turnRestrictionEnc, inEdge, viaNode, outEdge))
                return Double.POSITIVE_INFINITY;
        }

        if (orientationEnc != null) {
            double changeAngle = calcChangeAngle(inEdge, viaNode, outEdge);
            if (changeAngle > -minAngle && changeAngle < minAngle)
                return straightCosts;
            else if (changeAngle >= minAngle && changeAngle < minSharpAngle)
                return rightCosts;
            else if (changeAngle >= minSharpAngle && changeAngle <= minUTurnAngle)
                return rightSharpCosts;
            else if (changeAngle <= -minAngle && changeAngle > -minSharpAngle)
                return leftCosts;
            else if (changeAngle <= -minSharpAngle && changeAngle >= -minUTurnAngle)
                return leftSharpCosts;

            // Too sharp turn is like an u-turn.
            return uTurnCosts;
        }
        return 0;
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
