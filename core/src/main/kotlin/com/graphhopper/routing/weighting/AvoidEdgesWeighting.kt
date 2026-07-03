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

import com.graphhopper.coll.primitive.IntHashSet
import com.graphhopper.util.EdgeIteratorState

/**
 * Increases the weight for a certain set of edges by a given factor and thus makes them less likely to be part of
 * a shortest path
 *
 * @author Robin Boldt
 */
open class AvoidEdgesWeighting(superWeighting: Weighting?) : AbstractAdjustedWeighting(superWeighting) {
    // contains the edge IDs of the already visited edges
    @JvmField
    protected var avoidedEdges: IntHashSet = IntHashSet()
    private var edgePenaltyFactor = 5.0

    fun setEdgePenaltyFactor(edgePenaltyFactor: Double): AvoidEdgesWeighting {
        this.edgePenaltyFactor = edgePenaltyFactor
        return this
    }

    fun setAvoidedEdges(avoidedEdges: IntHashSet): AvoidEdgesWeighting {
        this.avoidedEdges = avoidedEdges
        return this
    }

    override fun calcEdgeWeight(edgeState: EdgeIteratorState, reverse: Boolean): Double {
        val weight = superWeighting.calcEdgeWeight(edgeState, reverse)
        if (avoidedEdges.contains(edgeState.edge))
            return weight * edgePenaltyFactor

        return weight
    }

    override val name: String
        get() = "avoid_edges"
}
