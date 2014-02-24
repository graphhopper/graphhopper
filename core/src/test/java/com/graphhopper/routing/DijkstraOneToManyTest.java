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

import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class DijkstraOneToManyTest extends AbstractRoutingAlgorithmTester
{
    @Override
    public AlgorithmPreparation prepareGraph( Graph defaultGraph, final FlagEncoder encoder, final Weighting w )
    {
        return new NoOpAlgorithmPreparation()
        {
            @Override
            public RoutingAlgorithm createAlgo()
            {
                return new DijkstraOneToMany(_graph, encoder, w);
            }
        }.setGraph(defaultGraph);
    }

    @Override
    public void testViaEdges_BiGraph()
    {
        // not supported
    }

    @Override
    public void testViaEdges_SpecialCases()
    {
        // not supported
    }

    @Override
    public void testViaEdges_FromEqualsTo()
    {
        // not supported
    }

    @Override
    public void testViaEdges_WithCoordinates()
    {
        // not supported
    }

    @Override
    public void testQueryGraphAndFastest()
    {
        // not supported
    }

    @Override
    public void testTwoWeightsPerEdge()
    {
        // not supported
    }
    
    @Test
    public void testUseCache()
    {
        AlgorithmPreparation prep = prepareGraph(createTestGraph());
        RoutingAlgorithm algo = prep.createAlgo();
        Path p = algo.calcPath(0, 4);
        assertEquals(Helper.createTList(0, 4), p.calcNodes());

        // expand SPT
        p = algo.calcPath(0, 7);
        assertEquals(Helper.createTList(0, 4, 6, 5, 7), p.calcNodes());

        // use SPT
        p = algo.calcPath(0, 2);
        assertEquals(Helper.createTList(0, 1, 2), p.calcNodes());
    }

    @Test
    public void testDifferentEdgeFilter()
    {
        Graph g = new GraphBuilder(encodingManager).levelGraphCreate();
        g.edge(4, 3, 10, true);
        g.edge(3, 6, 10, true);

        g.edge(4, 5, 10, true);
        g.edge(5, 6, 10, true);

        AlgorithmPreparation prep = prepareGraph(g);
        DijkstraOneToMany algo = (DijkstraOneToMany) prep.createAlgo();
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
