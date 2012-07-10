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

import de.jetsli.graph.reader.PrinctonReader;
import de.jetsli.graph.storage.MemoryGraph;
import de.jetsli.graph.storage.Graph;
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

    static Graph matrixGraph;

    static {
        matrixGraph = createMatrixAlikeGraph();
    }
    int from;
    int to;
    
    public abstract RoutingAlgorithm createAlgo(Graph g);

    @Test public void testCalcShortestPath() {
        Graph graph = createTestGraph();
        Path p = createAlgo(graph).calcShortestPath(from, to);
        assertEquals(p.toString(), 13, p.distance(), 1e-6);
        assertEquals(p.toString(), 5, p.locations());
    }

    //
    //  .---h-.
    //  g   | |
    // / \ /  |
    // e--f-. |
    // |\/ \ \|
    // a-b--c-d
    //
    protected Graph createTestGraph() {
        Graph graph = createGraph(8);
        from = 0;
        to = 7;

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
        Path p = createAlgo(graph).calcShortestPath(0, 4);
        assertEquals(p.toString(), 20, p.distance(), 1e-6);
        assertEquals(p.toString(), 4, p.locations());
    }

    @Test public void testCalcIfNoWay() {
        Graph graph = createTestGraph();
        Path p = createAlgo(graph).calcShortestPath(0, 0);
        assertEquals(p.toString(), 0, p.distance(), 1e-6);
        assertEquals(p.toString(), 1, p.locations());
    }

    @Test public void testCalcIf1EdgeAway() {
        Graph graph = createTestGraph();
        Path p = createAlgo(graph).calcShortestPath(1, 2);
        assertEquals(p.toString(), 2, p.distance(), 1e-6);
        assertEquals(p.toString(), 2, p.locations());
    }

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

//    @Test public void testCustomIds() {
//        Graph graph = new Graph(6);
//        from = graph.addLocation(100, null);
//        to = graph.addLocation(200, null);
//        graph.edge(from, to, 20, true);
//        Path p = createDijkstra(graph).calcShortestPath(from, to);
//        assertEquals(p.toString(), 20, p.distance(), 1e-6);
//        assertEquals(p.toString(), 2, p.locations());
//    }
    // 1-2-3-4-5
    // |     / |
    // |    9  |
    // \   /   /
    //  8-7-6-/
    @Test public void testBidirectional() {
        Graph graph = createGraph(6);
        from = 0;
        to = 4;

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

        Path p = createAlgo(graph).calcShortestPath(from, to);
        assertEquals(p.toString(), 51, p.distance(), 1e-6);
        assertEquals(p.toString(), 6, p.locations());
    }

    // 1-2-3-4-5
    // |     / |
    // |    9  |
    // \   /   /
    //  8-7-6-/
    @Test public void testBidirectional2() {
        Graph graph = createGraph(6);
        from = 0;
        to = 4;

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

        Path p = createAlgo(graph).calcShortestPath(from, to);
        assertEquals(p.toString(), 40, p.distance(), 1e-6);
        assertEquals(p.toString(), 5, p.locations());
    }

    @Test public void testRekeyBugOfIntBinHeap() {
        // using DijkstraSimple + IntBinHeap then rekey loops endlessly
        Path p = createAlgo(matrixGraph).calcShortestPath(36, 91);
        assertEquals(12, p.locations());
        assertEquals(66f, p.distance(), 1e-3);
    }

    @Test
    public void testBug1() {
        assertEquals(17, createAlgo(matrixGraph).calcShortestPath(34, 36).distance(), 1e-5);
    }

    @Test
    public void testCannotCalculateSP() {
        Graph g = createGraph(10);
        g.edge(0, 1, 1, false);
        g.edge(1, 2, 1, false);

        Path p = createAlgo(g).calcShortestPath(0, 2);
        assertEquals(p.toString(), 3, p.locations());
    }

    @Test
    public void testDirectedGraphBug1() {
        Graph g = createGraph(5);
        g.edge(0, 1, 3, false);
        g.edge(1, 2, 3, false);

        g.edge(0, 3, 2, false);
        g.edge(3, 4, 3, false);
        g.edge(4, 2, 1, false);

        Path p = createAlgo(g).calcShortestPath(0, 2);
        assertEquals(p.toString(), 3, p.locations());
    }

    @Test public void testPerformance() throws IOException {
        int N = 10;
        int noJvmWarming = N / 4;

        String name = getClass().getSimpleName();
        Random rand = new Random(0);
        Graph graph = createGraph(0);

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
            Path p = d.calcShortestPath(index1, index2);
            if (i >= noJvmWarming)
                sw.stop();
            System.out.println("#" + i + " " + name + ":" + sw.getSeconds() + " " + p.locations());
        }
        System.out.println("# " + name + ":" + sw.stop().getSeconds() + ", per run:" + sw.stop().getSeconds() / ((float) (N - noJvmWarming)));
    }

    private static Graph createMatrixAlikeGraph() {
        int WIDTH = 10;
        int HEIGHT = 15;
        Graph tmp = new MemoryGraph(WIDTH * HEIGHT);
        int[][] matrix = new int[WIDTH][HEIGHT];
        int counter = 0;
        Random rand = new Random(12);
        for (int h = 0; h < HEIGHT; h++) {
            for (int w = 0; w < WIDTH; w++) {
                System.out.print(" |\t           ");
            }
            System.out.println();

            for (int w = 0; w < WIDTH; w++) {
                matrix[w][h] = counter++;

                if (h > 0) {
                    float dist = 5 + Math.abs(rand.nextInt(5));
                    System.out.print(" " + (int) dist + "\t           ");
                    tmp.edge(matrix[w][h], matrix[w][h - 1], dist, true);
                }
            }
            System.out.println();
            if (h > 0) {
                for (int w = 0; w < WIDTH; w++) {
                    System.out.print(" |\t           ");
                }
                System.out.println();
            }

            for (int w = 0; w < WIDTH; w++) {
                if (w > 0) {
                    float dist = 5 + Math.abs(rand.nextInt(5));
                    System.out.print("-- " + (int) dist + "\t-- ");
                    tmp.edge(matrix[w][h], matrix[w - 1][h], dist, true);
                }
                System.out.print("(" + matrix[w][h] + ")\t");
            }
            System.out.println();
        }

        return tmp;
    }
    
    Graph createGraph(int size) {
        return new MemoryGraph(size);
    }
}
