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

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIDResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.CoordTrig;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class QueryGraphTest
{
    LocationIDResult match;

    void initGraph( Graph g )
    {
        //
        //  /*-*\
        // 0     1
        // |
        // 2
        g.setNode(0, 1, 0);
        g.setNode(1, 1, 2.5);
        g.setNode(2, 0, 0);
        g.edge(0, 2, 10, true);
        g.edge(0, 1, 10, true).setWayGeometry(Helper.createPointList(1.5, 1, 1.5, 1.5));
    }

    @Test
    public void testOneVirtualNode()
    {
        EncodingManager encodingManager = new EncodingManager("CAR");
        Graph g = new GraphStorage(new RAMDirectory(), encodingManager).create(100);
        initGraph(g);
        EdgeExplorer expl = g.createEdgeExplorer();

        // snap directly to tower node => pointList could get of size 1?!?      
        // a)
        EdgeIterator iter = expl.setBaseNode(2);
        iter.next();

        QueryGraph queryGraph = new QueryGraph(null, g, EdgeFilter.ALL_EDGES);
        match = createLocationResult(1, -1, iter, 0, true);
        queryGraph.lookup(match);
        assertEquals(2, match.getClosestNode());
        assertEquals(new CoordTrig(0, 0), match.getSnappedPoint());

        // b)
        match = createLocationResult(1, -1, iter, 1, true);
        queryGraph.lookup(match);
        assertEquals(0, match.getClosestNode());
        assertEquals(new CoordTrig(1, 0), match.getSnappedPoint());
        // c)
        iter = expl.setBaseNode(1);
        iter.next();
        match = createLocationResult(1.2, 2.7, iter, 0, true);
        queryGraph.lookup(match);
        assertEquals(1, match.getClosestNode());
        assertEquals(new CoordTrig(1, 2.5), match.getSnappedPoint());

        // node number stays
        assertEquals(3, queryGraph.getNodes());

        // snap directly to pillar node
        queryGraph = new QueryGraph(null, g, EdgeFilter.ALL_EDGES);
        iter = expl.setBaseNode(1);
        iter.next();
        match = createLocationResult(2, 1.5, iter, 1, false);
        queryGraph.lookup(match);
        assertEquals(new CoordTrig(1.5, 1.5), match.getSnappedPoint());
        assertEquals(3, match.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());

        queryGraph = new QueryGraph(null, g, EdgeFilter.ALL_EDGES);
        match = createLocationResult(2, 1.7, iter, 1, false);
        queryGraph.lookup(match);
        assertEquals(new CoordTrig(1.5, 1.5), match.getSnappedPoint());
        assertEquals(3, match.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());

        // snap to edge which has pillar nodes        
        queryGraph = new QueryGraph(null, g, EdgeFilter.ALL_EDGES);
        match = createLocationResult(1.5, 2, iter, 0, false);
        queryGraph.lookup(match);
        assertEquals(new CoordTrig(1.3, 1.9), match.getSnappedPoint());
        assertEquals(3, match.getClosestNode());
        assertEquals(4, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());

        // snap to edge which has no pillar nodes
        queryGraph = new QueryGraph(null, g, EdgeFilter.ALL_EDGES);
        iter = expl.setBaseNode(2);
        iter.next();
        match = createLocationResult(0.5, 0.1, iter, 0, false);
        queryGraph.lookup(match);
        assertEquals(new CoordTrig(0.5, 0), match.getSnappedPoint());
        assertEquals(3, match.getClosestNode());
        assertEquals(2, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 2).getSize());
    }

    @Test
    public void testMultipleVirtualNodes()
    {
        EncodingManager encodingManager = new EncodingManager("CAR");
        Graph g = new GraphStorage(new RAMDirectory(), encodingManager).create(100);
        initGraph(g);

        // snap to edge which has pillar nodes
        QueryGraph queryGraph = new QueryGraph(null, g, EdgeFilter.ALL_EDGES);
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(1);
        iter.next();
        match = createLocationResult(2, 1.7, iter, 1, false);
        queryGraph.lookup(match);
        assertEquals(new CoordTrig(1.5, 1.5), match.getSnappedPoint());
        assertEquals(3, match.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());

        // snap again => new virtual node on same edge!
        iter = queryGraph.createEdgeExplorer().setBaseNode(1);
        iter.next();
        match = createLocationResult(1.5, 2, iter, 0, false);
        queryGraph.lookup(match);
        assertEquals(new CoordTrig(1.3, 1.9), match.getSnappedPoint());
        assertEquals(4, match.getClosestNode());
        assertEquals(2, getPoints(queryGraph, 3, 4).getSize());
        assertEquals(2, getPoints(queryGraph, 4, 1).getSize());
        assertNull(GHUtility.getEdge(queryGraph, 3, 1));
    }

    PointList getPoints( Graph g, int base, int adj )
    {
        return GHUtility.getEdge(g, base, adj).fetchWayGeometry(3);
    }

    public LocationIDResult createLocationResult( double lat, double lon,
            EdgeIteratorState iter, int index, boolean onTowerNode )
    {
        LocationIDResult tmp = new LocationIDResult(lat, lon);
        tmp.setClosestEdge(iter);
        tmp.setWayIndex(index);
        tmp.setOnTowerNode(onTowerNode);

        if (tmp.isOnTowerNode())
        {
            if (tmp.getWayIndex() == 0)
                tmp.setClosestNode(tmp.getClosestEdge().getBaseNode());
            else
                tmp.setClosestNode(tmp.getClosestEdge().getAdjNode());
        }

        return tmp;
    }
}
