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
import com.graphhopper.routing.SPTEntry
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.GHUtility

/**
 * @author easbar
 */
class EdgeBasedCHBidirPathExtractor(private val routingGraph: RoutingCHGraph) :
    DefaultBidirPathExtractor(routingGraph.baseGraph, null) {

    private val shortcutUnpacker: ShortcutUnpacker = createShortcutUnpacker()
    private val weighting: Weighting = routingGraph.baseGraph.wrapWeighting(routingGraph.weighting)

    override fun onEdge(edge: Int, adjNode: Int, reverse: Boolean, prevOrNextEdge: Int) {
        if (reverse) {
            shortcutUnpacker.visitOriginalEdgesBwd(edge, adjNode, true, prevOrNextEdge)
        } else {
            shortcutUnpacker.visitOriginalEdgesFwd(edge, adjNode, true, prevOrNextEdge)
        }
    }

    override fun onMeetingPoint(inEdge: Int, viaNode: Int, outEdge: Int) {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return
        }
        // its important to use the wrapped weighting here, otherwise turn costs involving virtual edges will be wrong
        path.addTime(weighting.calcTurnMillis(inEdge, viaNode, outEdge))
    }

    private fun createShortcutUnpacker(): ShortcutUnpacker {
        return ShortcutUnpacker(routingGraph, { edge, reverse, prevOrNextEdgeId ->
            path.addDistance_mm(edge!!.distance_mm)
            path.addTime(GHUtility.calcMillisWithTurnMillis(weighting, edge, reverse, prevOrNextEdgeId))
            path.addEdge(edge.edge)
        }, true)
    }

    public override fun getIncEdge(entry: SPTEntry): Int = (entry as CHEntry).incEdge
}
