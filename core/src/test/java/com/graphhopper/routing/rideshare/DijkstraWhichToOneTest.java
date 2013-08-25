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
package com.graphhopper.routing.rideshare;

import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.Graph;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class DijkstraWhichToOneTest extends AbstractRoutingAlgorithmTester
{
    public static int[] pubTransportPath = new int[]
    {
        20, 21, 31, 41, 51, 52, 42, 43, 53, 63, 62, 72, 73, 74, 75
    };

    Graph getGraph()
    {
        return AbstractRoutingAlgorithmTester.getMatrixAlikeGraph();
    }

    @Override
    public AlgorithmPreparation prepareGraph( Graph g, final FlagEncoder encoder, final WeightCalculation calc)
    {
        return new NoOpAlgorithmPreparation()
        {
            @Override
            public RoutingAlgorithm createAlgo()
            {
                return new DijkstraWhichToOne(_graph, encoder, calc);
            }
        }.setGraph(g);
    }

    @Test
    public void testDirectlyOnPubTransport()
    {
        DijkstraWhichToOne d = new DijkstraWhichToOne(getGraph(), carEncoder, new ShortestCalc());
        d.addPubTransportPoints(pubTransportPath);
        int dest = 51;
        d.setDestination(dest);
        Path path = d.calcPath();

        assertWithBiDijkstra(pubTransportPath, path, dest);
    }

    @Test
    public void testABitAway()
    {
        DijkstraWhichToOne d = new DijkstraWhichToOne(getGraph(), carEncoder, new ShortestCalc());
        d.addPubTransportPoints(pubTransportPath);
        int dest = 49;
        d.setDestination(dest);
        Path path = d.calcPath();

        assertWithBiDijkstra(pubTransportPath, path, dest);
    }

    @Test
    public void testABitAway_DifferentPubTransport()
    {
        DijkstraWhichToOne d = new DijkstraWhichToOne(getGraph(), carEncoder, new ShortestCalc());
        int[] pubT = new int[]
        {
            20, 21, 22, 23, 24, 34, 33, 32, 31, 41, 51, 61, 62, 63, 64, 74, 73
        };
        d.addPubTransportPoints(pubT);
        int dest = 49;
        d.setDestination(dest);
        Path path = d.calcPath();

        assertWithBiDijkstra(pubT, path, dest);
    }

    private void assertWithBiDijkstra( int[] points, Path path, int dest )
    {
        Path bestManualPath = null;
        WeightCalculation type = new ShortestCalc();
        for (int i = 0; i < points.length; i++)
        {
            Path manualPath = new DijkstraBidirectionRef(getGraph(), carEncoder, type).calcPath(points[i], dest);
            if (bestManualPath == null || manualPath.getDistance() < bestManualPath.getDistance())
            {
                bestManualPath = manualPath;
            }
        }

        assertEquals(bestManualPath.getDistance(), path.getDistance(), 1e-3);
        assertEquals(bestManualPath.calcNodes(), path.calcNodes());
    }
}
