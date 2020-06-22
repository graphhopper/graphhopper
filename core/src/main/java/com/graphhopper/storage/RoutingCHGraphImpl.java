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

package com.graphhopper.storage;

import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

public class RoutingCHGraphImpl implements RoutingCHGraph {
    private final Graph baseGraph;
    private final CHGraph chGraph;
    private final Weighting weighting;

    public RoutingCHGraphImpl(CHGraph chGraph) {
        if (chGraph instanceof QueryGraph)
            throw new IllegalArgumentException("Do not create RoutingCHGraphImpl with a QueryGraph, use QueryRoutingCHGraph instead");
        Weighting weighting = chGraph.getCHConfig().getWeighting();
        if (weighting.hasTurnCosts() && !chGraph.getCHConfig().isEdgeBased())
            throw new IllegalArgumentException("Weighting has turn costs, but CHGraph is node-based");
        this.chGraph = chGraph;
        this.baseGraph = chGraph.getBaseGraph();
        this.weighting = weighting;
    }

    @Override
    public int getNodes() {
        return chGraph.getNodes();
    }

    @Override
    public int getEdges() {
        return chGraph.getEdges();
    }

    @Override
    public int getOtherNode(int shortcut, int node) {
        return chGraph.getOtherNode(shortcut, node);
    }

    @Override
    public boolean isAdjacentToNode(int shortcut, int node) {
        return chGraph.isAdjacentToNode(shortcut, node);
    }

    @Override
    public RoutingCHEdgeExplorer createInEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.inEdges(chGraph.createEdgeExplorer(), weighting);
    }

    @Override
    public RoutingCHEdgeExplorer createOutEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.outEdges(chGraph.createEdgeExplorer(), weighting);
    }

    @Override
    public RoutingCHEdgeIteratorState getEdgeIteratorState(int shortcut, int adjNode) {
        EdgeIteratorState edgeState = chGraph.getEdgeIteratorState(shortcut, adjNode);
        return edgeState == null ? null : new RoutingCHEdgeIteratorStateImpl(edgeState, weighting);
    }

    @Override
    public int getLevel(int node) {
        return chGraph.getLevel(node);
    }

    @Override
    public Graph getBaseGraph() {
        return baseGraph;
    }

    @Override
    public Weighting getWeighting() {
        return weighting;
    }

    @Override
    public boolean hasTurnCosts() {
        return weighting.hasTurnCosts();
    }

    @Override
    public boolean isEdgeBased() {
        return chGraph.getCHConfig().isEdgeBased();
    }

    @Override
    public double getTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
        return weighting.calcTurnWeight(edgeFrom, nodeVia, edgeTo);
    }

}
