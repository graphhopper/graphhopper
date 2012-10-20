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

import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;
import java.util.Arrays;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Peter Karich
 */
public class PathBidirRefTest {

    Graph createGraph() {
        return new GraphStorage(new RAMDirectory()).createNew(10);
    }

    @Test
    public void testExtract() {
        Graph g = createGraph();
        g.edge(1, 2, 10, true);
        PathBidirRef pw = new PathBidirRef(g, ShortestCalc.DEFAULT);
        EdgeIterator iter = g.getOutgoing(1);
        iter.next();
        pw.edgeFrom = new EdgeEntry(iter.edge(), 2, 0);
        pw.edgeFrom.parent = new EdgeEntry(-1, 1, 10);
        pw.edgeTo = new EdgeEntry(-1, 2, 0);
        Path p = pw.extract();
        assertEquals(Arrays.asList(1, 2), p.toNodeList());
        assertEquals(10, p.weight(), 1e-4);
    }

    @Test
    public void testExtract2() {
        Graph g = createGraph();
        g.edge(1, 2, 10, false);
        g.edge(2, 3, 20, false);
        EdgeIterator iter = g.getOutgoing(1);
        iter.next();
        PathBidirRef pw = new PathBidirRef(g, ShortestCalc.DEFAULT);
        pw.edgeFrom = new EdgeEntry(iter.edge(), 2, 10);
        pw.edgeFrom.parent = new EdgeEntry(-1, 1, 0);

        iter = g.getIncoming(3);
        iter.next();
        pw.edgeTo = new EdgeEntry(iter.edge(), 2, 20);
        pw.edgeTo.parent = new EdgeEntry(-1, 3, 0);
        Path p = pw.extract();
        assertEquals(Arrays.asList(1, 2, 3), p.toNodeList());
        assertEquals(30, p.weight(), 1e-4);
    }
}
