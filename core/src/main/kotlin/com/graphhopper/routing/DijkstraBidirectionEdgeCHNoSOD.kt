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

import com.graphhopper.routing.ch.CHEntry
import com.graphhopper.storage.RoutingCHGraph

/**
 * @author easbar
 */
open class DijkstraBidirectionEdgeCHNoSOD(graph: RoutingCHGraph) : AbstractBidirectionEdgeCHNoSOD(graph) {

    override fun createStartEntry(node: Int, weight: Double, reverse: Boolean): CHEntry {
        return CHEntry(node, weight)
    }

    override fun createEntry(edge: Int, adjNode: Int, incEdge: Int, weight: Double, parent: SPTEntry?, reverse: Boolean): CHEntry {
        return CHEntry(edge, incEdge, adjNode, weight, parent)
    }

    override fun updateEntry(entry: SPTEntry, edge: Int, adjNode: Int, incEdge: Int, weight: Double, parent: SPTEntry?, reverse: Boolean) {
        assert(entry.adjNode == adjNode)
        entry.edge = edge
        (entry as CHEntry).incEdge = incEdge
        entry.weight = weight
        entry.parent = parent
    }

    override fun getName(): String = "dijkstrabi|ch|edge_based|no_sod"
}
