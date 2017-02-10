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
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIterator;

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
    protected final void processEdge(int tmpEdge, int endNode, int prevEdgeId) {
        // Shortcuts do only contain valid weight so first expand before adding
        // to distance and time
        expandEdge((CHEdgeIteratorState) routingGraph.getEdgeIteratorState(tmpEdge, endNode), false);
    }

    private void expandEdge(CHEdgeIteratorState mainEdgeState, boolean reverse) {
        if (!mainEdgeState.isShortcut()) {
            distance += mainEdgeState.getDistance();
            time += weighting.calcMillis(mainEdgeState, reverse, EdgeIterator.NO_EDGE);
            addEdge(mainEdgeState.getEdge());
            return;
        }

        int skippedEdge1 = mainEdgeState.getSkippedEdge1();
        int skippedEdge2 = mainEdgeState.getSkippedEdge2();
        int from = mainEdgeState.getBaseNode(), to = mainEdgeState.getAdjNode();

        // get properties like speed of the edge in the correct direction
        if (reverse) {
            int tmp = from;
            from = to;
            to = tmp;
        }

        // getEdgeProps could possibly return an empty edge if the shortcut is available for both directions
        if (reverseOrder) {
            CHEdgeIteratorState edgeState = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, to);
            boolean empty = edgeState == null;
            if (empty)
                edgeState = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, to);

            expandEdge(edgeState, false);

            if (empty)
                edgeState = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, from);
            else
                edgeState = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, from);

            expandEdge(edgeState, true);
        } else {
            CHEdgeIteratorState iter = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, from);
            boolean empty = iter == null;
            if (empty)
                iter = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, from);

            expandEdge(iter, true);

            if (empty)
                iter = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, to);
            else
                iter = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, to);

            expandEdge(iter, false);
        }
    }
}
