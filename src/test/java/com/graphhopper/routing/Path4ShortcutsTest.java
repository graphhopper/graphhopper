/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.routing.util.ShortestCarCalc;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.Helper;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class Path4ShortcutsTest {

    LevelGraphStorage createGraph() {
        return new GraphBuilder().levelGraphCreate();
    }

    @Test
    public void testNoExpand() {
        LevelGraph g = createGraph();
        int e1 = g.edge(0, 1, 10, true).edge();
        int e2 = g.edge(1, 2, 10, true).edge();
        int e3 = g.edge(2, 3, 10, true).edge();
        int e4 = g.edge(3, 4, 10, true).edge();

        Path4Shortcuts path = new Path4Shortcuts(g, ShortestCarCalc.DEFAULT);
        path.edgeEntry = new EdgeEntry(e3, 3, 10);
        path.edgeEntry.parent = new EdgeEntry(e2, 2, 10);
        path.edgeEntry.parent.parent = new EdgeEntry(e1, 1, 10);
        path.edgeEntry.parent.parent.parent = new EdgeEntry(EdgeIterator.NO_EDGE, 0, 0);
        path.edgeTo = new EdgeEntry(e4, 3, 10);
        path.edgeTo.parent = new EdgeEntry(EdgeIterator.NO_EDGE, 4, 0);
        Path p = path.extract();
        assertEquals(5, p.calcNodes().size());
        assertEquals(Helper.createTList(0, 1, 2, 3, 4), p.calcNodes());
    }

    @Test
    public void testExpand() {
        LevelGraphStorage g = createGraph();
        EdgeSkipIterator iter = g.edge(0, 1, 10, true); // 1
        g.edge(1, 2, 10, true); // 2
        EdgeSkipIterator iter3 = g.edge(2, 3, 10, true); // 3

        g.setLevel(1, -1);
        g.setLevel(2, -1);
        EdgeSkipIterator iter4 = g.edge(0, 2, 20, CarStreetType.flagsDefault(true));
        iter4.skippedEdge(iter.edge());// 4

        Path4Shortcuts path = new Path4Shortcuts(g, ShortestCarCalc.DEFAULT);
        path.edgeEntry = new EdgeEntry(iter4.edge(), 2, 20);
        path.edgeEntry.parent = new EdgeEntry(EdgeIterator.NO_EDGE, 0, 0);
        path.edgeTo = new EdgeEntry(iter3.edge(), 2, 10);
        path.edgeTo.parent = new EdgeEntry(EdgeIterator.NO_EDGE, 3, 0);
        Path p = path.extract();
        assertEquals(Helper.createTList(0, 1, 2, 3), p.calcNodes());
        assertEquals(4, p.calcNodes().size());
    }

    @Test
    public void testExpandMultipleSkippedNodes() {
        LevelGraphStorage g = createGraph();
        int e1 = g.edge(0, 1, 10, true).edge();
        int e2 = g.edge(1, 2, 10, true).edge();
        int e3 = g.edge(2, 3, 10, true).edge();
        int e4 = g.edge(3, 4, 10, true).edge();

        g.setLevel(1, -1);
        g.setLevel(2, -1);
        EdgeSkipIterator iter = g.edge(0, 3, 30, CarStreetType.flagsDefault(true));
        iter.skippedEdge(e1);
        int e5 = iter.edge();

        Path4Shortcuts path = new Path4Shortcuts(g, ShortestCarCalc.DEFAULT);
        path.edgeEntry = new EdgeEntry(e5, 3, 30);
        path.edgeEntry.parent = new EdgeEntry(EdgeIterator.NO_EDGE, 0, 0);
        path.edgeTo = new EdgeEntry(e4, 3, 10);
        path.edgeTo.parent = new EdgeEntry(EdgeIterator.NO_EDGE, 4, 0);
        Path p = path.extract();
        assertEquals(Helper.createTList(0, 1, 2, 3, 4), p.calcNodes());
    }
}
