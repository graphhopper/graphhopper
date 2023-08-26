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
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests the correctness of the contraction hierarchies query in the presence of turn costs.
 * The graph preparation is done manually here and the tests try to focus on border cases that have to be covered
 * by the query algorithm correctly.
 */
public class CHQueryWithTurnCostsTest {

    private static class Fixture {
        private final int maxCost = 10;
        private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        private final DecimalEncodedValue turnCostEnc = TurnCost.create("car", maxCost);
        private final BaseGraph graph;
        private final CHConfig chConfig;
        private final String algoString;
        private CHStorage chStore;
        private CHStorageBuilder chBuilder;

        public Fixture(String algoString) {
            this.algoString = algoString;
            EncodingManager encodingManager = EncodingManager.start().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
            graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
            chConfig = CHConfig.edgeBased("profile", new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), Double.POSITIVE_INFINITY));
        }

        @Override
        public String toString() {
            return this.algoString;
        }

        private AbstractBidirectionEdgeCHNoSOD createAlgo() {
            return "astar".equals(algoString) ?
                    new AStarBidirectionEdgeCHNoSOD(RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig)) :
                    new DijkstraBidirectionEdgeCHNoSOD(RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig));
        }

        private void freeze() {
            graph.freeze();
            chStore = CHStorage.fromGraph(graph, chConfig);
            chBuilder = new CHStorageBuilder(chStore);
        }

        private void addShortcut(int from, int to, int firstOrigEdgeKey, int lastOrigEdgeKey, int skipped1, int skipped2, double weight, boolean reverse) {
            int flags = reverse ? PrepareEncoder.getScBwdDir() : PrepareEncoder.getScFwdDir();
            chBuilder.addShortcutEdgeBased(from, to, flags, weight, skipped1, skipped2, firstOrigEdgeKey, lastOrigEdgeKey);
        }

        private void setIdentityLevels() {
            chBuilder.setIdentityLevels();
        }

        private void setTurnCost(int from, int via, int to, double cost) {
            setTurnCost(getEdge(from, via), getEdge(via, to), via, cost);
        }

        private void setTurnCost(EdgeIteratorState edge1, EdgeIteratorState edge2, int viaNode, double costs) {
            graph.getTurnCostStorage().set(turnCostEnc, edge1.getEdge(), viaNode, edge2.getEdge(), costs);
        }

        private void setRestriction(int from, int via, int to) {
            setTurnCost(getEdge(from, via), getEdge(via, to), via, Double.POSITIVE_INFINITY);
        }

        private void setRestriction(EdgeIteratorState edge1, EdgeIteratorState edge2, int viaNode) {
            setTurnCost(edge1, edge2, viaNode, Double.POSITIVE_INFINITY);
        }

        private EdgeIteratorState getEdge(int from, int to) {
            return GHUtility.getEdge(graph, from, to);
        }

        private void testPathCalculation(int from, int to, int expectedWeight, IntArrayList expectedNodes) {
            testPathCalculation(from, to, expectedWeight, expectedNodes, 0);
        }

        private void testPathCalculation(int from, int to, int expectedEdgeWeight, IntArrayList expectedNodes, int expectedTurnCost) {
            int expectedWeight = expectedEdgeWeight + expectedTurnCost;
            int expectedDistance = expectedEdgeWeight;
            int expectedTime = expectedEdgeWeight * 60 + expectedTurnCost * 1000;
            AbstractBidirectionEdgeCHNoSOD algo = createAlgo();
            Path path = algo.calcPath(from, to);
            if (expectedWeight < 0) {
                assertFalse(path.isFound(), String.format(Locale.ROOT, "Unexpected path from %d to %d.", from, to));
            } else {
                if (expectedNodes != null) {
                    assertEquals(expectedNodes, path.calcNodes(), String.format(Locale.ROOT, "Unexpected path from %d to %d", from, to));
                }
                assertEquals(expectedWeight, path.getWeight(), 1.e-6, String.format(Locale.ROOT, "Unexpected path weight from %d to %d", from, to));
                assertEquals(expectedDistance, path.getDistance(), 1.e-6, String.format(Locale.ROOT, "Unexpected path distance from %d to %d", from, to));
                assertEquals(expectedTime, path.getTime(), String.format(Locale.ROOT, "Unexpected path time from %d to %d", from, to));
            }
        }
    }

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    new Fixture("astar"),
                    new Fixture("dijkstra")
            ).map(Arguments::of);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnCosts_bidirected_no_shortcuts_smallGraph(Fixture f) {
        // some special cases where from=to, or start and target edges are the same
        // 1 -- 0 -- 2
        f.graph.edge(1, 0).setDistance(3).set(f.speedEnc, 60, 60);
        f.graph.edge(0, 2).setDistance(5).set(f.speedEnc, 60, 60);
        f.setTurnCost(1, 0, 2, 3);
        f.freeze();

        // contraction yields no shortcuts for edge based case (at least without u-turns).
        f.setIdentityLevels();

        for (int i = 0; i < 3; ++i) {
            f.testPathCalculation(i, i, 0, IntArrayList.from(i));
        }
        f.testPathCalculation(1, 2, 8, IntArrayList.from(1, 0, 2), 3);
        f.testPathCalculation(2, 1, 8, IntArrayList.from(2, 0, 1));
        f.testPathCalculation(0, 1, 3, IntArrayList.from(0, 1));
        f.testPathCalculation(0, 2, 5, IntArrayList.from(0, 2));
        f.testPathCalculation(1, 0, 3, IntArrayList.from(1, 0));
        f.testPathCalculation(2, 0, 5, IntArrayList.from(2, 0));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnCosts_bidirected_no_shortcuts(Fixture f) {
        // 0 -- 2 -- 4 -- 6 -- 5 -- 3 -- 1
        f.graph.edge(0, 2).setDistance(3).set(f.speedEnc, 60, 60);
        f.graph.edge(2, 4).setDistance(2).set(f.speedEnc, 60, 60);
        f.graph.edge(4, 6).setDistance(7).set(f.speedEnc, 60, 60);
        f.graph.edge(6, 5).setDistance(9).set(f.speedEnc, 60, 60);
        f.graph.edge(5, 3).setDistance(1).set(f.speedEnc, 60, 60);
        f.graph.edge(3, 1).setDistance(4).set(f.speedEnc, 60, 60);
        f.setTurnCost(0, 2, 4, 3);
        f.setTurnCost(4, 6, 5, 6);
        f.setTurnCost(5, 6, 4, 2);
        f.setTurnCost(5, 3, 1, 5);
        f.freeze();

        // contraction yields no shortcuts
        f.setIdentityLevels();

        // note that we are using the shortest weighting but turn cost times are included whatsoever, see #1590
        f.testPathCalculation(0, 1, 26, IntArrayList.from(0, 2, 4, 6, 5, 3, 1), 14);
        f.testPathCalculation(1, 0, 26, IntArrayList.from(1, 3, 5, 6, 4, 2, 0), 2);
        f.testPathCalculation(4, 3, 17, IntArrayList.from(4, 6, 5, 3), 6);
        f.testPathCalculation(0, 0, 0, IntArrayList.from(0));
        f.testPathCalculation(4, 4, 0, IntArrayList.from(4));

        // also check if distance and times (including turn costs) are calculated correctly
        Path path = f.createAlgo().calcPath(0, 1);
        assertEquals(40, path.getWeight(), 1.e-3, "wrong weight");
        assertEquals(26, path.getDistance(), 1.e-3, "wrong distance");
        double weightPerMeter = 0.06;
        assertEquals((26 * weightPerMeter + 14) * 1000, path.getTime(), 1.e-3, "wrong time");
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnCosts_loopShortcutBwdSearch(Fixture f) {
        // the loop shortcut 4-4 will be encountered during the bwd search
        //             3
        //            / \
        //           1   2
        //            \ /
        // 0 - 7 - 8 - 4 - 6 - 5
        f.graph.edge(0, 7).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(7, 8).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(8, 4).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(4, 1).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(1, 3).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 2).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(2, 4).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(4, 6).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(6, 5).setDistance(1).set(f.speedEnc, 60, 0);
        f.setRestriction(8, 4, 6);
        f.setRestriction(8, 4, 2);
        f.setRestriction(1, 4, 6);

        f.freeze();

        f.setIdentityLevels();
        // from contracting nodes 1&2
        f.addShortcut(3, 4, 6, 8, 3, 4, 2, true);
        f.addShortcut(3, 4, 10, 12, 5, 6, 2, false);
        // from contracting node 3
        f.addShortcut(4, 4, 6, 13, 9, 10, 4, false);
        // from contracting node 4
        f.addShortcut(4, 8, 4, 12, 2, 11, 5, true);
        f.addShortcut(6, 8, 4, 14, 12, 7, 6, true);

        f.testPathCalculation(0, 5, 9, IntArrayList.from(0, 7, 8, 4, 1, 3, 2, 4, 6, 5));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnCosts_loopShortcutFwdSearch(Fixture f) {
        // the loop shortcut 4-4 will be encountered during the fwd search
        //         3
        //        / \
        //       1   2
        //        \ /
        // 5 - 6 - 4 - 7 - 8 - 0
        f.graph.edge(5, 6).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(6, 4).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(4, 1).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(1, 3).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 2).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(2, 4).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(4, 7).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(7, 8).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(8, 0).setDistance(1).set(f.speedEnc, 60, 0);
        f.setRestriction(6, 4, 7);
        f.setRestriction(6, 4, 2);
        f.setRestriction(1, 4, 7);
        f.freeze();

        f.setIdentityLevels();
        // from contracting nodes 1&2
        f.addShortcut(3, 4, 4, 6, 2, 3, 2, true);
        f.addShortcut(3, 4, 8, 10, 4, 5, 2, false);
        // from contracting node 3
        f.addShortcut(4, 4, 4, 10, 9, 10, 4, false);
        // from contracting node 4
        f.addShortcut(4, 6, 3, 10, 1, 11, 5, true);
        f.addShortcut(6, 7, 2, 12, 12, 6, 6, false);

        f.testPathCalculation(5, 0, 9, IntArrayList.from(5, 6, 4, 1, 3, 2, 4, 7, 8, 0));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnCosts_directed_single_shortcut(Fixture f) {
        //    2     3
        //   /5\   /1\
        //  /   \2/   \
        // 1     0     4
        f.graph.edge(1, 2).setDistance(4).set(f.speedEnc, 60, 0);
        f.graph.edge(2, 0).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(0, 3).setDistance(3).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 4).setDistance(2).set(f.speedEnc, 60, 0);
        f.setTurnCost(1, 2, 0, 5);
        f.setTurnCost(2, 0, 3, 2);
        f.setTurnCost(0, 3, 4, 1);
        f.freeze();

        // only when node 0 is contracted a shortcut is added
        f.setIdentityLevels();
        f.addShortcut(2, 3, 2, 4, 1, 2, 7, false);

        // when we are searching a path to the highest level node, the backward search will not expand any edges
        f.testPathCalculation(1, 4, 11, IntArrayList.from(1, 2, 0, 3, 4), 8);
        f.testPathCalculation(2, 4, 7, IntArrayList.from(2, 0, 3, 4), 3);
        f.testPathCalculation(0, 4, 5, IntArrayList.from(0, 3, 4), 1);

        // when we search a path to or start the search from a low level node both forward and backward searches run
        f.testPathCalculation(1, 0, 6, IntArrayList.from(1, 2, 0), 5);
        f.testPathCalculation(0, 4, 5, IntArrayList.from(0, 3, 4), 1);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnCosts_directed_single_shortcut_fwdSearchStopsQuickly(Fixture f) {
        //     0
        //    / \
        // 1-2-s-3-4
        f.graph.edge(1, 2).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(2, 0).setDistance(3).set(f.speedEnc, 60, 0);
        f.graph.edge(0, 3).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 4).setDistance(3).set(f.speedEnc, 60, 0);
        f.freeze();

        f.setTurnCost(1, 2, 0, 2);
        f.setTurnCost(0, 3, 4, 4);

        f.setIdentityLevels();
        // from contracting node 0
        f.addShortcut(2, 3, 2, 4, 1, 2, 4, false);

        f.testPathCalculation(1, 4, 9, IntArrayList.from(1, 2, 0, 3, 4), 6);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnCosts_directed_two_shortcuts(Fixture f) {
        //    3     0
        //   /5\   /1\
        //  /   \2/   \
        // 2     1     4
        f.graph.edge(2, 3).setDistance(4).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 1).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(1, 0).setDistance(3).set(f.speedEnc, 60, 0);
        f.graph.edge(0, 4).setDistance(2).set(f.speedEnc, 60, 0);
        f.setTurnCost(2, 3, 1, 5);
        f.setTurnCost(3, 1, 0, 2);
        f.setTurnCost(1, 0, 4, 1);
        f.freeze();

        f.setIdentityLevels();
        // contraction of node 0 and 1 each yield a single shortcut
        f.addShortcut(1, 4, 4, 6, 2, 3, 6, false);
        f.addShortcut(3, 4, 2, 6, 1, 4, 10, false);

        // the turn costs have to be accounted for also when the shortcuts are used
        f.testPathCalculation(2, 4, 11, IntArrayList.from(2, 3, 1, 0, 4), 8);
        f.testPathCalculation(1, 4, 5, IntArrayList.from(1, 0, 4), 1);
        f.testPathCalculation(2, 0, 9, IntArrayList.from(2, 3, 1, 0), 7);
        f.testPathCalculation(3, 4, 7, IntArrayList.from(3, 1, 0, 4), 3);
        f.testPathCalculation(2, 1, 6, IntArrayList.from(2, 3, 1), 5);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPath_directConnectionIsNotTheBestPath(Fixture f) {
        // this case is interesting because there is an expensive edge going from the source to the target directly
        // 0 --------\
        // |         |
        // v         v
        // 2 -> 3 -> 1
        f.graph.edge(0, 2).setDistance(3).set(f.speedEnc, 60, 0);
        f.graph.edge(2, 3).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 1).setDistance(9).set(f.speedEnc, 60, 0);
        f.graph.edge(0, 1).setDistance(50).set(f.speedEnc, 60, 0);
        f.setTurnCost(2, 3, 1, 4);
        f.freeze();

        // no shortcuts here
        f.setIdentityLevels();
        f.testPathCalculation(0, 1, 14, IntArrayList.from(0, 2, 3, 1), 4);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPath_upwardSearchRunsIntoTarget(Fixture f) {
        // this case is interesting because one possible path runs from 0 to 4 directly (the backward search does not
        // contribute anything in this case), but this path is not as good as the one via node 5
        // 0 -> 1 -> 5
        //      |    |
        //      v    v
        //      3 -> 4 -> 2
        f.graph.edge(0, 1).setDistance(9).set(f.speedEnc, 60, 0);
        f.graph.edge(1, 5).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(1, 3).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 4).setDistance(4).set(f.speedEnc, 60, 0);
        f.graph.edge(5, 4).setDistance(6).set(f.speedEnc, 60, 0);
        f.graph.edge(4, 2).setDistance(3).set(f.speedEnc, 60, 0);
        f.setTurnCost(1, 3, 4, 3);
        f.freeze();

        // no shortcuts here
        f.setIdentityLevels();
        f.testPathCalculation(0, 4, 17, IntArrayList.from(0, 1, 5, 4));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPath_downwardSearchRunsIntoTarget(Fixture f) {
        // 0 <- 1
        //  \   ^
        //   \  |
        //    <-2<-3
        f.graph.edge(1, 0).setDistance(9).set(f.speedEnc, 60, 0);
        f.graph.edge(2, 0).setDistance(14).set(f.speedEnc, 60, 0);
        f.graph.edge(2, 1).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 2).setDistance(9).set(f.speedEnc, 60, 0);
        f.freeze();

        //no shortcuts
        f.setIdentityLevels();
        f.testPathCalculation(3, 0, 20, IntArrayList.from(3, 2, 1, 0));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPath_incomingShortcut(Fixture f) {
        // this test covers the case where an original edge and a shortcut have the same traversal id
        // 0 -- 1
        // | __/
        // v/
        // 3 -> 2
        f.graph.edge(0, 1).setDistance(9).set(f.speedEnc, 60, 60);
        f.graph.edge(0, 3).setDistance(14).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 2).setDistance(9).set(f.speedEnc, 60, 0);
        f.freeze();
        f.setIdentityLevels();
        f.addShortcut(1, 3, 1, 2, 0, 1, 23, false);
        f.testPathCalculation(0, 2, 23, IntArrayList.from(0, 3, 2));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnCosts_fwdBwdSearchesMeetWithUTurn(Fixture f) {
        //       3
        //       |
        // 0 --- 2 --- 1
        f.graph.edge(0, 2).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(2, 3).setDistance(2).set(f.speedEnc, 60, 60);
        f.graph.edge(2, 1).setDistance(3).set(f.speedEnc, 60, 0);
        f.setRestriction(0, 2, 1);
        f.setTurnCost(0, 2, 3, 5);
        f.setTurnCost(2, 3, 2, 4);
        f.setTurnCost(3, 2, 1, 7);
        f.freeze();

        // contraction yields no shortcuts
        f.setIdentityLevels();

        // without u-turns no path can be found
        f.testPathCalculation(0, 1, -1, IntArrayList.from());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPath_doNotMakeUTurn(Fixture f) {
        // in this case there should be no u-turn at node A, but in principal it would be ok to take a shortcut from
        // A to B
        checkUTurnNotBeingUsed(f, false);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPath_doNotMakeUTurn_toLowerLevelNode(Fixture f) {
        // in this case it would be forbidden to take the shortcut from A to B because B has lower level than A and
        // because we cannot do a shortcut at node A. The optimization to not check the node levels in CHLevelEdgeFilter
        // that relies on shortcuts to lower level nodes being disconnected can 'hide' a u-turn bug here.
        checkUTurnNotBeingUsed(f, true);
    }

    private void checkUTurnNotBeingUsed(Fixture f, boolean toLowerLevelNode) {
        //           A <- 1
        //           |
        // 2 <- B <- 3 <- 0
        int nodeA = 4;
        int nodeB = 5;
        if (toLowerLevelNode) {
            int tmp = nodeA;
            nodeA = nodeB;
            nodeB = tmp;
        }
        f.graph.edge(1, nodeA).setDistance(4).set(f.speedEnc, 60, 0);
        f.graph.edge(0, 3).setDistance(4).set(f.speedEnc, 60, 0);
        f.graph.edge(nodeB, 2).setDistance(1).set(f.speedEnc, 60, 0);
        final EdgeIteratorState e3toB = f.graph.edge(3, nodeB).setDistance(2).set(f.speedEnc, 60, 0);
        final EdgeIteratorState e3toA = f.graph.edge(3, nodeA).setDistance(1).set(f.speedEnc, 60, 60);
        f.freeze();
        f.setRestriction(0, 3, nodeB);

        // one shortcut when contracting node 3
        f.setIdentityLevels();
        if (toLowerLevelNode) {
            f.addShortcut(nodeB, nodeA, e3toA.detach(true).getEdgeKey(), e3toB.getEdgeKey(), e3toA.getEdge(), e3toB.getEdge(), 2, true);
        } else {
            f.addShortcut(nodeA, nodeB, e3toA.detach(true).getEdgeKey(), e3toB.getEdgeKey(), e3toA.getEdge(), e3toB.getEdge(), 2, false);
        }

        // without u-turns the only 'possible' path 0-3-A-3-B-2 is forbidden
        f.testPathCalculation(0, 2, -1, IntArrayList.from());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnCosts_loop(Fixture f) {
        //       3\
        //       |/
        // 0 --- 2 --- 1
        final EdgeIteratorState edge1 = f.graph.edge(0, 2).setDistance(4).set(f.speedEnc, 60, 0);
        final EdgeIteratorState edge2 = f.graph.edge(2, 3).setDistance(1).set(f.speedEnc, 60, 60);
        final EdgeIteratorState edge3 = f.graph.edge(3, 2).setDistance(7).set(f.speedEnc, 60, 0);
        final EdgeIteratorState edge4 = f.graph.edge(2, 1).setDistance(3).set(f.speedEnc, 60, 0);
        // need to specify edges explicitly because there are two edges between nodes 2 and 3
        f.setRestriction(edge1, edge4, 2);
        f.setTurnCost(edge1, edge2, 2, 3);
        f.freeze();

        // no shortcuts
        f.setIdentityLevels();

        // without u-turns we need to take the loop
        f.testPathCalculation(0, 1, 15, IntArrayList.from(0, 2, 3, 2, 1), 3);

        // additional check
        f.testPathCalculation(3, 1, 4, IntArrayList.from(3, 2, 1));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnCosts_multiple_bridge_nodes(Fixture f) {
        //   --- 2 ---
        //  /         \
        // 0 --- 3 --- 1
        //  \         /
        //   --- 4 ---
        f.graph.edge(0, 2).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(0, 3).setDistance(3).set(f.speedEnc, 60, 0);
        f.graph.edge(0, 4).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(2, 1).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 1).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(4, 1).setDistance(6).set(f.speedEnc, 60, 0);
        f.setTurnCost(0, 2, 1, 9);
        f.setTurnCost(0, 3, 1, 2);
        f.setTurnCost(0, 4, 1, 1);
        f.freeze();

        // contraction yields no shortcuts
        f.setIdentityLevels();

        // going via 2, 3 and 4 is possible, but we want the shortest path taking into account turn costs also at
        // the bridge node
        f.testPathCalculation(0, 1, 5, IntArrayList.from(0, 3, 1), 2);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPath_shortcutLoopIsRecognizedAsIncomingEdge(Fixture f) {
        //          -0-
        //          \ /
        // 3 -- 4 -- 2 -- 1
        EdgeIteratorState edge0 = f.graph.edge(3, 4).setDistance(1).set(f.speedEnc, 60, 60);
        EdgeIteratorState edge1 = f.graph.edge(4, 2).setDistance(1).set(f.speedEnc, 60, 60);
        EdgeIteratorState edge2 = f.graph.edge(2, 0).setDistance(1).set(f.speedEnc, 60, 0);
        EdgeIteratorState edge3 = f.graph.edge(0, 2).setDistance(1).set(f.speedEnc, 60, 0);
        EdgeIteratorState edge4 = f.graph.edge(2, 1).setDistance(1).set(f.speedEnc, 60, 0);
        f.setRestriction(edge1, edge4, 2);
        f.freeze();

        f.setIdentityLevels();
        // contracting node 0 yields (the only) shortcut - and it's a loop
        f.addShortcut(2, 2, edge2.getEdgeKey(), edge3.getEdgeKey(), edge2.getEdge(), edge3.getEdge(), 2, false);

        // node 2 is the bridge node where the forward and backward searches meet (highest level). since there is a turn restriction
        // at node 2 we cannot go from 4 to 1 directly, but we need to take the loop at 2 first. when the backward
        // search arrives at 2 it is crucial that the ('forward') loop-shortcut at 2 is recognized as an incoming edge
        // at node 2, otherwise the backward search ends at node 2. the forward search can never reach node 2 at all,
        // because it never goes to a lower level. so when the backward search does not see the 'forward' loop shortcut
        // no path between 3 and 1 will be found even though there is one.
        f.testPathCalculation(3, 1, 5, IntArrayList.from(3, 4, 2, 0, 2, 1));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnRestriction_single_loop(Fixture f) {
        //     0
        //     | \
        //     |  >
        // 3-> 4---1
        //     |
        //     v  no right turn at 4 when coming from 3!
        //     2
        f.graph.edge(3, 4).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(4, 0).setDistance(1).set(f.speedEnc, 60, 60);
        f.graph.edge(0, 1).setDistance(3).set(f.speedEnc, 60, 0);
        f.graph.edge(4, 1).setDistance(5).set(f.speedEnc, 60, 60);
        f.graph.edge(4, 2).setDistance(4).set(f.speedEnc, 60, 0);
        f.setRestriction(3, 4, 2);
        f.freeze();

        f.setIdentityLevels();
        // contracting node 0
        f.addShortcut(1, 4, 2, 4, 1, 2, 4, true);
        // contracting node 1
        f.addShortcut(4, 4, 2, 6, 5, 3, 9, false);

        f.testPathCalculation(3, 2, 15, IntArrayList.from(3, 4, 0, 1, 4, 2));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPath_singleLoopInFwdSearch(Fixture f) {
        runTestWithSingleLoop(f, true);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPath_singleLoopInBwdSearch(Fixture f) {
        runTestWithSingleLoop(f, false);
    }

    private void runTestWithSingleLoop(Fixture f, boolean loopInFwdSearch) {
        // because we set the node levels equal to the node ids, depending on the size relation between node A and B
        // either the fwd search or the bwd search will explore the loop at node 5.
        // in any case it is important that the fwd/bwd search unpacks the loop shortcut at node 5 correctly
        int nodeA = 0;
        int nodeB = 6;
        if (!loopInFwdSearch) {
            int tmp = nodeA;
            nodeA = nodeB;
            nodeB = tmp;
        }
        //  4 1<-3
        //  | |  |
        //  A-5->2
        //    |
        //    B-7
        f.graph.edge(4, nodeA).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(nodeA, 5).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(5, 2).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(2, 3).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(3, 1).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(1, 5).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(5, nodeB).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(nodeB, 7).setDistance(2).set(f.speedEnc, 60, 0);
        f.setRestriction(nodeA, 5, nodeB);
        f.freeze();
        f.setIdentityLevels();
        f.addShortcut(3, 5, 8, 10, 4, 5, 3, false);
        f.addShortcut(3, 5, 4, 6, 2, 3, 3, true);
        f.addShortcut(5, 5, 4, 10, 9, 8, 6, false);

        f.testPathCalculation(4, 7, 12, IntArrayList.from(4, nodeA, 5, 2, 3, 1, 5, nodeB, 7));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnRestriction_double_loop(Fixture f) {
        //   1
        //   |\  at 6 we can only take the next left turn (can not skip a turn or go right)
        //   | \
        //   0--6--2
        //     / \ |
        //     |  \|
        // 4---7   3
        //     |
        //     |  no right turn at 7 when coming from 4 and no left turn at 7 when coming from 5!
        //     5
        final EdgeIteratorState e0to1 = f.graph.edge(0, 1).setDistance(2).set(f.speedEnc, 60, 60);
        final EdgeIteratorState e1to6 = f.graph.edge(1, 6).setDistance(1).set(f.speedEnc, 60, 60);
        final EdgeIteratorState e0to6 = f.graph.edge(0, 6).setDistance(4).set(f.speedEnc, 60, 60);
        final EdgeIteratorState e2to6 = f.graph.edge(2, 6).setDistance(5).set(f.speedEnc, 60, 60);
        final EdgeIteratorState e2to3 = f.graph.edge(2, 3).setDistance(3).set(f.speedEnc, 60, 60);
        final EdgeIteratorState e3to6 = f.graph.edge(3, 6).setDistance(2).set(f.speedEnc, 60, 60);
        final EdgeIteratorState e6to7 = f.graph.edge(7, 6).setDistance(1).set(f.speedEnc, 60, 60);
        final EdgeIteratorState e4to7 = f.graph.edge(7, 4).setDistance(3).set(f.speedEnc, 60, 60);
        final EdgeIteratorState e5to7 = f.graph.edge(7, 5).setDistance(2).set(f.speedEnc, 60, 60);

        f.setRestriction(e6to7, e1to6, 6);
        f.setRestriction(e6to7, e2to6, 6);
        f.setRestriction(e6to7, e3to6, 6);
        f.setRestriction(e1to6, e3to6, 6);
        f.setRestriction(e1to6, e6to7, 6);
        f.setRestriction(e1to6, e0to6, 6);

        f.setRestriction(e4to7, e5to7, 7);
        f.setRestriction(e5to7, e4to7, 7);
        f.freeze();

        f.setIdentityLevels();
        // contracting node 0,1,2,3
        f.addShortcut(1, 6, 4, 0, 2, 0, 6, true);
        f.addShortcut(3, 6, 6, 8, 3, 4, 8, true);
        f.addShortcut(6, 6, 4, 2, 9, 1, 7, false);
        f.addShortcut(6, 6, 6, 10, 10, 5, 10, false);
        // contracting node 4 and 5 yields no shortcuts
        // contracting node 6 --> three shortcuts to account for double loop (we nest shortcuts inside each other)
        f.addShortcut(6, 7, 12, 2, 6, 11, 8, true);
        f.addShortcut(6, 7, 12, 10, 13, 12, 18, true);
        f.addShortcut(7, 7, 12, 12, 14, 6, 19, false);

        f.testPathCalculation(4, 5, 24, IntArrayList.from(4, 7, 6, 0, 1, 6, 2, 3, 6, 7, 5));
        f.testPathCalculation(5, 4, 24, IntArrayList.from(5, 7, 6, 0, 1, 6, 2, 3, 6, 7, 4));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testFindPathWithTurnRestriction_two_different_loops(Fixture f) {
        // 1
        // | \
        // ^  \
        // |   |
        // 0<- 5
        //     | \
        //     |  >
        // 3-> 6---4
        //     |
        //     v  no right turn at 6 when coming from 3!
        //     2
        f.graph.edge(0, 1).setDistance(2).set(f.speedEnc, 60, 0);
        f.graph.edge(1, 5).setDistance(1).set(f.speedEnc, 60, 60);
        f.graph.edge(5, 0).setDistance(1).set(f.speedEnc, 60, 0);
        f.graph.edge(5, 4).setDistance(5).set(f.speedEnc, 60, 0);
        f.graph.edge(5, 6).setDistance(3).set(f.speedEnc, 60, 60);
        f.graph.edge(6, 4).setDistance(4).set(f.speedEnc, 60, 60);

        f.graph.edge(3, 6).setDistance(3).set(f.speedEnc, 60, 0);
        f.graph.edge(6, 2).setDistance(4).set(f.speedEnc, 60, 0);
        f.setRestriction(3, 6, 2);
        f.freeze();

        f.setIdentityLevels();
        // contracting node 0
        f.addShortcut(1, 5, 4, 0, 2, 0, 3, true);
        // contracting node 1
        f.addShortcut(5, 5, 4, 2, 8, 1, 4, false);
        // contracting node 2 & 3 does not yield any shortcuts
        // contracting node 4
        f.addShortcut(5, 6, 6, 11, 3, 5, 9, false);
        // contracting node 5 --> two shortcuts to account for loop (we nest shortcuts inside each other)
        f.addShortcut(5, 6, 9, 2, 4, 9, 7, true);
        f.addShortcut(6, 6, 9, 8, 11, 4, 10, false);
        // contracting node 6 --> no more shortcuts


        List<List<Integer>> distMatrix = Arrays.asList(
                // -1 if no path is expected
                Arrays.asList(0, 2, 10, -1, 8, 3, 6),
                Arrays.asList(2, 0, 8, -1, 6, 1, 4),
                Arrays.asList(-1, -1, 0, -1, -1, -1, -1),
                Arrays.asList(7, 7, 17, 0, 7, 6, 3),
                Arrays.asList(8, 8, 8, -1, 0, 7, 4),
                Arrays.asList(1, 1, 7, -1, 5, 0, 3),
                Arrays.asList(4, 4, 4, -1, 4, 3, 0));

        for (int i = 0; i < distMatrix.size(); ++i) {
            for (int j = 0; j < distMatrix.get(i).size(); ++j) {
                f.testPathCalculation(i, j, distMatrix.get(i).get(j), null);
            }
        }
    }

}
