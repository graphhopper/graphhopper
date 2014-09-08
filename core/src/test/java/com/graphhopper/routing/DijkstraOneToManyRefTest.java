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
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *
 * @author Peter Karich
 */
@RunWith(Parameterized.class)
public class DijkstraOneToManyRefTest extends DijkstraOneToManyTest
{
    /**
     * Runs the same test with each of the supported traversal modes
     */
    @Parameterized.Parameters
    public static Collection<Object[]> configs()
    {
        return Arrays.asList(new Object[][]
        {
            { TraversalMode.NODE_BASED },
            { TraversalMode.EDGE_BASED_1DIR },
            { TraversalMode.EDGE_BASED_2DIR },
            { TraversalMode.EDGE_BASED_2DIR_UTURN }
        });
    }

    
    public DijkstraOneToManyRefTest( TraversalMode tMode )
    {
        super(tMode);
    }

    @Override
    public AlgorithmPreparation prepareGraph( Graph defaultGraph, final FlagEncoder encoder, final Weighting w )
    {
        return new NoOpAlgorithmPreparation()
        {
            @Override
            public RoutingAlgorithm createAlgo()
            {
                return new DijkstraOneToManyRef(_graph, encoder, w, traversalmode);
            }
        }.setGraph(defaultGraph);
    }
    
    
    
    @Override
    @Test
    public void testIssue239()
    {
        Graph g = createGraph(false);
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 0, 1, true);

        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 4, 1, true);

        AlgorithmPreparation prep = prepareGraph(g);
        DijkstraOneToManyRef algo = (DijkstraOneToManyRef) prep.createAlgo();
        assertFalse(algo.calcPath(0, 4).isFound());
        assertFalse(algo.calcPath(0, 4).isFound());
    }
    
    @Override
    @Test
    public void testDifferentEdgeFilter()
    {
        Graph g = new GraphBuilder(encodingManager).levelGraphCreate();
        g.edge(4, 3, 10, true);
        g.edge(3, 6, 10, true);

        g.edge(4, 5, 10, true);
        g.edge(5, 6, 10, true);

        AlgorithmPreparation prep = prepareGraph(g);
        DijkstraOneToManyRef algo = (DijkstraOneToManyRef) prep.createAlgo();
        algo.setEdgeFilter(new EdgeFilter()
        {
            @Override
            public boolean accept( EdgeIteratorState iter )
            {
                return iter.getAdjNode() != 5;
            }
        });
        Path p = algo.calcPath(4, 6);
        assertEquals(Helper.createTList(4, 3, 6), p.calcNodes());

        // important call!
        algo.clear();
        algo.setEdgeFilter(new EdgeFilter()
        {
            @Override
            public boolean accept( EdgeIteratorState iter )
            {
                return iter.getAdjNode() != 3;
            }
        });
        p = algo.calcPath(4, 6);
        assertEquals(Helper.createTList(4, 5, 6), p.calcNodes());
    }

  
}
