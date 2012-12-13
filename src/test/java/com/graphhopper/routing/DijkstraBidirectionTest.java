/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing;

import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import java.util.Arrays;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich,
 */
public class DijkstraBidirectionTest extends AbstractRoutingAlgorithmTester {

    @Override
    public AlgorithmPreparation prepareGraph(Graph g, final WeightCalculation calc) {
        return new NoOpAlgorithmPreparation() {
            @Override public RoutingAlgorithm createAlgo() {
                return new DijkstraBidirection(_graph).setType(calc);
            }
        }.setGraph(g);
    }

    @Test
    public void testAddSkipNodes() {
        Graph g = createWikipediaTestGraph();
        Path p = prepareGraph(g).createAlgo().calcPath(0, 4);
        assertEquals(Arrays.asList(0, 2, 5, 4), p.toNodeList());
        assertEquals(p.toString(), 20, p.weight(), 1e-6);
        assertTrue(p.toString(), p.contains(5));

        DijkstraBidirection algo = new DijkstraBidirection(g);
        algo.addSkipNode(5);
        p = algo.calcPath(0, 4);
        assertFalse(p.toString(), p.contains(5));
    }

    @Test
    public void testCannotCalculateSP2() {
        Graph g = createGraph(10);
        g.edge(0, 1, 1, false);
        g.edge(1, 2, 1, false);

        DijkstraBidirection algo = new DijkstraBidirection(g);
        algo.addSkipNode(1);
        Path p = algo.calcPath(0, 2);
        assertFalse(p.found());
    }
}
