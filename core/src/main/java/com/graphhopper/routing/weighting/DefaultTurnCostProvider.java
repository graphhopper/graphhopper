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

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.core.util.EdgeIterator;

import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;

public class DefaultTurnCostProvider implements TurnCostProvider {
    private final DecimalEncodedValue turnCostEnc;
    private final TurnCostStorage turnCostStorage;
    private final int uTurnCostsInt;
    private final double uTurnCosts;

    public DefaultTurnCostProvider(DecimalEncodedValue turnCostEnc, TurnCostStorage turnCostStorage) {
        this(turnCostEnc, turnCostStorage, Weighting.INFINITE_U_TURN_COSTS);
    }

    /**
     * @param uTurnCosts the costs of a u-turn in seconds, for {@link Weighting#INFINITE_U_TURN_COSTS} the u-turn costs
     *                   will be infinite
     */
    public DefaultTurnCostProvider(DecimalEncodedValue turnCostEnc, TurnCostStorage turnCostStorage, int uTurnCosts) {
        if (uTurnCosts < 0 && uTurnCosts != INFINITE_U_TURN_COSTS) {
            throw new IllegalArgumentException("u-turn costs must be positive, or equal to " + INFINITE_U_TURN_COSTS + " (=infinite costs)");
        }
        this.uTurnCostsInt = uTurnCosts;
        this.uTurnCosts = uTurnCosts < 0 ? Double.POSITIVE_INFINITY : uTurnCosts;
        if (turnCostStorage == null) {
            throw new IllegalArgumentException("No storage set to calculate turn weight");
        }
        // if null the TurnCostProvider can be still useful for edge-based routing
        this.turnCostEnc = turnCostEnc;
        this.turnCostStorage = turnCostStorage;
    }

    public DecimalEncodedValue getTurnCostEnc() {
        return turnCostEnc;
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
            if (turnCostEnc != null)
                tCost = turnCostStorage.get(turnCostEnc, edgeFrom, nodeVia, edgeTo);
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
}
