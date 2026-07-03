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
package com.graphhopper.routing.ch

class PrepareCHEntry(
    /**
     * The ID of the edge associated with this entry in the prepare graph (this is not the same number that will later
     * be the ID of the edge/shortcut in the CHGraph.
     */
    @JvmField var prepareEdge: Int,
    /**
     * The first edge key of the incoming edge
     */
    @JvmField var firstEdgeKey: Int,
    /**
     * The edge key of the incoming edge at this shortest path tree entry. For original edges this is the same
     * as the edge key, but for shortcuts this is the edge key of the last original edge of the shortcut.
     */
    @JvmField var incEdgeKey: Int,
    @JvmField var adjNode: Int,
    @JvmField var weight: Double,
    /**
     * The number of original edges this (potential) shortcut represents. Will be one for original edges
     */
    @JvmField var origEdges: Int
) : Comparable<PrepareCHEntry> {

    @JvmField
    var parent: PrepareCHEntry? = null

    fun getParent(): PrepareCHEntry? = parent

    override fun toString(): String =
        "$adjNode ($prepareEdge) weight: $weight, incEdgeKey: $incEdgeKey"

    override fun compareTo(other: PrepareCHEntry): Int {
        if (weight < other.weight)
            return -1

        // assumption no NaN and no -0
        return if (weight > other.weight) 1 else 0
    }
}
