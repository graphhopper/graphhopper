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

package com.graphhopper.routing.ch;

import com.graphhopper.routing.DefaultBidirPathExtractor;
import com.graphhopper.routing.SPTEntry;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

/**
 * @author easbar
 */
public class EdgeBasedCHBidirPathExtractor extends DefaultBidirPathExtractor {
    private final RoutingCHGraph routingGraph;
    private final ShortcutUnpacker shortcutUnpacker;
    private final Weighting weighting;

    public EdgeBasedCHBidirPathExtractor(RoutingCHGraph routingGraph) {
        super(routingGraph.getBaseGraph(), null);
        this.routingGraph = routingGraph;
        shortcutUnpacker = createShortcutUnpacker();
        weighting = routingGraph.getBaseGraph().wrapWeighting(routingGraph.getWeighting());
    }

    @Override
    public void onEdge(int edge, int adjNode, boolean reverse, int prevOrNextEdge) {
        if (reverse) {
            shortcutUnpacker.visitOriginalEdgesBwd(edge, adjNode, true, prevOrNextEdge);
        } else {
            shortcutUnpacker.visitOriginalEdgesFwd(edge, adjNode, true, prevOrNextEdge);
        }
    }

    @Override
    protected void onMeetingPoint(int inEdge, int viaNode, int outEdge) {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return;
        }
        // its important to use the wrapped weighting here, otherwise turn costs involving virtual edges will be wrong
        path.addTime(weighting.calcTurnMillis(inEdge, viaNode, outEdge));
    }

    private ShortcutUnpacker createShortcutUnpacker() {
        return new ShortcutUnpacker(routingGraph, (edge, reverse, prevOrNextEdgeId) -> {
            path.addDistance(edge.getDistance());
            path.addTime(GHUtility.calcMillisWithTurnMillis(weighting, edge, reverse, prevOrNextEdgeId));
            path.addEdge(edge.getEdge());
        }, true);
    }

    @Override
    public int getIncEdge(SPTEntry sptEntry) {
        return ((CHEntry) sptEntry).incEdge;
    }

}
