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
package com.graphhopper.routing.util;

import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.GHUtility;

import gnu.trove.list.array.TIntArrayList;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworksTest
{
    private final EncodingManager em = new EncodingManager("car");

    GraphStorage createGraph( EncodingManager eman )
    {
        return new GraphBuilder(eman).create();
    }

    GraphStorage createSubnetworkTestGraph()
    {
        GraphStorage g = createGraph(em);
        // big network
        g.edge(1, 2, 1, true);
        g.edge(1, 4, 1, false);
        g.edge(1, 8, 1, true);
        g.edge(2, 4, 1, true);
        g.edge(8, 4, 1, false);
        g.edge(8, 11, 1, true);
        g.edge(12, 11, 1, true);
        g.edge(9, 12, 1, false);
        g.edge(9, 15, 1, true);

        // large network
        g.edge(0, 13, 1, true);
        g.edge(0, 3, 1, true);
        g.edge(0, 7, 1, true);
        g.edge(3, 7, 1, true);
        g.edge(3, 5, 1, true);
        g.edge(13, 5, 1, true);

        // small network
        g.edge(6, 14, 1, true);
        g.edge(10, 14, 1, true);
        return g;
    }

    @Test
    public void testFindSubnetworks()
    {
        GraphStorage g = createSubnetworkTestGraph();
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em);
        Map<Integer, Integer> map = instance.findSubnetworks();

        assertEquals(3, map.size());
        // start is at 0 => large network
        assertEquals(5, (int) map.get(0));
        // next smallest and unvisited node is 1 => big network
        assertEquals(8, (int) map.get(1));
        assertEquals(3, (int) map.get(6));
    }

    @Test
    public void testKeepLargestNetworks()
    {
        GraphStorage g = createSubnetworkTestGraph();
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em);
        Map<Integer, Integer> map = instance.findSubnetworks();
        instance.keepLargeNetworks(map);
        g.optimize();

        assertEquals(8, g.getNodes());
        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(g));
        map = instance.findSubnetworks();
        assertEquals(1, map.size());
        assertEquals(8, (int) map.get(0));
    }

    GraphStorage createSubnetworkTestGraph2( EncodingManager em )
    {
        GraphStorage g = createGraph(em);
        // large network
        g.edge(0, 1, 1, true);
        g.edge(1, 3, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 7, 1, true);
        g.edge(7, 8, 1, true);

        // connecting both but do not allow CAR!
        g.edge(3, 4).setDistance(1);

        // small network
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(4, 6, 1, true);
        return g;
    }

    @Test
    public void testRemoveSubnetworkIfOnlyOneVehicle()
    {
        GraphStorage g = createSubnetworkTestGraph2(em);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em);
        instance.setMinNetworkSize(4);
        instance.doWork();
        g.optimize();
        assertEquals(6, g.getNodes());
        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(g));
        EdgeExplorer explorer = g.createEdgeExplorer();
        assertEquals(GHUtility.asSet(2, 1, 5), GHUtility.getNeighbors(explorer.setBaseNode(3)));

        // do not remove because small network is big enough
        g = createSubnetworkTestGraph2(em);
        instance = new PrepareRoutingSubnetworks(g, em);
        instance.setMinNetworkSize(3);
        instance.doWork();
        g.optimize();
        assertEquals(9, g.getNodes());

        // do not remove because two two vehicles
        EncodingManager em2 = new EncodingManager("CAR,BIKE");
        g = createSubnetworkTestGraph2(em2);
        instance = new PrepareRoutingSubnetworks(g, em2);
        instance.setMinNetworkSize(3);
        instance.doWork();
        g.optimize();
        assertEquals(9, g.getNodes());
    }

    GraphStorage createDeadEndUnvisitedNetworkGraph( EncodingManager em )
    {
        GraphStorage g = createGraph(em);
        // 0 <-> 1 <-> 2 <-> 3 <-> 4 <- 5 <-> 6
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(5, 4, 1, false);
        g.edge(5, 6, 1, true);

        // 7 -> 8 <-> 9 <-> 10
        g.edge(7, 8, 1, false);
        g.edge(8, 9, 1, true);
        g.edge(9, 10, 1, true);

        return g;
    }

    GraphStorage createTarjanTestGraph()
    {
        GraphStorage g = createGraph(em);

        g.edge(1, 2, 1, false);
        g.edge(2, 3, 1, false);
        g.edge(3, 1, 1, false);

        g.edge(4, 2, 1, false);
        g.edge(4, 3, 1, false);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, false);

        g.edge(6, 3, 1, false);
        g.edge(6, 7, 1, true);

        g.edge(8, 5, 1, false);
        g.edge(8, 7, 1, false);
        g.edge(8, 8, 1, false);

        return g;
    }

    @Test
    public void testRemoveDeadEndUnvisitedNetworks()
    {
        GraphStorage g = createDeadEndUnvisitedNetworkGraph(em);
        assertEquals(11, g.getNodes());

        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em).
                setMinOneWayNetworkSize(3);
        int removed = instance.removeDeadEndUnvisitedNetworks(em.getEncoder("car"));

        assertEquals(3, removed);

        g.optimize();
        assertEquals(8, g.getNodes());
    }

    @Test
    public void testTarjan()
    {
        GraphStorage g = createSubnetworkTestGraph();

        // Requires a single vehicle type, otherwise we throw.
        final FlagEncoder flagEncoder = em.getEncoder("car");
        final EdgeFilter filter = new DefaultEdgeFilter(flagEncoder, false, true);

        TarjansStronglyConnectedComponentsAlgorithm tarjan = new TarjansStronglyConnectedComponentsAlgorithm(g, filter);

        List<TIntArrayList> components = tarjan.findComponents();

        assertEquals(4, components.size());
        assertEquals(new TIntArrayList(new int[]
        {
            13, 5, 3, 7, 0
        }), components.get(0));
        assertEquals(new TIntArrayList(new int[]
        {
            2, 4, 12, 11, 8, 1
        }), components.get(1));
        assertEquals(new TIntArrayList(new int[]
        {
            10, 14, 6
        }), components.get(2));
        assertEquals(new TIntArrayList(new int[]
        {
            15, 9
        }), components.get(3));
    }

    // Previous two-pass implementation failed on 1 -> 2 -> 0
    @Test
    public void testNodeOrderingRegression()
    {
        // 1 -> 2 -> 0
        GraphStorage g = createGraph(em);
        g.edge(1, 2, 1, false);
        g.edge(2, 0, 1, false);
        PrepareRoutingSubnetworks instance = new PrepareRoutingSubnetworks(g, em).
                setMinOneWayNetworkSize(2);
        int removed = instance.removeDeadEndUnvisitedNetworks(em.getEncoder("car"));

        assertEquals(3, removed);
    }
}
