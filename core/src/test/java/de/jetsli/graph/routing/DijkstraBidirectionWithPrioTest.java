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
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class DijkstraBidirectionWithPrioTest {

    RoutingAlgorithm createAlgoWithFilter(final PriorityGraph pg) {
        return new DijkstraBidirectionRef(pg).setEdgeFilter(new EdgePrioFilter(pg));
    }
    
    RoutingAlgorithm createAlgoWithFilterAndPathUnpacking(final PriorityGraph pg) {
        return new DijkstraBidirectionRef(pg) {
            @Override protected PathWrapperRef createPathWrapper() {
                return new PathWrapperPrio(pg);
            }
        }.setEdgeFilter(new EdgePrioFilter(pg));
    }

    PriorityGraph createGraph(int size) {
        return new PriorityGraphImpl(size);
    }

    @Test
    public void testShortcutUnpacking() {
        PriorityGraph g2 = createGraph(6);
        AbstractRoutingAlgorithmTester.initBiGraph(g2);
        // store skipped first node along with the shortcut
        new PrepareRoutingShortcuts(g2).doWork();
        // use that node to correctly unpack the shortcut
        Path p = createAlgoWithFilterAndPathUnpacking(g2).calcPath(0, 4);
        assertEquals(p.toString(), 51, p.distance(), 1e-6);
        assertEquals(p.toString(), 6, p.locations());
    }

    @Test
    public void testShortcutNoUnpacking() {
        PriorityGraph g2 = createGraph(6);
        AbstractRoutingAlgorithmTester.initBiGraph(g2);
        new PrepareRoutingShortcuts(g2).doWork();
        Path p = createAlgoWithFilter(g2).calcPath(0, 4);
        assertEquals(p.toString(), 51, p.distance(), 1e-6);
        assertEquals(p.toString(), 5, p.locations());
    }
}
