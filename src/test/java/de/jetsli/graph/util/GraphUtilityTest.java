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
package de.jetsli.graph.util;

import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.GraphStorage;
import de.jetsli.graph.storage.RAMDirectory;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class GraphUtilityTest {

    @Test
    public void testSort() {
        Graph g = new GraphStorage(new RAMDirectory()).createNew(10);
        g.setNode(0, 0, 1);
        g.setNode(1, 2.5, 4.5);
        g.setNode(2, 4.5, 4.5);
        g.setNode(3, 3, 0.5);
        g.setNode(4, 2.8, 2.8);
        g.setNode(5, 4.2, 1.6);
        g.setNode(6, 2.3, 2.2);
        g.setNode(7, 5, 1.5);
        g.setNode(8, 4.5, 4);
        g.edge(8, 2, 0.5, true);
        g.edge(7, 3, 2.1, false);
        g.edge(1, 0, 3.9, true);
        g.edge(7, 5, 0.7, true);
        g.edge(1, 2, 1.9, true);
        g.edge(8, 1, 2.05, true);

        Graph newG = GraphUtility.sort(g, new RAMDirectory(), 16);
        assertEquals(g.getNodes(), newG.getNodes());
        assertEquals(0, newG.getLatitude(0), 1e-4); // 0
        assertEquals(2.3, newG.getLatitude(1), 1e-4); // 6
        assertEquals(2.5, newG.getLatitude(2), 1e-4); // 1
        assertEquals(3, newG.getLatitude(3), 1e-4); // 3
        assertEquals(5, newG.getLatitude(4), 1e-4); // 7
        assertEquals(4.2, newG.getLatitude(5), 1e-4); // 5
        assertEquals(2.8, newG.getLatitude(6), 1e-4); // 4
    }
}
