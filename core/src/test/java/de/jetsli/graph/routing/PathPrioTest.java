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

import de.jetsli.graph.routing.util.CarStreetType;
import de.jetsli.graph.routing.util.ShortestCalc;
import de.jetsli.graph.storage.EdgeEntry;
import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.storage.PriorityGraphImpl;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class PathPrioTest {

    @Test
    public void testNoExpand() {
        PriorityGraph g = new PriorityGraphImpl(20);
        g.edge(0, 1, 10, true);
        g.edge(1, 2, 10, true);
        g.edge(2, 3, 10, true);
        g.edge(3, 4, 10, true);

        PathPrio path = new PathPrio(g, ShortestCalc.DEFAULT);
        path.edgeFrom = new EdgeEntry(3, 10);
        path.edgeFrom.prevEntry = new EdgeEntry(2, 10);
        path.edgeFrom.prevEntry.prevEntry = new EdgeEntry(1, 10);
        path.edgeFrom.prevEntry.prevEntry.prevEntry = new EdgeEntry(0, 0);
        path.edgeTo = new EdgeEntry(3, 10);
        path.edgeTo.prevEntry = new EdgeEntry(4, 0);
        Path p = path.extract();
        assertEquals(5, p.locations());
    }

    @Test
    public void testExpand() {
        PriorityGraph g = new PriorityGraphImpl(20);
        g.edge(0, 1, 10, true);
        g.edge(1, 2, 10, true);
        g.edge(2, 3, 10, true);

        g.setPriority(1, -1);
        g.setPriority(2, -1);
        g.shortcut(0, 2, 20, CarStreetType.flagsDefault(true), 1);

        PathPrio path = new PathPrio(g, ShortestCalc.DEFAULT);
        path.edgeFrom = new EdgeEntry(2, 20);
        path.edgeFrom.prevEntry = new EdgeEntry(0, 0);
        path.edgeTo = new EdgeEntry(2, 10);
        path.edgeTo.prevEntry = new EdgeEntry(3, 0);
        Path p = path.extract();
        assertEquals(4, p.locations());
    }

    @Test
    public void testExpandMultipleSkippedNodes() {
        PriorityGraph g = new PriorityGraphImpl(20);
        g.edge(0, 1, 10, true);
        g.edge(1, 2, 10, true);
        g.edge(2, 3, 10, true);
        g.edge(3, 4, 10, true);

        g.setPriority(1, -1);
        g.setPriority(2, -1);
        g.shortcut(0, 3, 30, CarStreetType.flagsDefault(true), 1);

        PathPrio path = new PathPrio(g, ShortestCalc.DEFAULT);
        path.edgeFrom = new EdgeEntry(3, 30);
        path.edgeFrom.prevEntry = new EdgeEntry(0, 0);
        path.edgeTo = new EdgeEntry(3, 10);
        path.edgeTo.prevEntry = new EdgeEntry(4, 0);
        Path p = path.extract();
        assertEquals(5, p.locations());
    }
}
