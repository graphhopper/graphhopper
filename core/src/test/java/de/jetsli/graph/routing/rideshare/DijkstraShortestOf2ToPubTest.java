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
package de.jetsli.graph.routing.rideshare;

import de.jetsli.graph.routing.DijkstraBidirection;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.routing.RoutingAlgorithm;
import de.jetsli.graph.storage.Graph;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraShortestOf2ToPubTest {

    public RoutingAlgorithm createDijkstra(Graph g) {
        return new DijkstraWhichToOne(g);
    }

    @Test
    public void testCalcShortestPath() {
        Graph g = DijkstraWhichToOneTest.matrixGraph.clone();
        DijkstraShortestOf2ToPub d = new DijkstraShortestOf2ToPub(g);
        d.addPubTransportPoints(DijkstraWhichToOneTest.pubTransportPath);
        int from = 13;
        int dest = 65;
        d.setFrom(from);
        d.setTo(dest);
        Path path = d.calcShortestPath();
        assertWithBiDijkstra(DijkstraWhichToOneTest.pubTransportPath, path, from, dest, g);
    }

    @Test
    public void testCalcShortestPath2() {
        Graph g = DijkstraWhichToOneTest.matrixGraph.clone();
        DijkstraShortestOf2ToPub d = new DijkstraShortestOf2ToPub(g);
        d.addPubTransportPoints(DijkstraWhichToOneTest.pubTransportPath);
        int from = 13;
        int dest = 70;
        d.setFrom(from);
        d.setTo(dest);

        Path path = d.calcShortestPath();
        assertWithBiDijkstra(DijkstraWhichToOneTest.pubTransportPath, path, from, dest, g);
    }
    
    @Test
    public void testCalculateShortestPathWithSpecialFinishCondition() {
        int[] pubTransport = new int[]{20, 21, 31, 41, 51, 52, 62, 72};
        Graph g = DijkstraWhichToOneTest.matrixGraph.clone();
        g.edge(21, 31, 100, true);
        g.edge(31, 41, 100, true);
        g.edge(41, 51, 100, true);
        g.edge(51, 52, 100, true);
        DijkstraShortestOf2ToPub d = new DijkstraShortestOf2ToPub(g);
        d.addPubTransportPoints(pubTransport);
        int from = 1;
        int dest = 53;
        d.setFrom(from);
        d.setTo(dest);
        Path path = d.calcShortestPath();
        
        assertWithBiDijkstra(pubTransport, path, from, dest, g);
    }

    private void assertWithBiDijkstra(int[] points, Path path, int from, int to, Graph g) {
        Path bestManualPathFrom = null;
        Path bestManualPathTo = null;        
        for (int i = 0; i < points.length; i++) {
            Path manualFrom = new DijkstraBidirection(g).calcShortestPath(points[i], from);
            Path manualTo = new DijkstraBidirection(g).calcShortestPath(points[i], to);
            if (bestManualPathFrom == null
                    || manualFrom.distance() + manualTo.distance()
                    < bestManualPathFrom.distance() + bestManualPathTo.distance()) {
                bestManualPathFrom = manualFrom;
                bestManualPathTo = manualTo;
            }
        }

        assertEquals(bestManualPathFrom.locations() + bestManualPathTo.locations() - 1, path.locations());
        assertEquals(bestManualPathFrom.distance() + bestManualPathTo.distance(), path.distance(), 1e-3);
    }
}
