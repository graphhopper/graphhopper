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

public interface RoutingCHGraph {
    int getNodes();

    long getEdges();

    long getShortcuts();

    /**
     * Traverses the base edges and shortcuts at a given node. This will only include shortcuts coming from higher
     * level nodes, but *all* base edges with finite weight.
     */
    RoutingCHEdgeExplorer createInEdgeExplorer();

    /**
     * @see #createInEdgeExplorer() but here the shortcuts/edges are going out of the given node.
     */
    RoutingCHEdgeExplorer createOutEdgeExplorer();

    RoutingCHEdgeIteratorState getEdgeIteratorState(long chEdge, int adjNode);

    int getLevel(int node);

    double getTurnWeight(int inEdge, int viaNode, int outEdge);

    /**
     * @return the graph this CH graph is based on, i.e. a the base {@link Graph} or a {@link QueryGraph} on top of the
     * base graph
     * todo: maybe it would be better to remove this method and use a direct reference to the base graph when it is
     * needed
     */
    Graph getBaseGraph();

    boolean hasTurnCosts();

    boolean isEdgeBased();

    Weighting getWeighting();

    // todo: would like to get rid of this
    void close();
}
