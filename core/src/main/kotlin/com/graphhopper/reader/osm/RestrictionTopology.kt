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

package com.graphhopper.reader.osm

import com.graphhopper.coll.primitive.IntArrayList

/**
 * Basically an OSM restriction, but in 'graph-representation', i.e. it is expressed in terms of graph node/edge IDs
 * instead of OSM way IDs. There can be via-node-restrictions (with a single via-node) and via-way/edge-restrictions
 * (with one or more via-edges). There can also be multiple from- or to-edges to represent OSM restrictions like
 * no_entry or no_exit that use multiple from- or to-members.
 *
 * We store a list of via-nodes even for via-way restrictions. It stores the nodes connecting the via-ways,
 * see [WayToEdgeConverter.EdgeResult]. For via-node restrictions the list simply contains the single via node.
 *
 * This class only contains the 'topology' of the restriction. The [RestrictionType] is handled separately,
 * because opposite to the type the topology does not depend on the vehicle type.
 */
class RestrictionTopology private constructor(
    val isViaWayRestriction: Boolean,
    val viaNodes: IntArrayList,
    val fromEdges: IntArrayList,
    val viaEdges: IntArrayList?,
    val toEdges: IntArrayList
) {
    init {
        if (fromEdges.size() > 1 && toEdges.size() > 1)
            throw IllegalArgumentException("fromEdges and toEdges cannot be size > 1 at the same time")
        if (fromEdges.isEmpty || toEdges.isEmpty)
            throw IllegalArgumentException("fromEdges and toEdges must not be empty")
        if (!isViaWayRestriction && viaNodes.size() != 1)
            throw IllegalArgumentException("for node restrictions there must be exactly one via node")
        if (!isViaWayRestriction && viaEdges != null)
            throw IllegalArgumentException("for node restrictions the viaEdges must be null")
        if (isViaWayRestriction && viaEdges!!.isEmpty)
            throw IllegalArgumentException("for way restrictions there must at least one via edge")
        if (isViaWayRestriction && viaNodes.size() != viaEdges!!.size() + 1)
            throw IllegalArgumentException("for way restrictions there must be one via node more than there are via edges")
    }

    companion object {
        @JvmStatic
        fun node(fromEdge: Int, viaNode: Int, toEdge: Int): RestrictionTopology =
            node(IntArrayList.from(fromEdge), viaNode, IntArrayList.from(toEdge))

        @JvmStatic
        fun node(fromEdges: IntArrayList, viaNode: Int, toEdges: IntArrayList): RestrictionTopology =
            RestrictionTopology(false, IntArrayList.from(viaNode), fromEdges, null, toEdges)

        @JvmStatic
        fun way(fromEdge: Int, viaEdge: Int, toEdge: Int, viaNodes: IntArrayList): RestrictionTopology =
            way(fromEdge, IntArrayList.from(viaEdge), toEdge, viaNodes)

        @JvmStatic
        fun way(fromEdge: Int, viaEdges: IntArrayList, toEdge: Int, viaNodes: IntArrayList): RestrictionTopology =
            way(IntArrayList.from(fromEdge), viaEdges, IntArrayList.from(toEdge), viaNodes)

        @JvmStatic
        fun way(fromEdges: IntArrayList, viaEdges: IntArrayList, toEdges: IntArrayList, viaNodes: IntArrayList): RestrictionTopology =
            RestrictionTopology(true, viaNodes, fromEdges, viaEdges, toEdges)
    }
}
