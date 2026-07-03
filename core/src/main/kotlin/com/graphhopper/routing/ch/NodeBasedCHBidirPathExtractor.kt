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

import com.graphhopper.routing.DefaultBidirPathExtractor
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.RoutingCHGraph

class NodeBasedCHBidirPathExtractor(private val routingGraph: RoutingCHGraph) :
    DefaultBidirPathExtractor(routingGraph.baseGraph, routingGraph.weighting) {

    private val shortcutUnpacker: ShortcutUnpacker = createShortcutUnpacker()
    private val weighting: Weighting = routingGraph.baseGraph.wrapWeighting(routingGraph.weighting)

    override fun onEdge(edge: Int, adjNode: Int, reverse: Boolean, prevOrNextEdge: Int) {
        if (reverse) {
            shortcutUnpacker.visitOriginalEdgesBwd(edge, adjNode, true, prevOrNextEdge)
        } else {
            shortcutUnpacker.visitOriginalEdgesFwd(edge, adjNode, true, prevOrNextEdge)
        }
    }

    private fun createShortcutUnpacker(): ShortcutUnpacker {
        return ShortcutUnpacker(routingGraph, { edge, reverse, prevOrNextEdgeId ->
            path.addDistance_mm(edge!!.distance_mm)
            path.addTime(weighting.calcEdgeMillis(edge, reverse))
            path.addEdge(edge.edge)
        }, false)
    }
}
