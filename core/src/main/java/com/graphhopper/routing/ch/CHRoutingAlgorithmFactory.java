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
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Routing.ALGORITHM;
import static com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES;

public class CHRoutingAlgorithmFactory {
    private final CHConfig chConfig;

    public CHRoutingAlgorithmFactory(CHGraph chGraph) {
        this.chConfig = chGraph.getCHConfig();
    }

    public RoutingAlgorithm createAlgo(Graph graph, PMap opts) {
        // todo: This method does not really fit for CH: We get a graph, but really we already know which
        // graph we have to use: the CH graph. Same with  opts.weighting: The CHConfig already contains a weighting
        // and we cannot really use it here. The real reason we do this the way its done atm is that graph might be
        // a QueryGraph that wraps (our) CHGraph.
        RoutingAlgorithm algo = doCreateAlgo(graph, opts);
        if (opts.has(MAX_VISITED_NODES))
            algo.setMaxVisitedNodes(opts.getInt(MAX_VISITED_NODES, Integer.MAX_VALUE));
        return algo;
    }

    private RoutingAlgorithm doCreateAlgo(Graph graph, PMap opts) {
        if (chConfig.isEdgeBased()) {
            // important: do not simply take the turn cost storage from ghStorage, because we need the wrapped storage from
            // query graph!
            TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
            if (turnCostStorage == null) {
                throw new IllegalArgumentException("For edge-based CH you need a turn cost extension");
            }
            RoutingCHGraph g = new RoutingCHGraphImpl(graph, graph.wrapWeighting(getWeighting()));
            return createAlgoEdgeBased(g, opts);
        } else {
            RoutingCHGraph g = new RoutingCHGraphImpl(graph, getWeighting());
            return createAlgoNodeBased(g, opts);
        }
    }

    private RoutingAlgorithm createAlgoEdgeBased(RoutingCHGraph g, PMap opts) {
        // todo: AStar is much faster for edge-based but currently we cannot make it the default because
        //       of #2061
        String defaultAlgo = DIJKSTRA_BI;
        String algo = opts.getString(ALGORITHM, defaultAlgo);
        if (Helper.isEmpty(algo))
            algo = defaultAlgo;
        if (ASTAR_BI.equals(algo)) {
            return new AStarBidirectionEdgeCHNoSOD(g)
                    .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, getWeighting(), g.getGraph().getNodeAccess()));
        } else if (DIJKSTRA_BI.equals(algo)) {
            return new DijkstraBidirectionEdgeCHNoSOD(g);
        } else if (ALT_ROUTE.equalsIgnoreCase(algo)) {
            return new AlternativeRouteEdgeCH(g, opts);
        } else {
            throw new IllegalArgumentException("Algorithm " + algo + " not supported for edge-based Contraction Hierarchies. Try with ch.disable=true");
        }
    }

    private RoutingAlgorithm createAlgoNodeBased(RoutingCHGraph g, PMap opts) {
        // use dijkstra by default for node-based (its faster)
        String defaultAlgo = DIJKSTRA_BI;
        String algo = opts.getString(ALGORITHM, defaultAlgo);
        if (Helper.isEmpty(algo))
            algo = defaultAlgo;
        if (ASTAR_BI.equals(algo)) {
            return new AStarBidirectionCH(g)
                    .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, getWeighting(), g.getGraph().getNodeAccess()));
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

    public Weighting getWeighting() {
        return chConfig.getWeighting();
    }

    public CHConfig getCHConfig() {
        return chConfig;
    }
}
