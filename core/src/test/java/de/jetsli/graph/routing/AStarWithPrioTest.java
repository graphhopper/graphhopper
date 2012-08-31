/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.routing;

import de.jetsli.graph.reader.PrepareRoutingShortcuts;
import de.jetsli.graph.storage.EdgePrioFilter;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.storage.PriorityGraphImpl;
import de.jetsli.graph.util.EdgeFilter;
import de.jetsli.graph.util.EdgeIterator;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class AStarWithPrioTest extends AbstractRoutingAlgorithmTester {

    @Override
    public RoutingAlgorithm createAlgo(Graph g) {
        if (g instanceof PriorityGraph)
            return new DijkstraBidirectionRef(g).setEdgeFilterWrapper(new EdgePrioFilter((PriorityGraph) g));

        return new DijkstraBidirectionRef(g);
    }

    @Override
    PriorityGraph createGraph(int size) {
        return new PriorityGraphImpl(size);
    }

    @Test @Override
    public void testBidirectional() {
        PriorityGraph g2 = createGraph(6);
        initBiGraph(g2);
        new PrepareRoutingShortcuts(g2).doWork();
        Path p = createAlgo(g2).calcPath(0, 4);
        assertEquals(p.toString(), 51, p.distance(), 1e-6);
        assertEquals(p.toString(), 5, p.locations());
    }
}
