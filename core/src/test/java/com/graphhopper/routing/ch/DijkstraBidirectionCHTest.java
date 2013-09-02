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
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.util.EdgeSkipExplorer;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.Helper;
import java.io.IOException;
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
    public PrepareContractionHierarchies prepareGraph( Graph g, FlagEncoder encoder, WeightCalculation calc)
    {
        PrepareContractionHierarchies ch = new PrepareContractionHierarchies(encoder, calc).setGraph(g);
        // hack: prepare matrixgraph only once
        if (g != preparedMatrixGraph)
        {
            ch.doWork();
        }
        return ch;
    }

    @Test
    public void testPathRecursiveUnpacking()
    {
        LevelGraphStorage g2 = (LevelGraphStorage) createGraph();
        g2.edge(0, 1, 1, true);
        EdgeSkipExplorer iter1_1 = g2.edge(0, 2, 1.4, true);
        EdgeSkipExplorer iter1_2 = g2.edge(2, 5, 1.4, true);
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
        EdgeSkipExplorer iter2_1 = g2.edge(0, 5, 2.8, carEncoder.flags(0, true));
        iter2_1.setSkippedEdges(iter1_1.getEdge(), iter1_2.getEdge());
        EdgeSkipExplorer iter2_2 = g2.edge(5, 7, 1.4, carEncoder.flags(0, true));
        g2.edge(0, 7, 4.2, carEncoder.flags(0, true)).setSkippedEdges(iter2_1.getEdge(), iter2_2.getEdge());
        g2.setLevel(1, 0);
        g2.setLevel(3, 1);
        g2.setLevel(4, 2);
        g2.setLevel(6, 3);
        g2.setLevel(2, 4);
        g2.setLevel(5, 5);
        g2.setLevel(7, 6);
        g2.setLevel(0, 7);

        Path p = new PrepareContractionHierarchies(carEncoder, new ShortestCalc()).setGraph(g2).createAlgo().calcPath(0, 7);
        assertEquals(Helper.createTList(0, 2, 5, 7), p.calcNodes());
        assertEquals(4, p.calcNodes().size());
        assertEquals(4.2, p.getDistance(), 1e-5);
    }
}
