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

import de.jetsli.graph.storage.MemoryGraph;
import de.jetsli.graph.storage.Graph;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraBidirectionRefTest extends AbstractRoutingAlgorithmTester {

    @Override public RoutingAlgorithm createAlgo(Graph g) {        
        return new DijkstraBidirectionRef(g);
    }
    
    @Test
    public void testAddSkipNodes() {
        Graph g = createWikipediaTestGraph();
        Path p = createAlgo(g).calcShortestPath(0, 4);
        assertEquals(p.toString(), 20, p.distance(), 1e-6);
        assertTrue(p.toString(), p.contains(5));
        
        DijkstraBidirectionRef db = new DijkstraBidirectionRef(g);
        db.addSkipNode(5);
        p = db.calcShortestPath(0, 4);        
        assertFalse(p.toString(), p.contains(5));
    }
    
    @Test
    public void testCannotCalculateSP2() {
        Graph g = new MemoryGraph();
        g.edge(0, 1, 1, false);
        g.edge(1, 2, 1, false);
               
        DijkstraBidirectionRef db = new DijkstraBidirectionRef(g);
        db.addSkipNode(1);
        Path p = db.calcShortestPath(0, 2);
        assertNull(p);
    }
}
