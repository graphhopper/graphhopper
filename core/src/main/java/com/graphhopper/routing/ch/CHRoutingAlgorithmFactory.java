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
import com.graphhopper.routing.util.LevelEdgeFilter;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostStorage;

import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;

public class CHRoutingAlgorithmFactory implements RoutingAlgorithmFactory {
    private final CHGraph chGraph;
    private final CHProfile chProfile;
    private final PreparationWeighting prepareWeighting;

    public CHRoutingAlgorithmFactory(CHGraph chGraph) {
        this.chGraph = chGraph;
        this.chProfile = chGraph.getCHProfile();
        prepareWeighting = new PreparationWeighting(chProfile.getWeighting());
    }

    @Override
    public RoutingAlgorithm createAlgo(Graph graph, AlgorithmOptions opts) {
        // todo: This method does not really fit for CH: We are passed a graph, but really we already know which
        // graph we have to use: the CH graph. Same with  opts.weighting: The CHProfile already contains a weighting
        // and we cannot really use it here. The real reason we do this the way its done atm is that graph might be
        // a QueryGraph that wraps (our) CHGraph.
        AbstractBidirAlgo algo = doCreateAlgo(graph, opts);
        algo.setEdgeFilter(new LevelEdgeFilter(chGraph));
        algo.setMaxVisitedNodes(opts.getMaxVisitedNodes());
        return algo;
    }

    private AbstractBidirAlgo doCreateAlgo(Graph graph, AlgorithmOptions opts) {
        if (chProfile.isEdgeBased()) {
            return createAlgoEdgeBased(graph, opts);
        } else {
            return createAlgoNodeBased(graph, opts);
        }
    }

    private AbstractBidirAlgo createAlgoEdgeBased(Graph graph, AlgorithmOptions opts) {
        if (ASTAR_BI.equals(opts.getAlgorithm())) {
            return new AStarBidirectionEdgeCHNoSOD(graph, createTurnWeightingForEdgeBased(graph))
                    .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, graph.getNodeAccess()));
        } else if (DIJKSTRA_BI.equals(opts.getAlgorithm())) {
            return new DijkstraBidirectionEdgeCHNoSOD(graph, createTurnWeightingForEdgeBased(graph));
        } else {
            throw new IllegalArgumentException("Algorithm " + opts.getAlgorithm() + " not supported for edge-based Contraction Hierarchies. Try with ch.disable=true");
        }
    }

    private AbstractBidirAlgo createAlgoNodeBased(Graph graph, AlgorithmOptions opts) {
        if (ASTAR_BI.equals(opts.getAlgorithm())) {
            return new AStarBidirectionCH(graph, prepareWeighting)
                    .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, graph.getNodeAccess()));
        } else if (DIJKSTRA_BI.equals(opts.getAlgorithm())) {
            if (opts.getHints().getBool("stall_on_demand", true)) {
                return new DijkstraBidirectionCH(graph, prepareWeighting);
            } else {
                return new DijkstraBidirectionCHNoSOD(graph, prepareWeighting);
            }
        } else {
            throw new IllegalArgumentException("Algorithm " + opts.getAlgorithm() + " not supported for node-based Contraction Hierarchies. Try with ch.disable=true");
        }
    }

    private TurnWeighting createTurnWeightingForEdgeBased(Graph graph) {
        // important: do not simply take the turn cost storage from ghStorage, because we need the wrapped storage from
        // query graph!
        TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
        if (turnCostStorage == null) {
            throw new IllegalArgumentException("For edge-based CH you need a turn cost storage");
        }
        return new TurnWeighting(prepareWeighting, turnCostStorage, chProfile.getUTurnCosts());
    }

    public Weighting getWeighting() {
        return chProfile.getWeighting();
    }

    public CHProfile getCHProfile() {
        return chProfile;
    }
}
