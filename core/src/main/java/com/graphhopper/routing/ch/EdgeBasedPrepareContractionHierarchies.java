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

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.DijkstraBidirectionEdgeCHNoSOD;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.util.LevelEdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Dummy implementation to use edge based contraction hierarchies with manual contraction orders, only kept for testing
 * and debugging purposes at the moment.
 *
 * @see PrepareContractionHierarchies
 */
public class EdgeBasedPrepareContractionHierarchies extends AbstractAlgoPreparation implements RoutingAlgorithmFactory {
    private final GraphHopperStorage ghStorage;
    private final CHGraph chGraph;
    private final TurnWeighting chTurnWeighting;
    private List<Integer> contractionOrder;

    public EdgeBasedPrepareContractionHierarchies(GraphHopperStorage ghStorage, CHGraph chGraph, Weighting weighting) {
        this.ghStorage = ghStorage;
        this.chGraph = chGraph;
        GraphExtension extension = ghStorage.getExtension();
        if (!(extension instanceof TurnCostExtension)) {
            throw new IllegalArgumentException("need a turn cost extension");
        }
        TurnCostExtension turnCostExtension = (TurnCostExtension) extension;
        // todo: should the weighting wrapping happen here ? for example to use Dijkstra with turn costs one has to
        // pass a TurnWeighting to it...
        chTurnWeighting = new TurnWeighting(new PreparationWeighting(weighting), turnCostExtension);
    }

    public EdgeBasedPrepareContractionHierarchies usingContractionOrder(List<Integer> contractionOrder) {
        this.contractionOrder = contractionOrder;
        return this;
    }

    public EdgeBasedPrepareContractionHierarchies usingRandomContractionOrder() {
        this.contractionOrder = createRandomContractionOrder();
        return this;
    }

    @Override
    protected void doSpecificWork() {
        EdgeBasedNodeContractor nodeContractor =
                new EdgeBasedNodeContractor(ghStorage, chGraph, chTurnWeighting, TraversalMode.EDGE_BASED_2DIR);
        ghStorage.freeze();
        nodeContractor.initFromGraph();
        setMaxLevelOnAllNodes();
        if (contractionOrder != null) {
            contractAllNodes(nodeContractor, contractionOrder);
        } else {
            contractAllNodes(nodeContractor);
        }

    }

    @Override
    public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
        DijkstraBidirectionEdgeCHNoSOD algo = new DijkstraBidirectionEdgeCHNoSOD(g, chTurnWeighting, TraversalMode.EDGE_BASED_2DIR);
        algo.setEdgeFilter(new LevelEdgeFilter(chGraph));
        return algo;
    }

    private void setMaxLevelOnAllNodes() {
        int nodes = chGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            chGraph.setLevel(node, nodes + 1);
        }
    }

    private void contractAllNodes(EdgeBasedNodeContractor nodeContractor) {
        for (int node = 0; node < chGraph.getNodes(); ++node) {
            nodeContractor.contractNode(node);
            chGraph.setLevel(node, node);
        }
    }

    private void contractAllNodes(EdgeBasedNodeContractor nodeContractor, List<Integer> contractionOrder) {
        if (contractionOrder.size() != chGraph.getNodes()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "contraction order size (%d) must be equal to number of nodes in graph (%d)",
                    contractionOrder.size(), chGraph.getNodes()));
        }
        for (int i = 0; i < contractionOrder.size(); ++i) {
            nodeContractor.contractNode(contractionOrder.get(i));
            chGraph.setLevel(contractionOrder.get(i), i);
        }
    }

    private List<Integer> createRandomContractionOrder() {
        return createRandomIntegerSequence(chGraph.getNodes());
    }

    private List<Integer> createRandomIntegerSequence(int nodes) {
        List<Integer> result = new ArrayList<>(nodes);
        for (int i = 0; i < nodes; ++i) {
            result.add(i);
        }
        // the shuffle method is the only reason we are using java.util.ArrayList instead of hppc IntArrayList
        // using a random contraction order only makes sense for this experimental dummy version anyway
        Collections.shuffle(result);
        return result;
    }
}
