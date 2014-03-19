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
package com.graphhopper.routing.ch;

import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.EdgeSkipIterState;
import com.graphhopper.util.Helper;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests if a graph optimized by contraction hierarchies returns the same results as a none
 * optimized one. Additionally fine grained path unpacking is tested.
 * <p/>
 * @author Peter Karich
 */
public class DijkstraBidirectionCHTest extends AbstractRoutingAlgorithmTester
{
    // graph is expensive to create and to prepare!
    private static Graph preparedMatrixGraph;

    @Override
    public Graph getMatrixGraph()
    {
        if (preparedMatrixGraph == null)
        {
            LevelGraph lg = createGraph();
            getMatrixAlikeGraph().copyTo(lg);
            prepareGraph(lg);
            preparedMatrixGraph = lg;
        }
        return preparedMatrixGraph;
    }

    @Override
    protected LevelGraph createGraph()
    {
        return new GraphBuilder(encodingManager).levelGraphCreate();
    }

    @Override
    public PrepareContractionHierarchies prepareGraph( Graph g, FlagEncoder encoder, Weighting w )
    {
        PrepareContractionHierarchies ch = new PrepareContractionHierarchies(encoder, w).setGraph(g);
        // hack: prepare matrixgraph only once
        if (g != preparedMatrixGraph)
            ch.doWork();

        return ch;
    }

    @Test
    public void testPathRecursiveUnpacking()
    {
        LevelGraphStorage g2 = (LevelGraphStorage) createGraph();
        g2.edge(0, 1, 1, true);
        EdgeIteratorState iter1_1 = g2.edge(0, 2, 1.4, true);
        EdgeIteratorState iter1_2 = g2.edge(2, 5, 1.4, true);
        g2.edge(1, 2, 1, true);
        g2.edge(1, 3, 3, true);
        g2.edge(2, 3, 1, true);
        g2.edge(4, 3, 1, true);
        g2.edge(2, 5, 1.4, true);
        g2.edge(3, 5, 1, true);
        g2.edge(5, 6, 1, true);
        g2.edge(4, 6, 1, true);
        g2.edge(5, 7, 1.4, true);
        g2.edge(6, 7, 1, true);
        
        // simulate preparation
        EdgeIteratorState iter2_2 = g2.edge(5, 7);
        iter2_2.setDistance(1.4).setFlags(carEncoder.setProperties(0, true, true));
        EdgeSkipIterState iter2_1 = g2.shortcut(0, 5);
        iter2_1.setDistance(2.8).setFlags(carEncoder.setProperties(0, true, true));
        iter2_1.setSkippedEdges(iter1_1.getEdge(), iter1_2.getEdge());
        EdgeSkipIterState tmp = g2.shortcut(0, 7);
        tmp.setDistance(4.2).setFlags(carEncoder.setProperties(0, true, true));
        tmp.setSkippedEdges(iter2_1.getEdge(), iter2_2.getEdge());
        g2.setLevel(1, 0);
        g2.setLevel(3, 1);
        g2.setLevel(4, 2);
        g2.setLevel(6, 3);
        g2.setLevel(2, 4);
        g2.setLevel(5, 5);
        g2.setLevel(7, 6);
        g2.setLevel(0, 7);

        Path p = new PrepareContractionHierarchies(carEncoder, new ShortestWeighting()).setGraph(g2).createAlgo().calcPath(0, 7);
        assertEquals(Helper.createTList(0, 2, 5, 7), p.calcNodes());
        assertEquals(4, p.calcNodes().size());
        assertEquals(4.2, p.getDistance(), 1e-5);
    }

    @Override
    public void testCalcFootPath()
    {
        // disable car encoder and move foot to first position => workaround as CH does not allow multiple vehicles
        FlagEncoder tmpFootEncoder = footEncoder;
        FlagEncoder tmpCarEncoder = carEncoder;
        carEncoder = new CarFlagEncoder()
        {            
            @Override
            public long setProperties( double speed, boolean forward, boolean backward )
            {
                return 0;
            }                        
        };
        
        footEncoder = new EncodingManager("FOOT").getSingle();        
        super.testCalcFootPath();
        footEncoder = tmpFootEncoder;
        carEncoder = tmpCarEncoder;
    }
}
