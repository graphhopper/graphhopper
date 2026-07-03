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

import com.graphhopper.coll.primitive.IntScatterSet

interface NodeContractor {
    fun initFromGraph()

    fun close()

    /**
     * Calculates the priority of a node without changing the graph. Lower (!!) priority nodes are contracted first.
     */
    fun calculatePriority(node: Int): Float

    /**
     * Adds the required shortcuts for the given node.
     *
     * @return the set of nodes adjacent to this node (before contraction)
     */
    fun contractNode(node: Int): IntScatterSet

    fun finishContraction()

    fun getAddedShortcutsCount(): Long

    fun getStatisticsString(): String

    fun getDijkstraSeconds(): Float
}
