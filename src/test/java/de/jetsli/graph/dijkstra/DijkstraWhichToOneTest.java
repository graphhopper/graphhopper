/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.dijkstra;

import de.jetsli.graph.storage.Graph;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraWhichToOneTest extends AbstractDijkstraTester {

    public static int[] pubTransportPath = new int[]{20, 21, 31, 41, 51, 52, 42, 43, 53, 63, 62, 72, 73, 74, 75};

    @Override public Dijkstra createDijkstra(Graph g) {
        return new DijkstraWhichToOne(g);
    }

    @Test public void testDirectlyOnPubTransport() {
        DijkstraWhichToOne d = new DijkstraWhichToOne(matrixGraph);
        d.addPubTransportPoints(pubTransportPath);
        int dest = 51;
        d.setDestination(dest);
        DijkstraPath path = d.calcShortestPath();

        assertWithBiDijkstra(pubTransportPath, path, dest);
    }

    @Test public void testABitAway() {
        DijkstraWhichToOne d = new DijkstraWhichToOne(matrixGraph);
        d.addPubTransportPoints(pubTransportPath);
        int dest = 49;
        d.setDestination(dest);
        DijkstraPath path = d.calcShortestPath();

        assertWithBiDijkstra(pubTransportPath, path, dest);
    }

    @Test public void testABitAway_DifferentPubTransport() {
        DijkstraWhichToOne d = new DijkstraWhichToOne(matrixGraph);
        int[] pubT = new int[]{20, 21, 22, 23, 24, 34, 33, 32, 31, 41, 51, 61, 62, 63, 64, 74, 73};
        d.addPubTransportPoints(pubT);
        int dest = 49;
        d.setDestination(dest);
        DijkstraPath path = d.calcShortestPath();

        assertWithBiDijkstra(pubT, path, dest);
    }

    private void assertWithBiDijkstra(int[] points, DijkstraPath path, int dest) {
        DijkstraPath bestManualPath = null;        
        for (int i = 0; i < points.length; i++) {
            DijkstraPath manualPath = new DijkstraBidirection(matrixGraph).calcShortestPath(points[i], dest);
            if (bestManualPath == null || manualPath.distance() < bestManualPath.distance())
                bestManualPath = manualPath;
        }

        assertEquals(bestManualPath.distance(), path.distance(), 1e-3);
        assertEquals(bestManualPath.locations(), path.locations());        
    }
}
