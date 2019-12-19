/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Test;

import static com.graphhopper.routing.RoutingAlgorithmTest.initTestStorage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Run some tests specific for {@link DijkstraOneToMany}
 *
 * @author Peter Karich
 * @see RoutingAlgorithmTest for test cases covering standard routing with this algorithm
 */
public class DijkstraOneToManyTest {

    private final EncodingManager encodingManager;
    private final FlagEncoder carEncoder;
    private Weighting defaultWeighting;

    public DijkstraOneToManyTest() {
        encodingManager = EncodingManager.create("car");
        carEncoder = encodingManager.getEncoder("car");
        defaultWeighting = new ShortestWeighting(carEncoder);
    }

    private static void initGraphWeightLimit(Graph g) {
        //      0----1
        //     /     |
        //    7--    |
        //   /   |   |
        //   6---5   |
        //   |   |   |
        //   4---3---2

        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);

        g.edge(3, 2, 1, true);
        g.edge(3, 5, 1, true);
        g.edge(5, 7, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(4, 6, 1, true);
        g.edge(6, 7, 1, true);
        g.edge(6, 5, 1, true);
        g.edge(0, 7, 1, true);
    }

    @Test
    public void testIssue182() {
        GraphHopperStorage graph = createGHStorage();
        initGraph(graph);
        Path p = calcPath(graph, 0, 8);
        assertEquals(IntArrayList.from(0, 7, 8), p.calcNodes());

        // expand SPT
        p = calcPath(graph, 0, 10);
        assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 10), p.calcNodes());
    }

    @Test
    public void testIssue239_and362() {
        GraphHopperStorage g = createGHStorage();
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 0, 1, true);

        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 4, 1, true);

        DijkstraOneToMany algo = createAlgo(g);
        assertEquals(-1, algo.findEndNode(0, 4));
        assertEquals(-1, algo.findEndNode(0, 4));

        assertEquals(1, algo.findEndNode(0, 1));
        assertEquals(1, algo.findEndNode(0, 1));
    }

    @Test
    public void testUseCache() {
        GraphHopperStorage graph = createGHStorage();
        initTestStorage(graph);
        RoutingAlgorithm algo = createAlgo(graph);
        Path p = algo.calcPath(0, 4);
        assertEquals(IntArrayList.from(0, 4), p.calcNodes());

        // expand SPT
        p = algo.calcPath(0, 7);
        assertEquals(IntArrayList.from(0, 4, 5, 7), p.calcNodes());

        // use SPT
        p = algo.calcPath(0, 2);
        assertEquals(IntArrayList.from(0, 1, 2), p.calcNodes());
    }

    @Test
    public void testDifferentEdgeFilter() {
        GraphHopperStorage g = new GraphBuilder(encodingManager).setCHProfiles(CHProfile.nodeBased(new FastestWeighting(carEncoder))).create();
        g.edge(4, 3, 10, true);
        g.edge(3, 6, 10, true);

        g.edge(4, 5, 10, true);
        g.edge(5, 6, 10, true);

        DijkstraOneToMany algo = createAlgo(g);
        algo.setEdgeFilter(new EdgeFilter() {
            @Override
            public boolean accept(EdgeIteratorState iter) {
                return iter.getAdjNode() != 5;
            }
        });
        Path p = algo.calcPath(4, 6);
        assertEquals(IntArrayList.from(4, 3, 6), p.calcNodes());

        // important call!
        algo.clear();
        algo.setEdgeFilter(new EdgeFilter() {
            @Override
            public boolean accept(EdgeIteratorState iter) {
                return iter.getAdjNode() != 3;
            }
        });
        p = algo.calcPath(4, 6);
        assertEquals(IntArrayList.from(4, 5, 6), p.calcNodes());
    }

    private void initGraph(Graph g) {
        // 0-1-2-3-4
        // |       /
        // 7-10----
        // \-8
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(4, 10, 1, true);

        g.edge(0, 7, 1, true);
        g.edge(7, 8, 1, true);
        g.edge(7, 10, 10, true);
    }

    @Test
    public void testWeightLimit_issue380() {
        GraphHopperStorage graph = createGHStorage();
        initGraphWeightLimit(graph);

        DijkstraOneToMany algo = createAlgo(graph);
        algo.setWeightLimit(3);
        Path p = algo.calcPath(0, 4);
        assertTrue(p.isFound());
        assertEquals(3.0, p.getWeight(), 1e-6);

        algo = createAlgo(graph);
        p = algo.calcPath(0, 3);
        assertTrue(p.isFound());
        assertEquals(3.0, p.getWeight(), 1e-6);
    }

    @Test
    public void testUseCacheZeroPath_issue707() {
        GraphHopperStorage graph = createGHStorage();
        initTestStorage(graph);
        RoutingAlgorithm algo = createAlgo(graph);

        Path p = algo.calcPath(0, 0);
        assertEquals(0, p.getDistance(), 0.00000);

        p = algo.calcPath(0, 4);
        assertEquals(IntArrayList.from(0, 4), p.calcNodes());

        // expand SPT
        p = algo.calcPath(0, 7);
        assertEquals(IntArrayList.from(0, 4, 5, 7), p.calcNodes());

        // use SPT
        p = algo.calcPath(0, 2);
        assertEquals(IntArrayList.from(0, 1, 2), p.calcNodes());
    }

    private GraphHopperStorage createGHStorage() {
        return new GraphBuilder(encodingManager).create();
    }

    private Path calcPath(GraphHopperStorage graph, int from, int to) {
        return createAlgo(graph).calcPath(from, to);
    }

    private DijkstraOneToMany createAlgo(GraphHopperStorage g) {
        return new DijkstraOneToMany(g, defaultWeighting, TraversalMode.NODE_BASED);
    }
}
