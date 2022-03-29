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

import com.graphhopper.routing.weighting.Weighting;

public class RoutingCHGraphImpl implements RoutingCHGraph {
    private final BaseGraph baseGraph;
    private final CHStorage chStorage;
    private final Weighting weighting;

    /**
     * @deprecated currently we use this only for easier GraphHopperStorage -> BaseGraph migration
     *             instead of just calling graph.getBaseGraph() better try to convert graph to a BaseGraph
     */
    @Deprecated
    public static RoutingCHGraph fromGraph(Graph graph, CHStorage chStorage, CHConfig chConfig) {
        return fromGraph(graph.getBaseGraph(), chStorage, chConfig);
    }

    public static RoutingCHGraph fromGraph(BaseGraph baseGraph, CHStorage chStorage, CHConfig chConfig) {
        return new RoutingCHGraphImpl(baseGraph, chStorage, chConfig.getWeighting());
    }

    public RoutingCHGraphImpl(BaseGraph baseGraph, CHStorage chStorage, Weighting weighting) {
        if (weighting.hasTurnCosts() && !chStorage.isEdgeBased())
            throw new IllegalArgumentException("Weighting has turn costs, but CHStorage is node-based");
        this.baseGraph = baseGraph;
        this.chStorage = chStorage;
        this.weighting = weighting;
    }

    @Override
    public int getNodes() {
        return baseGraph.getNodes();
    }

    @Override
    public int getEdges() {
        return baseGraph.getEdges() + chStorage.getShortcuts();
    }

    @Override
    public int getShortcuts() {
        return chStorage.getShortcuts();
    }

    @Override
    public RoutingCHEdgeExplorer createInEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.inEdges(chStorage, baseGraph, weighting);
    }

    @Override
    public RoutingCHEdgeExplorer createOutEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.outEdges(chStorage, baseGraph, weighting);
    }

    @Override
    public RoutingCHEdgeIteratorState getEdgeIteratorState(int chEdge, int adjNode) {
        RoutingCHEdgeIteratorStateImpl edgeState =
                new RoutingCHEdgeIteratorStateImpl(chStorage, baseGraph, new BaseGraph.EdgeIteratorStateImpl(baseGraph), weighting);
        if (edgeState.init(chEdge, adjNode))
            return edgeState;
        // if edgeId exists, but adjacent nodes do not match
        return null;
    }

    @Override
    public int getLevel(int node) {
        return chStorage.getLevel(chStorage.toNodePointer(node));
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
        return chStorage.isEdgeBased();
    }

    @Override
    public double getTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
        return weighting.calcTurnWeight(edgeFrom, nodeVia, edgeTo);
    }

    @Override
    public void close() {
        if (!baseGraph.isClosed()) baseGraph.close();
        chStorage.close();
    }
}
