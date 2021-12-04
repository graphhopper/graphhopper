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

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EdgeBasedWitnessPathSearcherTest {

    private GraphHopperStorage graph;
    private CHStorage chStore;
    private CHStorageBuilder chBuilder;
    private Weighting weighting;
    private FlagEncoder encoder;

    @BeforeEach
    public void setup() {
        encoder = new CarFlagEncoder(5, 5, 10);
        EncodingManager encodingManager = EncodingManager.create(encoder);
        graph = new GraphBuilder(encodingManager).create();
    }

    private void freeze() {
        graph.freeze();
        CHConfig chConfig = CHConfig.edgeBased("p", new ShortestWeighting(encoder, new DefaultTurnCostProvider(encoder, graph.getTurnCostStorage())));
        chStore = graph.createCHStorage(chConfig);
        chBuilder = new CHStorageBuilder(chStore);
        weighting = graph.createCHGraph(chStore, chConfig).getWeighting();
    }

    @Test
    public void test_shortcut_needed_basic() {
        // 0 -> 1 -> 2 -> 3 -> 4
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        freeze();
        setMaxLevelOnAllNodes();
        EdgeBasedWitnessPathSearcher finder = createFinder();
        finder.initSearch(2, 1, 0);
        PrepareCHEntry result = finder.runSearch(3, 3);
        PrepareCHEntry expected = new ExpectedResultBuilder(3, 2, 4, 2.0)
                .withParent(2, 1, 2, 1.0)
                .build(2);
        assertFinderResult(expected, result);
    }

    @Test
    public void test_shortcut_needed_bidirectional() {
        // 0 -> 1 -> 2 -> 3 -> 4
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(1));
        freeze();
        setMaxLevelOnAllNodes();
        EdgeBasedWitnessPathSearcher finder = createFinder();
        finder.initSearch(2, 1, 0);
        PrepareCHEntry result = finder.runSearch(3, 3);
        PrepareCHEntry expected = new ExpectedResultBuilder(3, 2, 4, 2.0)
                .withParent(2, 1, 2, 1.0)
                .build(2);
        assertFinderResult(expected, result);
    }

    @Test
    public void test_witness_basic() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \       /
        //        \> 5 >/
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 3).setDistance(1));
        freeze();
        setMaxLevelOnAllNodes();
        EdgeBasedWitnessPathSearcher finder = createFinder();
        finder.initSearch(2, 1, 0);
        PrepareCHEntry result = finder.runSearch(3, 3);
        assertNull(result);
    }

    @Test
    public void test_witness_bidirectional() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \       /
        //        \> 5 >/
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 5).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(5, 3).setDistance(1));
        freeze();
        setMaxLevelOnAllNodes();
        EdgeBasedWitnessPathSearcher finder = createFinder();
        finder.initSearch(2, 1, 0);
        PrepareCHEntry result = finder.runSearch(3, 3);
        assertNull(result);
    }

    private EdgeBasedWitnessPathSearcher createFinder() {
        CHPreparationGraph.TurnCostFunction turnCostFunction = CHPreparationGraph.buildTurnCostFunctionFromTurnCostStorage(graph, weighting);
        CHPreparationGraph prepareGraph = CHPreparationGraph.edgeBased(graph.getNodes(), graph.getEdges(), turnCostFunction);
        CHPreparationGraph.buildFromGraph(prepareGraph, graph, weighting);
        return new EdgeBasedWitnessPathSearcher(prepareGraph, new PMap());
    }

    private void setMaxLevelOnAllNodes() {
        chBuilder.setLevelForAllNodes(chStore.getNodes());
    }

    private void assertFinderResult(PrepareCHEntry expected, PrepareCHEntry result) {
        while (expected.parent != null) {
            assertEquals(expected.adjNode, result.adjNode);
            assertEquals(expected.prepareEdge, result.prepareEdge);
            assertEquals(expected.incEdgeKey, result.incEdgeKey);
            assertEquals(expected.weight, result.weight, 1.e-6);
            expected = expected.getParent();
            result = result.getParent();
        }
    }

    private static class ExpectedResultBuilder {
        private final PrepareCHEntry result;
        private PrepareCHEntry last;

        private ExpectedResultBuilder(int adjNode, int edge, int incKey, double weight) {
            result = new PrepareCHEntry(edge, incKey, adjNode, weight);
            last = result;
        }

        ExpectedResultBuilder withParent(int adjNode, int edge, int incKey, double weight) {
            PrepareCHEntry parent = new PrepareCHEntry(edge, incKey, adjNode, weight);
            last.parent = parent;
            last = parent;
            return this;
        }

        PrepareCHEntry build(int firstKey) {
            last.parent = new PrepareCHEntry(EdgeIterator.NO_EDGE, firstKey, -1, 0.0);
            return result;
        }

    }

}