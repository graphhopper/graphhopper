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

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeBasedWitnessPathSearcherTest {

    private final CarFlagEncoder encoder = new CarFlagEncoder();
    private final EncodingManager encodingManager = EncodingManager.create(encoder);
    private final Weighting weighting = new ShortestWeighting(encoder);
    private final GraphHopperStorage graph = new GraphBuilder(encodingManager).setCHConfigs(CHConfig.nodeBased("profile", weighting)).create();
    private final CHGraph lg = graph.getCHGraph();

    @Test
    public void testShortestPathSkipNode() {
        createExampleGraph();
        CHPreparationGraph prepareGraph = CHPreparationGraph.nodeBased(graph.getNodes(), graph.getEdges());
        CHPreparationGraph.buildFromGraph(prepareGraph, graph, weighting);
        final double normalDist = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(4, 2).getDistance();
        NodeBasedWitnessPathSearcher algo = new NodeBasedWitnessPathSearcher(prepareGraph);

        setMaxLevelOnAllNodes();

        algo.ignoreNode(3);
        algo.setWeightLimit(100);
        int nodeEntry = algo.findEndNode(4, 2);
        assertTrue(algo.getWeight(nodeEntry) > normalDist);

        algo.clear();
        algo.setMaxVisitedNodes(1);
        nodeEntry = algo.findEndNode(4, 2);
        assertEquals(-1, nodeEntry);
    }

    @Test
    public void testShortestPathSkipNode2() {
        createExampleGraph();
        CHPreparationGraph prepareGraph = CHPreparationGraph.nodeBased(graph.getNodes(), graph.getEdges());
        CHPreparationGraph.buildFromGraph(prepareGraph, graph, weighting);
        final double normalDist = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(4, 2).getDistance();
        assertEquals(3, normalDist, 1e-5);
        NodeBasedWitnessPathSearcher algo = new NodeBasedWitnessPathSearcher(prepareGraph);

        setMaxLevelOnAllNodes();

        algo.ignoreNode(3);
        algo.setWeightLimit(10);
        int nodeEntry = algo.findEndNode(4, 2);
        assertEquals(4, algo.getWeight(nodeEntry), 1e-5);

        nodeEntry = algo.findEndNode(4, 1);
        assertEquals(4, algo.getWeight(nodeEntry), 1e-5);
    }

    @Test
    public void testShortestPathLimit() {
        createExampleGraph();
        CHPreparationGraph prepareGraph = CHPreparationGraph.nodeBased(graph.getNodes(), graph.getEdges());
        CHPreparationGraph.buildFromGraph(prepareGraph, graph, weighting);
        NodeBasedWitnessPathSearcher algo = new NodeBasedWitnessPathSearcher(prepareGraph);

        setMaxLevelOnAllNodes();

        algo.ignoreNode(0);
        algo.setWeightLimit(2);
        int endNode = algo.findEndNode(4, 1);
        // did not reach endNode
        assertNotEquals(1, endNode);
    }

    private void createExampleGraph() {
        //5-1-----2
        //   \ __/|
        //    0   |
        //   /    |
        //  4-----3
        //
        graph.edge(0, 1, 1, true);
        graph.edge(0, 2, 1, true);
        graph.edge(0, 4, 3, true);
        graph.edge(1, 2, 3, true);
        graph.edge(2, 3, 1, true);
        graph.edge(4, 3, 2, true);
        graph.edge(5, 1, 2, true);
        graph.freeze();
    }

    private void setMaxLevelOnAllNodes() {
        int nodes = lg.getNodes();
        for (int node = 0; node < nodes; node++) {
            lg.setLevel(node, nodes);
        }
    }
}