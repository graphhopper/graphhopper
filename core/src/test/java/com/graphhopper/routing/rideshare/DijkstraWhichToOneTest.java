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
package com.graphhopper.routing.rideshare;

import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.VehicleEncoder;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class DijkstraWhichToOneTest extends AbstractRoutingAlgorithmTester {

    public static int[] pubTransportPath = new int[]{20, 21, 31, 41, 51, 52, 42, 43, 53, 63, 62, 72, 73, 74, 75};

    Graph getGraph() {
        return AbstractRoutingAlgorithmTester.getMatrixAlikeGraph();
    }

    @Override
    public AlgorithmPreparation prepareGraph(Graph g, final WeightCalculation calc, final VehicleEncoder encoder) {
        return new NoOpAlgorithmPreparation() {
            @Override public RoutingAlgorithm createAlgo() {
                return new DijkstraWhichToOne(_graph, encoder).type(calc);
            }
        }.graph(g);
    }

    @Test public void testDirectlyOnPubTransport() {
        DijkstraWhichToOne d = new DijkstraWhichToOne(getGraph(), carEncoder);
        d.addPubTransportPoints(pubTransportPath);
        int dest = 51;
        d.setDestination(dest);
        Path path = d.calcPath();

        assertWithBiDijkstra(pubTransportPath, path, dest);
    }

    @Test public void testABitAway() {
        DijkstraWhichToOne d = new DijkstraWhichToOne(getGraph(), carEncoder);
        d.addPubTransportPoints(pubTransportPath);
        int dest = 49;
        d.setDestination(dest);
        Path path = d.calcPath();

        assertWithBiDijkstra(pubTransportPath, path, dest);
    }

    @Test public void testABitAway_DifferentPubTransport() {
        DijkstraWhichToOne d = new DijkstraWhichToOne(getGraph(), carEncoder);
        int[] pubT = new int[]{20, 21, 22, 23, 24, 34, 33, 32, 31, 41, 51, 61, 62, 63, 64, 74, 73};
        d.addPubTransportPoints(pubT);
        int dest = 49;
        d.setDestination(dest);
        Path path = d.calcPath();

        assertWithBiDijkstra(pubT, path, dest);
    }

    private void assertWithBiDijkstra(int[] points, Path path, int dest) {
        Path bestManualPath = null;
        for (int i = 0; i < points.length; i++) {
            Path manualPath = new DijkstraBidirectionRef(getGraph(), carEncoder).calcPath(points[i], dest);
            if (bestManualPath == null || manualPath.distance() < bestManualPath.distance())
                bestManualPath = manualPath;
        }

        assertEquals(bestManualPath.distance(), path.distance(), 1e-3);
        assertEquals(bestManualPath.calcNodes(), path.calcNodes());
    }
}
