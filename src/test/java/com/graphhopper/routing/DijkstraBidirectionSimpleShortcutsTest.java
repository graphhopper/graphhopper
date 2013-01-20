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
package com.graphhopper.routing;

import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.routing.util.EdgeLevelFilter;
import com.graphhopper.routing.util.PrepareTowerNodesShortcuts;
import com.graphhopper.routing.util.PrepareTowerNodesShortcutsTest;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.Helper;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * TODO merge with PrepareTowerNodesShortcutsTest?
 *
 * @author Peter Karich
 */
public class DijkstraBidirectionSimpleShortcutsTest {

    RoutingAlgorithm createAlgoWithFilter(final LevelGraph lg) {
        return new DijkstraBidirectionRef(lg).edgeFilter(new EdgeLevelFilter(lg));
    }

    RoutingAlgorithm createAlgoWithFilterAndPathUnpacking(final LevelGraph lg) {
        return new PrepareTowerNodesShortcuts().graph(lg).createAlgo();
    }

    LevelGraph createGraph() {
        return new GraphBuilder().levelGraphCreate();
    }

    @Test
    public void testShortcutUnpacking() {
        LevelGraph g2 = createGraph();
        AbstractRoutingAlgorithmTester.initBiGraph(g2);
        // store skipped first node along with the shortcut
        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(g2).doWork();
        assertEquals(1, prepare.shortcuts());
        // use that node to correctly unpack the shortcut
        Path p = createAlgoWithFilterAndPathUnpacking(g2).calcPath(0, 4);
        assertEquals(p.toString(), 51, p.weight(), 1e-6);
        assertEquals(Helper.createTList(0, 7, 6, 8, 3, 4), p.calcNodes());
    }

    @Test
    public void testShortcutNoUnpacking() {
        LevelGraph g2 = createGraph();
        AbstractRoutingAlgorithmTester.initBiGraph(g2);
        new PrepareTowerNodesShortcuts().graph(g2).doWork();
        Path p = createAlgoWithFilter(g2).calcPath(0, 4);
        assertEquals(p.toString(), 51, p.weight(), 1e-6);
        assertEquals(Helper.createTList(0, 7, 6, 3, 4), p.calcNodes());
    }

    @Test
    public void testDirected() {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        // see 49.9052,10.35491
        //
        // =19-20-21-22=

        g.edge(18, 19, 1, true);
        g.edge(17, 19, 1, true);

        EdgeSkipIterator iter = g.edge(19, 20, 1, false);
        g.edge(20, 21, 1, false);
        g.edge(21, 22, 1, false);

        g.edge(22, 23, 1, true);
        g.edge(22, 24, 1, true);

        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(g);
        prepare.doWork();
        assertEquals(1, prepare.shortcuts());
        EdgeSkipIterator iter2 = (EdgeSkipIterator) GraphUtility.until(g.getEdges(19), 22);
        assertEquals(iter.edge(), iter2.skippedEdge());
        Path p = new DijkstraBidirectionRef(g) {
            @Override protected PathBidirRef createPath() {
                return new Path4Shortcuts(graph, weightCalc);
            }
        }.calcPath(17, 23);
        assertEquals(Helper.createTList(17, 19, 20, 21, 22, 23), p.calcNodes());
    }

    @Test
    public void testDirected1() {
        LevelGraph g = createGraph();
        PrepareTowerNodesShortcutsTest.initDirected1(g);
        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(g);
        prepare.doWork();
        RoutingAlgorithm algo = prepare.createAlgo();
        Path p = algo.calcPath(0, 6);
        assertEquals(Helper.createTList(0, 1, 3, 5, 4, 2, 6), p.calcNodes());

        p = algo.clear().calcPath(4, 7);
        assertEquals(Helper.createTList(4, 2, 6, 0, 1, 3, 7), p.calcNodes());
    }

    @Test
    public void testDirected2() {
        LevelGraph g = createGraph();
        PrepareTowerNodesShortcutsTest.initDirected2(g);
        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(g);
        prepare.doWork();
        assertEquals(1, prepare.shortcuts());
//        PrepareTowerNodesShortcutsTest.printEdges(g);
        RoutingAlgorithm algo = prepare.createAlgo();
        Path p = algo.calcPath(0, 10);
        assertEquals(10, p.distance(), 1e-6);
        assertEquals(Helper.createTList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), p.calcNodes());
    }

    @Test
    public void testTwoEdgesWithDifferentSpeed() {
        LevelGraph g = createGraph();
        // see 49.894653,9.309765
        //
        //         10
        //         |
        // 0-1-2-3-4-9
        //   |     |
        //   5-6-7-8        
        g.edge(1, 5, 1, CarStreetType.flags(50, true));
        g.edge(5, 6, 1, CarStreetType.flags(50, true));
        g.edge(6, 7, 1, CarStreetType.flags(50, true));
        g.edge(7, 8, 1, CarStreetType.flags(50, true));
        g.edge(8, 4, 1, CarStreetType.flags(50, true));

        g.edge(0, 1, 1, CarStreetType.flags(50, true));
        g.edge(1, 2, 1, CarStreetType.flags(10, true));
        g.edge(2, 3, 1, CarStreetType.flags(10, true));
        g.edge(3, 4, 1, CarStreetType.flags(10, true));
        g.edge(4, 9, 1, CarStreetType.flags(10, true));
        g.edge(4, 10, 1, CarStreetType.flags(50, true));

        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(g);
        prepare.doWork();
        assertEquals(2, prepare.shortcuts());
        Path p = new DijkstraBidirectionRef(g) {
            @Override protected PathBidirRef createPath() {
                return new Path4Shortcuts(graph, weightCalc);
            }
        }.calcPath(1, 4);
        assertEquals(Helper.createTList(1, 2, 3, 4), p.calcNodes());
    }
}
