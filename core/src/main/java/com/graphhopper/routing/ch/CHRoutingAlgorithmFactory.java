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

import com.graphhopper.routing.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Routing.ALGORITHM;
import static com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES;

/**
 * Given a {@link RoutingCHGraph} and possibly a {@link QueryGraph} this class sets up and creates routing
 * algorithm instances used for CH.
 */
public class CHRoutingAlgorithmFactory {
    private final RoutingCHGraph routingCHGraph;

    public CHRoutingAlgorithmFactory(RoutingCHGraph routingCHGraph, QueryGraph queryGraph) {
        this(new QueryRoutingCHGraph(routingCHGraph, queryGraph));
    }

    public CHRoutingAlgorithmFactory(RoutingCHGraph routingCHGraph) {
        this.routingCHGraph = routingCHGraph;
    }

    public EdgeToEdgeRoutingAlgorithm createAlgo(PMap opts) {
        EdgeToEdgeRoutingAlgorithm algo = routingCHGraph.isEdgeBased()
                ? createAlgoEdgeBased(routingCHGraph, opts)
                : createAlgoNodeBased(routingCHGraph, opts);
        if (opts.has(MAX_VISITED_NODES))
            algo.setMaxVisitedNodes(opts.getInt(MAX_VISITED_NODES, Integer.MAX_VALUE));
        return algo;
    }

    private EdgeToEdgeRoutingAlgorithm createAlgoEdgeBased(RoutingCHGraph g, PMap opts) {
        String defaultAlgo = ASTAR_BI;
        String algo = opts.getString(ALGORITHM, defaultAlgo);
        if (Helper.isEmpty(algo))
            algo = defaultAlgo;
        if (ASTAR_BI.equals(algo)) {
            return new AStarBidirectionEdgeCHNoSOD(g)
                    .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, getWeighting(), g.getBaseGraph().getNodeAccess()));
        } else if (DIJKSTRA_BI.equals(algo)) {
            return new DijkstraBidirectionEdgeCHNoSOD(g);
        } else if (ALT_ROUTE.equalsIgnoreCase(algo)) {
            return new AlternativeRouteEdgeCH(g, opts);
        } else {
            throw new IllegalArgumentException("Algorithm " + algo + " not supported for edge-based Contraction Hierarchies. Try with ch.disable=true");
        }
    }

    private EdgeToEdgeRoutingAlgorithm createAlgoNodeBased(RoutingCHGraph g, PMap opts) {
        // use dijkstra by default for node-based (its faster)
        String defaultAlgo = DIJKSTRA_BI;
        String algo = opts.getString(ALGORITHM, defaultAlgo);
        if (Helper.isEmpty(algo))
            algo = defaultAlgo;
        if (ASTAR_BI.equals(algo)) {
            return new AStarBidirectionCH(g)
                    .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, getWeighting(), g.getBaseGraph().getNodeAccess()));
        } else if (DIJKSTRA_BI.equals(algo) || Helper.isEmpty(algo)) {
            if (opts.getBool("stall_on_demand", true)) {
                return new DijkstraBidirectionCH(g);
            } else {
                return new DijkstraBidirectionCHNoSOD(g);
            }
        } else if (ALT_ROUTE.equalsIgnoreCase(algo)) {
            return new AlternativeRouteCH(g, opts);
        } else {
            throw new IllegalArgumentException("Algorithm " + algo + " not supported for node-based Contraction Hierarchies. Try with ch.disable=true");
        }
    }

    private Weighting getWeighting() {
        return routingCHGraph.getWeighting();
    }
}
