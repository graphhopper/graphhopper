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

import com.graphhopper.coll.GHIntArrayList;
import com.graphhopper.coll.MapEntry;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Recursivly unpack shortcuts.
 *
 * @author Peter Karich
 * @see PrepareContractionHierarchies
 */
public class Path4CH extends PathBidirRef {
    private final Graph routingGraph;
    private final TraversalMode traversalMode;
    private final List<EdgeIteratorState> finalEdges = new ArrayList<>();
    private final EdgeExplorer explorer;

    public Path4CH(Graph routingGraph, Graph baseGraph, Weighting weighting, TraversalMode traversalMode) {
        super(baseGraph, weighting);
        this.routingGraph = routingGraph;
        this.traversalMode = traversalMode;
        this.explorer = routingGraph.createEdgeExplorer(new DefaultEdgeFilter(weighting.getFlagEncoder(), false, true));
    }

    @Override
    public Path extract() {
        Path result = super.extract();

        // TODO why we need this and cannot use the original method via processEdge?
        EdgeIteratorState prevEdge = null;
        time = 0;
        distance = 0;
        System.out.println(finalEdges);
        for (int i = 0; i < finalEdges.size(); i++) {
            EdgeIteratorState edge = finalEdges.get(i);
            if (prevEdge != null && edge.getBaseNode() != prevEdge.getAdjNode())
                throw new IllegalStateException("end node of previous edge should be start node of current but it was " + prevEdge + " -> " + edge);

            distance += edge.getDistance();
            double tmpTime = weighting.calcMillis(edge, false, prevEdge == null ? EdgeIterator.NO_EDGE : prevEdge.getEdge());
            if (tmpTime < 0)
                throw new IllegalStateException("Time cannot be negative " + tmpTime + " for edge " + edge.getEdge() + " " + edge.fetchWayGeometry(3));

            time += tmpTime;
            prevEdge = edge;
        }
        return result;
    }

    @Override
    protected void reverseOrder() {
        super.reverseOrder();

        // reverse final edges too!
        Collections.reverse(finalEdges);
    }

    @Override
    protected final void processEdge(int edgeId, int endNode, int prevEdgeId) {
        // Shortcuts do only contain the weight so expand to get distance and time
        expandEdge((CHEdgeIteratorState) routingGraph.getEdgeIteratorState(edgeId, endNode));
    }

    private void expandEdge(CHEdgeIteratorState mainEdgeState) {
        if (!mainEdgeState.isShortcut()) {
            // TODO all edges are in non-reverse state and the normal processEdge should work
            // hmh, but with these finalEdges we avoid creating edge objects from integers (!)
            // and could implement a more efficient forEveryEdge(EdgeVisitor visitor)
            // TODO see todo in the extract() method
            finalEdges.add(mainEdgeState);
            addEdge(mainEdgeState.getEdge());
            return;
        }

        // main base node ---(skipped edge 1)---> contracted node ---(skipped edge 2)---> main adj node
        int skippedEdge1 = mainEdgeState.getSkippedEdge1();
        int skippedEdge2 = mainEdgeState.getSkippedEdge2();
        int to = mainEdgeState.getAdjNode();
        int contractedNode;

        // getEdgeProps returns null if edge is in the wrong direction like it can be the case if a shortcut is valid (and stored) for both directions.
        CHEdgeIteratorState skipped2 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, to);
        CHEdgeIteratorState skipped1;
        if (skipped2 == null) {
            skipped2 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, to);
            contractedNode = skipped2.getBaseNode();
            skipped1 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge2, contractedNode);
        } else {
            contractedNode = skipped2.getBaseNode();
            skipped1 = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(skippedEdge1, contractedNode);
        }

        if (reverseOrder) {
            expandEdge(skipped2);
            expandLocalLoops(skipped1, skipped2, contractedNode);
            expandEdge(skipped1);
        } else {
            expandEdge(skipped1);
            expandLocalLoops(skipped1, skipped2, contractedNode);
            expandEdge(skipped2);
        }
    }

    private void expandLocalLoops(CHEdgeIteratorState skipped1, CHEdgeIteratorState skipped2, int skippedNode) {
        if (!traversalMode.isEdgeBased())
            return;
        double cost_uv = weighting.calcWeight(skipped1, false, EdgeIterator.NO_EDGE);
        double cost_vw = weighting.calcWeight(skipped2, false, skipped1.getEdge());
        double directCost = cost_uv + cost_vw;
        EdgeIteratorState bestLoop = null;
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

        if (directCost >= Double.MAX_VALUE && bestLoop == null)
            throw new IllegalStateException("CH turncost don't support multiple p-turns at a single node yet");

        if (bestLoop != null) {
            expandEdge((CHEdgeIteratorState) bestLoop);
        }
    }
}
