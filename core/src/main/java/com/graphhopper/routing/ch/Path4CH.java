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

import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathBidirRef;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursivly unpack shortcuts.
 * <p>
 *
 * @author Peter Karich
 * @see PrepareContractionHierarchies
 */
public class Path4CH extends PathBidirRef {
    private final Graph routingGraph;
    private final TraversalMode traversalMode;
    private final List<EdgeIteratorState> finalEdges = new ArrayList<>();

    public Path4CH(Graph routingGraph, Graph baseGraph, Weighting weighting, TraversalMode traversalMode) {
        super(baseGraph, weighting);
        this.routingGraph = routingGraph;
        this.traversalMode = traversalMode;
    }

    @Override
    public Path extract() {
        Path result = super.extract();

        int prevId = EdgeIterator.NO_EDGE;
        time = 0;
        distance = 0;
        for (int i = 0; i < finalEdges.size(); i++) {
            EdgeIteratorState edge = finalEdges.get(i);
            distance += edge.getDistance();
            time += weighting.calcMillis(edge, false, prevId);
            prevId = edge.getEdge();
        }
        return result;
    }

    @Override
    protected final void processEdge(int tmpEdge, int endNode, int prevEdgeId) {
        // Shortcuts do only contain valid weight so first expand before adding
        // to distance and time
        expandEdge((CHEdgeIteratorState) routingGraph.getEdgeIteratorState(tmpEdge, endNode), false, prevEdgeId);
    }

    private void expandEdge(CHEdgeIteratorState mainEdgeState, boolean reverse, int prevEdgeId) {
        if (!mainEdgeState.isShortcut()) {
            finalEdges.add(mainEdgeState);
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

        // getEdgeProps could possibly return an empty edge if the shortcut is available for both directions.
        if (reverseOrder) {
            CHEdgeIteratorState edgeState1 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, to);
            boolean empty = edgeState1 == null;
            if (empty)
                edgeState1 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, to);

            CHEdgeIteratorState edgeState2;
            if (empty)
                edgeState2 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, from);
            else
                edgeState2 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, from);

            expandEdge(edgeState1, false, edgeState2.getEdge());
            expandLocalLoops(edgeState2, edgeState1, edgeState2.getBaseNode(), true);
            expandEdge(edgeState2, true, prevEdgeId);
        } else {
            CHEdgeIteratorState iter = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, from);
            boolean empty = iter == null;
            if (empty)
                iter = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, from);

            CHEdgeIteratorState iter2;
            if (empty)
                iter2 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, to);
            else
                iter2 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, to);

            expandEdge(iter, true, prevEdgeId);
            expandLocalLoops(iter2, iter, iter.getBaseNode(), false);
            expandEdge(iter, false, iter.getEdge());
        }
    }

    private void expandLocalLoops(CHEdgeIteratorState skipped1, CHEdgeIteratorState skipped2, int skippedNode, boolean reverse) {
        if (!traversalMode.isEdgeBased())
            return;
        double cost_uv = weighting.calcWeight(skipped1, true, EdgeIterator.NO_EDGE);
        double cost_vw = weighting.calcWeight(skipped2, false, skipped1.getEdge());
        double directCost = cost_uv + cost_vw;
        EdgeIteratorState bestLoop = null;

        EdgeExplorer explorer = routingGraph.createEdgeExplorer(new DefaultEdgeFilter(weighting.getFlagEncoder(), false, true));
        EdgeIterator iter = explorer.setBaseNode(skippedNode);
        while (iter.next()) {
            if (iter.getAdjNode() != skippedNode)
                continue;
            // loop at node detected. Check if we get a better result by including this loop.
            double cost_vloop = weighting.calcWeight(iter, false, skipped1.getEdge());
            double cost_to_w = weighting.calcWeight(skipped2, false, iter.getEdge());
            double total = cost_uv + cost_vloop + cost_to_w;
            if (total < directCost) {
                directCost = total;
                bestLoop = iter.detach(false);
            }
        }

        if (bestLoop != null) {
            expandEdge((CHEdgeIteratorState) bestLoop, reverse, skipped1.getEdge());
        }
    }
}
