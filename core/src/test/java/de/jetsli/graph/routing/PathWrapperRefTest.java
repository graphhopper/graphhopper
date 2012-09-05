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

import de.jetsli.graph.storage.EdgeEntry;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.MemoryGraphSafe;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Peter Karich
 */
public class PathWrapperRefTest {

    @Test
    public void testExtract() {
        Graph g = new MemoryGraphSafe(10);
        g.edge(1, 2, 10, true);
        PathWrapperRef pw = new PathWrapperRef(g);
        pw.edgeFrom = new EdgeEntry(2, 10);
        pw.edgeFrom.prevEntry = new EdgeEntry(1, 10);
        pw.edgeTo = new EdgeEntry(2, 20);
        Path p = pw.extract(new Path());
        assertEquals(2, p.locations());
        assertEquals(10, p.distance(), 1e-4);
    }

    @Test
    public void testExtract2() {
        Graph g = new MemoryGraphSafe(10);
        g.edge(1, 2, 10, true);
        g.edge(2, 3, 20, true);
        PathWrapperRef pw = new PathWrapperRef(g);
        pw.edgeFrom = new EdgeEntry(2, 10);
        pw.edgeFrom.prevEntry = new EdgeEntry(1, 0);
        pw.edgeTo = new EdgeEntry(2, 20);
        pw.edgeTo.prevEntry = new EdgeEntry(3, 0);
        Path p = pw.extract(new Path());
        assertEquals(1, p.location(0));
        assertEquals(3, p.location(2));
        assertEquals(3, p.locations());
        assertEquals(30, p.distance(), 1e-4);
    }
}
