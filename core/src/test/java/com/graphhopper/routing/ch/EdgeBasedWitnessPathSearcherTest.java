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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EdgeBasedWitnessPathSearcherTest {

    @Test
    public void test_shortcut_needed_basic() {
        // 0 -> 1 -> 2 -> 3 -> 4
        CHPreparationGraph graph = CHPreparationGraph.edgeBased(5, 4, (in, via, out) -> in == out ? 10 : 0);
        int edge = 0;
        graph.addEdge(0, 1, edge++, 10, Double.POSITIVE_INFINITY);
        graph.addEdge(1, 2, edge++, 10, Double.POSITIVE_INFINITY);
        graph.addEdge(2, 3, edge++, 10, Double.POSITIVE_INFINITY);
        graph.addEdge(3, 4, edge++, 10, Double.POSITIVE_INFINITY);
        graph.prepareForContraction();
        EdgeBasedWitnessPathSearcher searcher = new EdgeBasedWitnessPathSearcher(graph);
        searcher.initSearch(0, 1, 2, new EdgeBasedWitnessPathSearcher.Stats());
        double weight = searcher.runSearch(3, 6, 20.0, 100);
        assertTrue(Double.isInfinite(weight));
    }

    @Test
    public void test_shortcut_needed_bidirectional() {
        // 0 -> 1 -> 2 -> 3 -> 4
        CHPreparationGraph graph = CHPreparationGraph.edgeBased(5, 4, (in, via, out) -> in == out ? 10 : 0);
        int edge = 0;
        graph.addEdge(0, 1, edge++, 10, 10);
        graph.addEdge(1, 2, edge++, 10, 10);
        graph.addEdge(2, 3, edge++, 10, 10);
        graph.addEdge(3, 4, edge++, 10, 10);
        graph.prepareForContraction();
        EdgeBasedWitnessPathSearcher searcher = new EdgeBasedWitnessPathSearcher(graph);
        searcher.initSearch(0, 1, 2, new EdgeBasedWitnessPathSearcher.Stats());
        double weight = searcher.runSearch(3, 6, 20.0, 100);
        assertTrue(Double.isInfinite(weight));
    }

    @Test
    public void test_witness_basic() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \       /
        //        \> 5 >/
        CHPreparationGraph graph = CHPreparationGraph.edgeBased(6, 6, (in, via, out) -> in == out ? 10 : 0);
        int edge = 0;
        graph.addEdge(0, 1, edge++, 10, Double.POSITIVE_INFINITY);
        graph.addEdge(1, 2, edge++, 10, Double.POSITIVE_INFINITY);
        graph.addEdge(2, 3, edge++, 20, Double.POSITIVE_INFINITY);
        graph.addEdge(3, 4, edge++, 10, Double.POSITIVE_INFINITY);
        graph.addEdge(1, 5, edge++, 10, Double.POSITIVE_INFINITY);
        graph.addEdge(5, 3, edge++, 10, Double.POSITIVE_INFINITY);
        graph.prepareForContraction();
        EdgeBasedWitnessPathSearcher searcher = new EdgeBasedWitnessPathSearcher(graph);
        searcher.initSearch(0, 1, 2, new EdgeBasedWitnessPathSearcher.Stats());
        double weight = searcher.runSearch(3, 6, 30.0, 100);
        assertEquals(20, weight, 1.e-6);
    }

    @Test
    public void test_witness_bidirectional() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \       /
        //        \> 5 >/
        CHPreparationGraph graph = CHPreparationGraph.edgeBased(6, 6, (in, via, out) -> in == out ? 10 : 0);
        int edge = 0;
        graph.addEdge(0, 1, edge++, 10, 10);
        graph.addEdge(1, 2, edge++, 10, 10);
        graph.addEdge(2, 3, edge++, 20, 20);
        graph.addEdge(3, 4, edge++, 10, 10);
        graph.addEdge(1, 5, edge++, 10, 10);
        graph.addEdge(5, 3, edge++, 10, 10);
        graph.prepareForContraction();
        EdgeBasedWitnessPathSearcher searcher = new EdgeBasedWitnessPathSearcher(graph);
        searcher.initSearch(0, 1, 2, new EdgeBasedWitnessPathSearcher.Stats());
        double weight = searcher.runSearch(3, 6, 30.0, 100);
        assertEquals(20, weight, 1.e-6);
    }

}