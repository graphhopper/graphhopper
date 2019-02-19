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
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PMap;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WitnessPathSearcherTest {

    private GraphHopperStorage graph;
    private CHGraph chGraph;
    private TurnWeighting chTurnWeighting;

    @Before
    public void setup() {
        CarFlagEncoder encoder = new CarFlagEncoder(5, 5, 10);
        EncodingManager encodingManager = EncodingManager.create(encoder);
        Weighting weighting = new ShortestWeighting(encoder);
        PreparationWeighting preparationWeighting = new PreparationWeighting(weighting);
        graph = new GraphBuilder(encodingManager).setCHGraph(weighting).setEdgeBasedCH(true).create();
        TurnCostExtension turnCostExtension = (TurnCostExtension) graph.getExtension();
        chTurnWeighting = new TurnWeighting(preparationWeighting, turnCostExtension);
        chGraph = graph.getGraph(CHGraph.class);
    }

    @Test
    public void test_shortcut_needed_basic() {
        // 0 -> 1 -> 2 -> 3 -> 4
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        WitnessPathSearcher finder = createFinder();
        finder.initSearch(2, 1, 0);
        CHEntry result = finder.runSearch(3, 3);
        CHEntry expected = new ExpectedResultBuilder(3, 2, 2, 2.0)
                .withParent(2, 1, 1, 1.0)
                .build(1);
        assertFinderResult(expected, result);
    }

    @Test
    public void test_shortcut_needed_bidirectional() {
        // 0 -> 1 -> 2 -> 3 -> 4
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.freeze();
        setMaxLevelOnAllNodes();
        WitnessPathSearcher finder = createFinder();
        finder.initSearch(2, 1, 0);
        CHEntry result = finder.runSearch(3, 3);
        CHEntry expected = new ExpectedResultBuilder(3, 2, 2, 2.0)
                .withParent(2, 1, 1, 1.0)
                .build(1);
        assertFinderResult(expected, result);
    }

    @Test
    public void test_witness_basic() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \       /
        //        \> 5 >/
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 2, false);
        graph.edge(3, 4, 1, false);
        graph.edge(1, 5, 1, false);
        graph.edge(5, 3, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        WitnessPathSearcher finder = createFinder();
        finder.initSearch(2, 1, 0);
        CHEntry result = finder.runSearch(3, 3);
        assertNull(result);
    }

    @Test
    public void test_witness_bidirectional() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \       /
        //        \> 5 >/
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 2, true);
        graph.edge(3, 4, 1, true);
        graph.edge(1, 5, 1, true);
        graph.edge(5, 3, 1, true);
        graph.freeze();
        setMaxLevelOnAllNodes();
        WitnessPathSearcher finder = createFinder();
        finder.initSearch(2, 1, 0);
        CHEntry result = finder.runSearch(3, 3);
        assertNull(result);
    }

    private WitnessPathSearcher createFinder() {
        return new WitnessPathSearcher(chGraph, chTurnWeighting, new PMap());
    }

    private void setMaxLevelOnAllNodes() {
        int nodes = chGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            chGraph.setLevel(node, nodes);
        }
    }

    private void assertFinderResult(CHEntry expected, CHEntry result) {
        while (expected.parent != null) {
            assertEquals(expected.adjNode, result.adjNode);
            assertEquals(expected.edge, result.edge);
            assertEquals(expected.incEdge, result.incEdge);
            assertEquals(expected.weight, result.weight, 1.e-6);
            expected = expected.getParent();
            result = result.getParent();
        }
    }

    private static class ExpectedResultBuilder {
        private CHEntry result;
        private CHEntry last;

        private ExpectedResultBuilder(int adjNode, int edge, int incEdge, double weight) {
            result = new CHEntry(edge, incEdge, adjNode, weight);
            last = result;
        }

        ExpectedResultBuilder withParent(int adjNode, int edge, int incEdge, double weight) {
            CHEntry parent = new CHEntry(edge, incEdge, adjNode, weight);
            last.parent = parent;
            last = parent;
            return this;
        }

        CHEntry build(int firstEdge) {
            last.parent = new CHEntry(EdgeIterator.NO_EDGE, firstEdge, -1, 0.0);
            return result;
        }

    }

}