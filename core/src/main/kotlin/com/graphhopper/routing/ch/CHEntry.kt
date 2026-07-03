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

import com.graphhopper.routing.SPTEntry
import com.graphhopper.util.EdgeIterator

open class CHEntry(edge: Int, incEdge: Int, adjNode: Int, weight: Double, parent: SPTEntry?) :
    SPTEntry(edge, adjNode, weight, parent) {

    /**
     * The id of the incoming original edge at this shortest path tree entry. For original edges this is the same
     * as the edge id, but for shortcuts this is the id of the last original edge of the shortcut.
     *
     * @see com.graphhopper.storage.RoutingCHEdgeIteratorState.origEdgeKeyLast
     */
    @JvmField
    var incEdge: Int = incEdge

    constructor(node: Int, weight: Double) : this(EdgeIterator.NO_EDGE, EdgeIterator.NO_EDGE, node, weight, null)

    override fun getParent(): CHEntry? = parent as CHEntry?

    override fun toString(): String = super.toString() + ", incEdge: " + incEdge
}
