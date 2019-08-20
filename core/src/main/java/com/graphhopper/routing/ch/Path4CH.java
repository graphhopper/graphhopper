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

import com.graphhopper.routing.PathBidirRef;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.ShortcutUnpacker;
import com.graphhopper.util.EdgeIteratorState;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;

public class Path4CH extends PathBidirRef {
    private final ShortcutUnpacker shortcutUnpacker;

    public Path4CH(Graph routingGraph, Graph baseGraph, final Weighting weighting) {
        super(baseGraph, weighting);
        this.shortcutUnpacker = getShortcutUnpacker(routingGraph, weighting);
    }

    @Override
    protected final void processEdge(int edgeId, int adjNode, int prevEdgeId) {
        // Shortcuts do only contain valid weight so first expand before adding
        // to distance and time
        shortcutUnpacker.visitOriginalEdgesFwd(edgeId, adjNode, true, prevEdgeId);
    }

    @Override
    protected void processEdgeBwd(int edgeId, int adjNode, int nextEdgeId) {
        shortcutUnpacker.visitOriginalEdgesBwd(edgeId, adjNode, true, nextEdgeId);
    }

    protected ShortcutUnpacker getShortcutUnpacker(Graph routingGraph, final Weighting weighting) {
        return new ShortcutUnpacker(routingGraph, new ShortcutUnpacker.Visitor() {
            @Override
            public void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
                distance += edge.getDistance();
                time += weighting.calcMillis(edge, reverse, NO_EDGE);
                addEdge(edge.getEdge());
            }
        }, false);
    }
}
