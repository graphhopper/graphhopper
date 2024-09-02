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

    private final double minAngleInRad;
    private final double minSharpAngleInRad;
    private final double maxAngleInRad;

    private final double leftCost;
    private final double leftSharpCost;
    private final double straightCost;
    private final double rightCost;
    private final double rightSharpCost;
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
        if (tcConfig.getMaxAngle() > 180)
            throw new IllegalArgumentException("Illegal max_angle = " + tcConfig.getMinAngle());
        if (tcConfig.getMinSharpAngle() > tcConfig.getMaxAngle())
            throw new IllegalArgumentException("Illegal min_sharp_angle = " + tcConfig.getMinSharpAngle());
        if (tcConfig.getMinAngle() > tcConfig.getMinSharpAngle() || tcConfig.getMinAngle() < 0)
            throw new IllegalArgumentException("Illegal min_angle = " + tcConfig.getMinAngle());
        this.minAngleInRad = Math.toRadians(tcConfig.getMinAngle());
        this.minSharpAngleInRad = Math.toRadians(tcConfig.getMinSharpAngle());
        this.maxAngleInRad = Math.toRadians(tcConfig.getMaxAngle());

        this.leftCost = tcConfig.getLeftCost();
        this.leftSharpCost = tcConfig.getLeftSharpCost();
        this.straightCost = tcConfig.getStraightCost();
        this.rightCost = tcConfig.getRightCost();
        this.rightSharpCost = tcConfig.getRightSharpCost();

        this.graph = graph.getBaseGraph();
        this.edgeIntAccess = graph.getBaseGraph().getEdgeAccess();
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return 0;
        }
        double tCost = 0;
        if (inEdge == outEdge) {
            // note that the u-turn costs overwrite any turn costs set in TurnCostStorage
            tCost = uTurnCosts;
        } else {
            if (turnRestrictionEnc != null)
                tCost = turnCostStorage.get(turnRestrictionEnc, inEdge, viaNode, outEdge) ? Double.POSITIVE_INFINITY : 0;
        }

        if (orientationEnc != null) {
            if (Double.isInfinite(tCost)) return tCost;
            double changeAngle = calcChangeAngle(inEdge, viaNode, outEdge);
            if (changeAngle > -minAngleInRad && changeAngle < minAngleInRad)
                return straightCost + tCost;
            else if (changeAngle >= minAngleInRad && changeAngle < minSharpAngleInRad)
                return leftCost + tCost;
            else if (changeAngle >= minSharpAngleInRad && changeAngle <= maxAngleInRad)
                return leftSharpCost + tCost;
            else if (changeAngle <= -minAngleInRad && changeAngle > -minSharpAngleInRad)
                return rightCost + tCost;
            else if (changeAngle <= -minSharpAngleInRad && changeAngle >= -maxAngleInRad)
                return rightSharpCost + tCost;
            else return Double.POSITIVE_INFINITY; // too sharp turn
        }
        return tCost;
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
        double prevOrientation = orientationEnc.getDecimal(inEdgeReverse, inEdge, edgeIntAccess);

        boolean outEdgeReverse = !graph.isAdjNode(outEdge, viaNode);
        double orientation = orientationEnc.getDecimal(outEdgeReverse, outEdge, edgeIntAccess);

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
