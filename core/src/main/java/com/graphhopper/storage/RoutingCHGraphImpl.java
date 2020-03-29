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
    /**
     * can be a CHGraph or a QueryGraph wrapping a CHGraph
     */
    private final Graph graph;
    /**
     * the CHGraph, might be the same as graph
     */
    private final CHGraph chGraph;
    /**
     * the base graph
     */
    private final Graph baseGraph;
    private final Weighting weighting;

    public RoutingCHGraphImpl(Graph graph, Weighting weighting) {
        this.graph = graph;
        if (graph instanceof QueryGraph) {
            chGraph = (CHGraph) ((QueryGraph) graph).getMainGraph();
        } else {
            chGraph = (CHGraph) graph;
        }
        baseGraph = chGraph.getBaseGraph();
        this.weighting = weighting;
    }

    @Override
    public int getNodes() {
        return graph.getNodes();
    }

    @Override
    public int getEdges() {
        return graph.getEdges();
    }

    @Override
    public int getOriginalEdges() {
        return baseGraph.getEdges();
    }

    @Override
    public int getOtherNode(int edge, int node) {
        return graph.getOtherNode(edge, node);
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        return graph.isAdjacentToNode(edge, node);
    }

    @Override
    public RoutingCHEdgeExplorer createInEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.inEdges(graph.createEdgeExplorer(), weighting);
    }

    @Override
    public RoutingCHEdgeExplorer createOutEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.outEdges(graph.createEdgeExplorer(), weighting);
    }

    @Override
    public RoutingCHEdgeExplorer createAllEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.allEdges(graph.createEdgeExplorer(), weighting);
    }

    @Override
    public RoutingCHEdgeExplorer createOriginalInEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.inEdges(graph.getBaseGraph().createEdgeExplorer(), weighting);
    }

    @Override
    public RoutingCHEdgeExplorer createOriginalOutEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.outEdges(graph.getBaseGraph().createEdgeExplorer(), weighting);
    }

    @Override
    public RoutingCHEdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        EdgeIteratorState edgeState = graph.getEdgeIteratorState(edgeId, adjNode);
        return edgeState == null ? null : new RoutingCHEdgeIteratorStateImpl(edgeState, weighting);
    }

    @Override
    public int getLevel(int node) {
        return chGraph.getLevel(node);
    }

    @Override
    public Graph getGraph() {
        return graph;
    }

    @Override
    public Graph getBaseGraph() {
        return chGraph.getBaseGraph();
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
    public double getTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
        return weighting.calcTurnWeight(edgeFrom, nodeVia, edgeTo);
    }

}
