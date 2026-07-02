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

package com.graphhopper.routing.weighting

import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.weighting.custom.CustomWeighting
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.Graph
import com.graphhopper.storage.TurnCostStorage
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.TurnCostsConfig
import com.graphhopper.util.TurnCostsConfig.INFINITE_U_TURN_COSTS

class DefaultTurnCostProvider(
    // if null the TurnCostProvider can be still useful for edge-based routing
    private val turnRestrictionEnc: BooleanEncodedValue?,
    graph: Graph, tcConfig: TurnCostsConfig,
    private val turnPenaltyMapping: CustomWeighting.TurnPenaltyMapping?
) : TurnCostProvider {
    private val turnCostStorage: TurnCostStorage
    private val uTurnCostsInt: Int = tcConfig.uTurnCosts
    private val uTurnCosts: Double
    private val graph: BaseGraph
    private val edgeIntAccess: EdgeIntAccess

    init {
        if (uTurnCostsInt < 0 && uTurnCostsInt != INFINITE_U_TURN_COSTS) {
            throw IllegalArgumentException("u-turn costs must be positive, or equal to $INFINITE_U_TURN_COSTS (=infinite costs)")
        }
        this.uTurnCosts = if (uTurnCostsInt < 0) Double.POSITIVE_INFINITY else uTurnCostsInt.toDouble()
        this.turnCostStorage = graph.turnCostStorage
            ?: throw IllegalArgumentException("No storage set to calculate turn weight")

        this.graph = graph.baseGraph
        this.edgeIntAccess = graph.baseGraph.edgeAccess
    }

    override fun calcTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return 0.0
        }

        if (inEdge == outEdge) {
            // note that the u-turn costs overwrite any turn costs set in TurnCostStorage
            return uTurnCosts
        } else if (turnRestrictionEnc != null) {
            if (turnCostStorage.get(turnRestrictionEnc, inEdge, viaNode, outEdge))
                return Double.POSITIVE_INFINITY
        }
        if (turnPenaltyMapping != null)
            return turnPenaltyMapping.get(graph, edgeIntAccess, inEdge, viaNode, outEdge)
        return 0.0
    }

    override fun calcTurnMillis(inEdge: Int, viaNode: Int, outEdge: Int): Long {
        // Making a proper assumption about the turn time is very hard. Assuming zero is the
        // simplest way to deal with this. This also means the u-turn time is zero. Provided that
        // the u-turn weight is large enough, u-turns only occur in special situations like curbsides
        // pointing to the end of dead-end streets where it is unclear if a finite u-turn time would
        // be a good choice.
        return 0
    }

    override fun toString(): String {
        return "default_tcp_$uTurnCostsInt"
    }
}
