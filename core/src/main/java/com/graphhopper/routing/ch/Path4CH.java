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
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIterator;

import java.util.Locale;

/**
 * Recursively unpack shortcuts.
 * <p>
 *
 * @author Peter Karich
 * @see PrepareContractionHierarchies
 */
public class Path4CH extends PathBidirRef {
    private final Graph routingGraph;

    public Path4CH(Graph routingGraph, Graph baseGraph, Weighting weighting) {
        super(baseGraph, weighting);
        this.routingGraph = routingGraph;
    }

    @Override
    protected final void processEdge(int edgeId, int endNode, int prevEdgeId) {
        // Shortcuts do only contain valid weight so first expand before adding
        // to distance and time
        expandEdge(getEdge(edgeId, endNode), false);
    }

    private void expandEdge(CHEdgeIteratorState edge, boolean reverse) {
        if (!edge.isShortcut()) {
            distance += edge.getDistance();
            time += weighting.calcMillis(edge, reverse, EdgeIterator.NO_EDGE);
            addEdge(edge.getEdge());
            return;
        }
        expandSkippedEdges(edge.getSkippedEdge1(), edge.getSkippedEdge2(), edge.getBaseNode(), edge.getAdjNode(), reverse);
    }

    private void expandSkippedEdges(int skippedEdge1, int skippedEdge2, int from, int to, boolean reverse) {
        // for edge-based CH we need to take special care for loop shortcuts
        if (from != to) {
            // get properties like speed of the edge in the correct direction
            if (reverseOrder == reverse) {
                int tmp = from;
                from = to;
                to = tmp;
            }
            CHEdgeIteratorState sk2to = getEdge(skippedEdge2, to);
            if (sk2to != null) {
                expandEdge(sk2to, !reverseOrder);
                expandEdge(getEdge(skippedEdge1, from), reverseOrder);
            } else {
                expandEdge(getEdge(skippedEdge1, to), !reverseOrder);
                expandEdge(getEdge(skippedEdge2, from), reverseOrder);
            }
        } else {
            CHEdgeIteratorState sk1 = getEdge(skippedEdge1, from);
            CHEdgeIteratorState sk2 = getEdge(skippedEdge2, from);
            if (sk1.getAdjNode() == sk1.getBaseNode() || sk2.getAdjNode() == sk2.getBaseNode()) {
                // this is a loop where both skipped edges are loops. but this should never happen.
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "error: detected edge where both skipped edges are loops. from: %d, to: %d, " +
                                "skip-edge1: %d, skip-edge2: %d, reverse: %b", from, to, skippedEdge1, skippedEdge2, reverse));
            }

            if (!reverseOrder) {
                expandEdge(sk1, !reverse);
                expandEdge(sk2, reverse);
            } else {
                expandEdge(sk2, reverse);
                expandEdge(sk1, !reverse);
            }
        }
    }

    private CHEdgeIteratorState getEdge(int edgeId, int adjNode) {
        return (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(edgeId, adjNode);
    }
}
