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
package com.graphhopper.routing

import com.graphhopper.util.EdgeIterator

/**
 * This class is used to create the shortest-path-tree from linked entities.
 *
 * @author Peter Karich
 */
open class SPTEntry(
    @JvmField var edge: Int,
    @JvmField var adjNode: Int,
    @JvmField var weight: Double,
    @JvmField var parent: SPTEntry?
) : Comparable<SPTEntry> {

    @JvmField
    var deleted = false

    constructor(node: Int, weight: Double) : this(EdgeIterator.NO_EDGE, node, weight, null)

    fun setDeleted() {
        deleted = true
    }

    fun isDeleted(): Boolean = deleted

    /**
     * This method returns the weight to the origin e.g. to the start for the forward SPT and to the
     * destination for the backward SPT. Where the variable 'weight' is used to let heap select
     * smallest *full* weight (from start to destination).
     */
    open fun getWeightOfVisitedPath(): Double = weight

    open fun getParent(): SPTEntry? = parent

    override fun compareTo(other: SPTEntry): Int {
        if (weight < other.weight)
            return -1

        // assumption no NaN and no -0
        return if (weight > other.weight) 1 else 0
    }

    override fun toString(): String = "$adjNode ($edge) weight: $weight"
}
