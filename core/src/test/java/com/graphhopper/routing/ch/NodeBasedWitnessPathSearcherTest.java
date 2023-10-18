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

class NodeBasedWitnessPathSearcherTest {

    @Test
    void ignoreNode() {
        //  /- 3 -\
        // 0 - 1 - 2
        CHPreparationGraph p = CHPreparationGraph.nodeBased(5, 10);
        p.addEdge(0, 1, 0, 10, 10);
        p.addEdge(1, 2, 1, 10, 10);
        p.addEdge(0, 3, 2, 9, 9);
        p.addEdge(3, 2, 3, 9, 9);
        p.prepareForContraction();
        NodeBasedWitnessPathSearcher algo = new NodeBasedWitnessPathSearcher(p);
        // just use 1 as ignore node and make sure the witness 0-3-2 is found.
        algo.init(0, 1);
        assertEquals(18, algo.findUpperBound(2, 100, Integer.MAX_VALUE));
        // if we ignore 3 instead we get the longer path
        algo.init(0, 3);
        assertEquals(20, algo.findUpperBound(2, 100, Integer.MAX_VALUE));
        assertEquals(2, algo.getSettledNodes());
    }

    @Test
    void acceptedWeight() {
        //  /-----------\
        // 0 - 1 - ... - 5
        CHPreparationGraph p = CHPreparationGraph.nodeBased(10, 10);
        p.addEdge(0, 5, 0, 10, 10);
        for (int i = 0; i < 5; i++)
            p.addEdge(i, i + 1, i + 1, 1, 1);
        p.prepareForContraction();
        NodeBasedWitnessPathSearcher algo = new NodeBasedWitnessPathSearcher(p);
        algo.init(0, -1);
        // here we set acceptable weight to 100, so even the suboptimal path 0-5 is 'good enough' for us and the search
        // stops as soon as 0-5 has been discovered
        assertEquals(10, algo.findUpperBound(5, 100, Integer.MAX_VALUE));
        assertEquals(1, algo.getSettledNodes());
        // .. repeating this over and over does not change continue the search
        assertEquals(10, algo.findUpperBound(5, 100, Integer.MAX_VALUE));
        assertEquals(10, algo.findUpperBound(5, 100, Integer.MAX_VALUE));
        assertEquals(10, algo.findUpperBound(5, 100, Integer.MAX_VALUE));
        assertEquals(1, algo.getSettledNodes());

        // if we lower our requirement we enforce a longer search and find the actual shortest path
        algo.init(0, -1);
        assertEquals(5, algo.findUpperBound(5, 8, Integer.MAX_VALUE));
        // if we lower it further (below the shortest path weight) we might not find the shortest path and get the
        // sup optimal weight again. however, we know for sure that there is no path with weight <= 1.
        algo.init(0, -1);
        assertEquals(10, algo.findUpperBound(5, 1, Integer.MAX_VALUE));
        assertEquals(2, algo.getSettledNodes());
    }

    @Test
    void settledNodes() {
        //  /-----------\
        // 0 - 1 - ... - 5
        CHPreparationGraph p = CHPreparationGraph.nodeBased(10, 10);
        p.addEdge(0, 5, 0, 10, 10);
        for (int i = 0; i < 5; i++)
            p.addEdge(i, i + 1, i + 1, 1, 1);
        p.prepareForContraction();
        NodeBasedWitnessPathSearcher algo = new NodeBasedWitnessPathSearcher(p);
        algo.init(0, -1);
        assertEquals(5, algo.findUpperBound(5, 5, Integer.MAX_VALUE));
        assertEquals(5, algo.getSettledNodes());
        algo.init(0, -1);
        assertEquals(10, algo.findUpperBound(5, 5, 2));
        assertEquals(2, algo.getSettledNodes());
        algo.init(0, -1);
        assertEquals(Double.POSITIVE_INFINITY, algo.findUpperBound(5, 5, 0));
        assertEquals(0, algo.getSettledNodes());
        // repeating the search does not change the number of settled nodes
        algo.init(0, -1);
        assertEquals(10, algo.findUpperBound(5, 5, 2));
        assertEquals(2, algo.getSettledNodes());
        assertEquals(10, algo.findUpperBound(5, 5, 2));
        assertEquals(10, algo.findUpperBound(5, 5, 2));
        assertEquals(10, algo.findUpperBound(5, 5, 2));
        assertEquals(2, algo.getSettledNodes());
    }

}