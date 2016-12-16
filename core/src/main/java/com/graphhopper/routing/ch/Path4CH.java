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
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import javafx.util.Pair;

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
    private final List<Pair<EdgeIteratorState, Boolean>> finalEdges = new ArrayList<>();

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
            EdgeIteratorState edge = finalEdges.get(i).getKey();
            Boolean reverse = finalEdges.get(i).getValue();
            distance += edge.getDistance();
            time += weighting.calcMillis(edge, reverse, prevId);
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
            finalEdges.add(new Pair<EdgeIteratorState, Boolean>(mainEdgeState, reverse));
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
        // both edges in forward direction
        CHEdgeIteratorState skipped2 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, to);
        CHEdgeIteratorState skipped1;
        if (skipped2 == null) {
            skipped2 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, to);
            skipped1 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, skipped2.getBaseNode());
        } else {
            skipped1 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, skipped2.getBaseNode());
        }

        if (reverseOrder) {
            expandEdge(skipped2, false, prevEdgeId);
            expandLocalLoops(skipped1, skipped2, skipped1.getAdjNode(), true);
            expandEdge(skipped1, false, skipped2.getEdge());
        } else {
            expandEdge(skipped1, false, prevEdgeId);
            expandLocalLoops(skipped1, skipped2, skipped1.getAdjNode(), false);
            expandEdge(skipped2, false, skipped1.getEdge());
        }
    }

    private void expandLocalLoops(CHEdgeIteratorState skipped1, CHEdgeIteratorState skipped2, int skippedNode, boolean reverse) {
        if (!traversalMode.isEdgeBased())
            return;
        double cost_uv = weighting.calcWeight(skipped1, false, EdgeIterator.NO_EDGE);
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

        if (directCost == Double.MAX_VALUE && bestLoop == null)
            throw new IllegalStateException("CH turncost don't support multiple p-turns at a single node yet");

        if (bestLoop != null) {
            expandEdge((CHEdgeIteratorState) bestLoop, reverse, skipped1.getEdge());
        }
    }
}
