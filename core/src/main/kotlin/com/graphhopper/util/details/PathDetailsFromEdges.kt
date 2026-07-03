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
package com.graphhopper.util.details

import com.graphhopper.routing.Path
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode

/**
 * This class calculates a PathDetail list in a similar fashion to the instruction calculation,
 * also see [com.graphhopper.routing.InstructionsFromEdges].
 *
 * This class uses the [PathDetailsBuilder]. We provide every edge to the builder
 * and up to its internals we create a new interval, ie. a new PathDetail in the List.
 *
 * @author Robin Boldt
 * @see PathDetail
 */
class PathDetailsFromEdges(private val calculators: List<PathDetailsBuilder>, previousIndex: Int) : Path.EdgeVisitor {

    private var lastIndex = previousIndex

    override fun next(edge: EdgeIteratorState, index: Int, prevEdgeId: Int) {
        for (calc in calculators) {
            if (calc.isEdgeDifferentToLastEdge(edge)) {
                calc.endInterval(lastIndex)
                calc.startInterval(lastIndex)
            }
        }
        lastIndex += edge.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ).size()
    }

    override fun finish() {
        for (calc in calculators) {
            calc.endInterval(lastIndex)
        }
    }

    companion object {
        /**
         * Calculates the PathDetails for a Path. This method will return fast, if there are no calculators.
         *
         * @param pathBuilderFactory Generates the relevant PathBuilders
         * @return List of PathDetails for this Path
         */
        @JvmStatic
        fun calcDetails(path: Path, evLookup: EncodedValueLookup, weighting: Weighting,
                        // the factory may be null (like in java) when no path details are requested
                        requestedPathDetails: List<String>, pathBuilderFactory: PathDetailsBuilderFactory?,
                        previousIndex: Int, graph: Graph): Map<String, List<PathDetail>> {
            if (!path.isFound() || requestedPathDetails.isEmpty())
                return emptyMap()
            val uniquePD = HashSet<String>(requestedPathDetails.size)
            val res = requestedPathDetails.filter { !uniquePD.add(it) }
            if (res.isNotEmpty())
                throw IllegalArgumentException("Do not use duplicate path details: $res")

            val pathBuilders = pathBuilderFactory!!.createPathDetailsBuilders(requestedPathDetails, path, evLookup, weighting, graph)
            if (pathBuilders.isEmpty())
                return emptyMap()

            path.forEveryEdge(PathDetailsFromEdges(pathBuilders, previousIndex))

            val pathDetails = HashMap<String, List<PathDetail>>(pathBuilders.size)
            for (builder in pathBuilders) {
                val entry = builder.build()
                val existing = pathDetails.put(entry.key, entry.value)
                if (existing != null)
                    throw IllegalStateException("Some PathDetailsBuilders use duplicate key: " + entry.key)
            }

            return pathDetails
        }
    }
}
