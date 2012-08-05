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
package de.jetsli.graph.routing;

import de.jetsli.graph.reader.CarFlags;
import de.jetsli.graph.reader.PrinctonReader;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.MemoryGraph;
import de.jetsli.graph.storage.MemoryGraphSafe;
import de.jetsli.graph.util.StopWatch;
import java.io.IOException;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public abstract class AbstractRoutingAlgorithmTester {

    public static Graph matrixGraph;

    static {
        matrixGraph = createMatrixAlikeGraph();
    }

    public abstract RoutingAlgorithm createAlgo(Graph g);

    @Test public void testCalcShortestPath() {
        Graph graph = createTestGraph();
        Path p = createAlgo(graph).calcPath(0, 7);
        assertEquals(p.toString(), 13, p.distance(), 1e-6);
        assertEquals(p.toString(), 5, p.locations());
    }

    @Test public void testCalcFastestPath() {
        Graph graph = createGraph(20);
        graph.edge(0, 1, 7, CarFlags.create(10, false));
        graph.edge(0, 4, 5, CarFlags.create(20, false));

        graph.edge(1, 4, 7, CarFlags.create(10, true));
        graph.edge(1, 5, 7, CarFlags.create(10, true));
        graph.edge(1, 2, 20, CarFlags.create(10, true));

        graph.edge(5, 2, 5, CarFlags.create(10, false));
        graph.edge(2, 3, 5, CarFlags.create(10, false));

        graph.edge(5, 3, 11, CarFlags.create(20, false));
        graph.edge(3, 7, 7, CarFlags.create(10, false));

        graph.edge(4, 6, 5, CarFlags.create(20, false));
        graph.edge(5, 4, 7, CarFlags.create(10, false));

        graph.edge(5, 6, 7, CarFlags.create(10, false));
        graph.edge(7, 5, 5, CarFlags.create(20, false));

        graph.edge(6, 7, 5, CarFlags.create(20, true));
        Path p = createAlgo(graph).setType(AlgoType.SHORTEST).calcPath(0, 3);
        assertEquals(p.toString(), 24, p.distance(), 1e-6);
        assertEquals(p.toString(), 5, p.locations());

        p = createAlgo(graph).setType(AlgoType.FASTEST).calcPath(0, 3);
        // assertEquals(p.toString(), 24, p.weight(), 1e-6);
        assertEquals(p.toString(), 6, p.locations());
    }

    // see test-graph.png !
    protected Graph createTestGraph() {
        Graph graph = createGraph(20);

        graph.edge(0, 1, 7, true);
        graph.edge(0, 4, 6, true);

        graph.edge(1, 4, 1, true);
        graph.edge(1, 5, 8, true);
        graph.edge(1, 2, 2, true);

        graph.edge(2, 5, 5, true);
        graph.edge(2, 3, 2, true);

        graph.edge(3, 5, 2, true);
        graph.edge(3, 7, 10, true);

        graph.edge(4, 6, 4, true);
        graph.edge(4, 5, 7, true);

        graph.edge(5, 6, 2, true);
        graph.edge(5, 7, 1, true);

        graph.edge(6, 7, 5, true);
        return graph;
    }

    @Test public void testWikipediaShortestPath() {
        Graph graph = createWikipediaTestGraph();
        Path p = createAlgo(graph).calcPath(0, 4);
        assertEquals(p.toString(), 20, p.distance(), 1e-6);
        assertEquals(p.toString(), 4, p.locations());
    }

    @Test public void testCalcIfNoWay() {
        Graph graph = createTestGraph();
        Path p = createAlgo(graph).calcPath(0, 0);
        assertEquals(p.toString(), 0, p.distance(), 1e-6);
        assertEquals(p.toString(), 1, p.locations());
    }

    @Test public void testCalcIf1EdgeAway() {
        Graph graph = createTestGraph();
        Path p = createAlgo(graph).calcPath(1, 2);
        assertEquals(p.toString(), 2, p.distance(), 1e-6);
        assertEquals(p.toString(), 2, p.locations());
    }

    // see wikipedia-graph.svg !
    protected Graph createWikipediaTestGraph() {
        Graph graph = createGraph(6);

        graph.edge(0, 1, 7, true);
        graph.edge(0, 2, 9, true);
        graph.edge(0, 5, 14, true);

        graph.edge(1, 2, 10, true);
        graph.edge(1, 3, 15, true);

        graph.edge(2, 5, 2, true);
        graph.edge(2, 3, 11, true);

        graph.edge(3, 4, 6, true);

        graph.edge(4, 5, 9, true);

        return graph;
    }

    // 1-2-3-4-5
    // |     / |
    // |    9  |
    // \   /   /
    //  8-7-6-/
    @Test public void testBidirectional() {
        Graph graph = createGraph(6);

        graph.edge(0, 1, 100, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 25, true);
        graph.edge(5, 6, 25, true);
        graph.edge(6, 7, 5, true);
        graph.edge(7, 0, 5, true);
        graph.edge(3, 8, 20, true);
        graph.edge(8, 6, 20, true);

        Path p = createAlgo(graph).calcPath(0, 4);
        assertEquals(p.toString(), 51, p.distance(), 1e-6);
        assertEquals(p.toString(), 6, p.locations());
    }

    // 1-2-3-4-5
    // |     / |
    // |    9  |
    // \   /   /
    //  8-7-6-/
    @Test public void testBidirectional2() {
        Graph graph = createGraph(20);

        graph.edge(0, 1, 100, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 20, true);
        graph.edge(5, 6, 10, true);
        graph.edge(6, 7, 5, true);
        graph.edge(7, 0, 5, true);
        graph.edge(3, 8, 20, true);
        graph.edge(8, 6, 20, true);

        Path p = createAlgo(graph).calcPath(0, 4);
        assertEquals(p.toString(), 40, p.distance(), 1e-6);
        assertEquals(p.toString(), 5, p.locations());
    }

    @Test
    public void testRekeyBugOfIntBinHeap() {
        // using DijkstraSimple + IntBinHeap then rekey loops endlessly
        Path p = createAlgo(matrixGraph).calcPath(36, 91);
        assertEquals(12, p.locations());
        assertEquals(66f, p.distance(), 1e-3);
    }

    @Test
    public void testBug1() {
        assertEquals(17, createAlgo(matrixGraph).calcPath(34, 36).distance(), 1e-5);
    }

    @Test
    public void testCannotCalculateSP() {
        Graph g = createGraph(10);
        g.edge(0, 1, 1, false);
        g.edge(1, 2, 1, false);

        Path p = createAlgo(g).calcPath(0, 2);
        assertEquals(p.toString(), 3, p.locations());
    }

    @Test
    public void testDirectedGraphBug1() {
        Graph g = createGraph(10);
        g.edge(0, 1, 3, false);
        g.edge(1, 2, 3, false);

        g.edge(0, 3, 2, false);
        g.edge(3, 4, 3, false);
        g.edge(4, 2, 1, false);

        Path p = createAlgo(g).calcPath(0, 2);
        assertEquals(p.toString(), 3, p.locations());
    }

    @Test
    public void testDirectedGraphBug2() {
        Graph g = createGraph(10);
        g.edge(0, 1, 1, false);
        g.edge(1, 2, 1, false);
        g.edge(2, 3, 1, false);

        g.edge(3, 1, 3, true);

        Path p = createAlgo(g).calcPath(0, 3);
        assertEquals(p.toString(), 4, p.locations());
    }

    @Test public void testPerformance() throws IOException {
        int N = 10;
        int noJvmWarming = N / 4;

        String name = getClass().getSimpleName();
        Random rand = new Random(0);
        Graph graph = createGraph(10000);

        String bigFile = "10000EWD.txt.gz";

//        String bigFile = "largeEWD.txt.gz";
        new PrinctonReader(graph).setStream(new GZIPInputStream(PrinctonReader.class.getResourceAsStream(bigFile), 8 * (1 << 10))).read();
        StopWatch sw = new StopWatch();
        for (int i = 0; i < N; i++) {
            int index1 = Math.abs(rand.nextInt(graph.getNodes()));
            int index2 = Math.abs(rand.nextInt(graph.getNodes()));
            RoutingAlgorithm d = createAlgo(graph);
            if (i >= noJvmWarming)
                sw.start();
            Path p = d.calcPath(index1, index2);
            if (i >= noJvmWarming)
                sw.stop();

            System.out.println("#" + i + " " + name + ":" + sw.getSeconds() + " " + p.locations());
        }

        float perRun = sw.stop().getSeconds() / ((float) (N - noJvmWarming));
        System.out.println("# " + name + ":" + sw.stop().getSeconds() + ", per run:" + perRun);
        assertTrue("speed to low!? " + perRun + " per run", perRun < 0.07);
    }

    private static Graph createMatrixAlikeGraph() {
        int WIDTH = 10;
        int HEIGHT = 15;
        Graph tmp = new MemoryGraphSafe(WIDTH * HEIGHT);
        int[][] matrix = new int[WIDTH][HEIGHT];
        int counter = 0;
        Random rand = new Random(12);
        boolean print = false;
        for (int h = 0; h < HEIGHT; h++) {
            if (print) {
                for (int w = 0; w < WIDTH; w++) {
                    System.out.print(" |\t           ");
                }
                System.out.println();
            }

            for (int w = 0; w < WIDTH; w++) {
                matrix[w][h] = counter++;

                if (h > 0) {
                    float dist = 5 + Math.abs(rand.nextInt(5));
                    if (print)
                        System.out.print(" " + (int) dist + "\t           ");
                    tmp.edge(matrix[w][h], matrix[w][h - 1], dist, true);
                }
            }
            if (print) {
                System.out.println();
                if (h > 0) {
                    for (int w = 0; w < WIDTH; w++) {
                        System.out.print(" |\t           ");
                    }
                    System.out.println();
                }
            }

            for (int w = 0; w < WIDTH; w++) {
                if (w > 0) {
                    float dist = 5 + Math.abs(rand.nextInt(5));
                    if (print)
                        System.out.print("-- " + (int) dist + "\t-- ");
                    tmp.edge(matrix[w][h], matrix[w - 1][h], dist, true);
                }
                if (print)
                    System.out.print("(" + matrix[w][h] + ")\t");
            }
            if (print)
                System.out.println();
        }

        return tmp;
    }

    Graph createGraph(int size) {
        return new MemoryGraphSafe(null, size, 4 * size);
    }
}
