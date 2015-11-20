/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.routing;

import com.graphhopper.routing.util.*;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author Peter Karich
 */
public class PathBidirRefTest
{
    private final EncodingManager encodingManager = new EncodingManager("CAR");
    private FlagEncoder carEncoder = encodingManager.getEncoder("CAR");
    private EdgeFilter carOutEdges = new DefaultEdgeFilter(carEncoder, false, true);

    Graph createGraph()
    {
        return new GraphBuilder(encodingManager).create();
    }

    @Test
    public void testExtract()
    {
        Graph g = createGraph();
        g.edge(1, 2, 10, true);
        PathBidirRef pw = new PathBidirRef(g, carEncoder);
        EdgeExplorer explorer = g.createEdgeExplorer(carOutEdges);
        EdgeIterator iter = explorer.setBaseNode(1);
        iter.next();
        pw.edgeEntry = new EdgeEntry(iter.getEdge(), 2, 0);
        pw.edgeEntry.parent = new EdgeEntry(EdgeIterator.NO_EDGE, 1, 10);
        pw.edgeTo = new EdgeEntry(EdgeIterator.NO_EDGE, 2, 0);
        Path p = pw.extract();
        assertEquals(Helper.createTList(1, 2), p.calcNodes());
        assertEquals(10, p.getDistance(), 1e-4);
    }

    @Test
    public void testExtract2()
    {
        Graph g = createGraph();
        g.edge(1, 2, 10, false);
        g.edge(2, 3, 20, false);
        EdgeExplorer explorer = g.createEdgeExplorer(carOutEdges);
        EdgeIterator iter = explorer.setBaseNode(1);
        iter.next();
        PathBidirRef pw = new PathBidirRef(g, carEncoder);
        pw.edgeEntry = new EdgeEntry(iter.getEdge(), 2, 10);
        pw.edgeEntry.parent = new EdgeEntry(EdgeIterator.NO_EDGE, 1, 0);

        explorer = g.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, false));
        iter = explorer.setBaseNode(3);
        iter.next();
        pw.edgeTo = new EdgeEntry(iter.getEdge(), 2, 20);
        pw.edgeTo.parent = new EdgeEntry(EdgeIterator.NO_EDGE, 3, 0);
        Path p = pw.extract();
        assertEquals(Helper.createTList(1, 2, 3), p.calcNodes());
        assertEquals(30, p.getDistance(), 1e-4);
    }
}
