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
package com.graphhopper.storage;

import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.util.Bike2WeightFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.BBox;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class GraphHopperStorageCHTest extends GraphHopperStorageTest {
    private CHStorage getCHStore(GraphHopperStorage ghStorage) {
        return ghStorage.getCHStore(ghStorage.getCHConfigs().get(0).getName());
    }

    private RoutingCHGraph getRoutingCHGraph(GraphHopperStorage ghStorage) {
        return ghStorage.getRoutingCHGraph(ghStorage.getCHConfigs().get(0).getName());
    }

    @Override
    protected GraphHopperStorage newGHStorage(Directory dir, boolean enabled3D) {
        return newGHStorage(dir, enabled3D, -1);
    }

    @Override
    public GraphHopperStorage newGHStorage(Directory dir, boolean is3D, int segmentSize) {
        return newGHStorage(dir, is3D, false, segmentSize);
    }

    private GraphHopperStorage newGHStorage(boolean is3D, boolean forEdgeBasedTraversal) {
        return newGHStorage(new RAMDirectory(defaultGraphLoc, true), is3D, forEdgeBasedTraversal, -1).create(defaultSize);
    }

    private GraphHopperStorage newGHStorage(Directory dir, boolean is3D, boolean forEdgeBasedTraversal, int segmentSize) {
        GraphHopperStorage graph = new GraphBuilder(encodingManager)
                .setDir(dir).set3D(is3D).withTurnCosts(true).setSegmentSize(segmentSize).build();
        for (FlagEncoder encoder : encodingManager.fetchEdgeEncoders()) {
            TurnCostProvider turnCostProvider = forEdgeBasedTraversal
                    ? new DefaultTurnCostProvider(encoder, graph.getTurnCostStorage())
                    : TurnCostProvider.NO_TURN_COST_PROVIDER;
            FastestWeighting weighting = new FastestWeighting(encoder, turnCostProvider);
            graph.addCHGraph(new CHConfig("p_" + encoder.toString(), weighting, forEdgeBasedTraversal));
        }
        return graph;
    }

    @Test
    public void testCannotBeLoadedWithNormalGraphHopperStorageClass() {
        graph = newGHStorage(false, false);
        graph.flush();
        graph.close();

        graph = GraphBuilder.start(encodingManager).setRAM(defaultGraphLoc, true).create();
        try {
            graph.loadExisting();
            fail();
        } catch (Exception ex) {
        }

        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), false);
        assertTrue(graph.loadExisting());
        // empty graph still has invalid bounds
        assertEquals(graph.getBounds(), BBox.createInverse(false));
    }

    @Test
    public void testPrios() {
        graph = createGHStorage();
        CHStorage g = getCHStore(graph);
        CHStorageBuilder b = new CHStorageBuilder(g);
        graph.getNodeAccess().ensureNode(30);
        graph.freeze();

        assertEquals(0, g.getLevel(g.toNodePointer(10)));

        b.setLevel(10, 100);
        assertEquals(100, g.getLevel(g.toNodePointer(10)));

        b.setLevel(30, 100);
        assertEquals(100, g.getLevel(g.toNodePointer(30)));
    }

    @Test
    public void testShortcutConnection() {
        //   4 ------ 1 > 0
        //            ^ \
        //            3  2
        graph = createGHStorage();
        EdgeExplorer baseCarOutExplorer = graph.createEdgeExplorer(carOutFilter);
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(4, 1).setDistance(30));
        graph.freeze();

        CHStorage store = getCHStore(graph);
        CHStorageBuilder chBuilder = new CHStorageBuilder(store);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutNodeBased(0, 1, PrepareEncoder.getScBwdDir(), 0, 12, 13);
        chBuilder.addShortcutNodeBased(1, 2, PrepareEncoder.getScDirMask(), 0, 10, 11);
        chBuilder.addShortcutNodeBased(1, 3, PrepareEncoder.getScBwdDir(), 0, 14, 15);

        RoutingCHGraph lg = getRoutingCHGraph(graph);
        RoutingCHEdgeExplorer chOutExplorer = lg.createOutEdgeExplorer();
        RoutingCHEdgeExplorer chInExplorer = lg.createInEdgeExplorer();
        // shortcuts are only visible from the lower level node, for example we do not see node 1 from node 2, or node
        // 0 from node 1
        assertEquals(0, GHUtility.count(chOutExplorer.setBaseNode(2)));
        assertEquals(0, GHUtility.count(chInExplorer.setBaseNode(2)));

        assertEquals(2, GHUtility.count(chOutExplorer.setBaseNode(1)));
        assertEquals(3, GHUtility.count(chInExplorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(2, 4), GHUtility.getNeighbors(chOutExplorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(4), GHUtility.getNeighbors(baseCarOutExplorer.setBaseNode(1)));

        assertEquals(0, GHUtility.count(chOutExplorer.setBaseNode(3)));
        assertEquals(0, GHUtility.count(chInExplorer.setBaseNode(3)));

        assertEquals(0, GHUtility.count(chOutExplorer.setBaseNode(0)));
        assertEquals(1, GHUtility.count(chInExplorer.setBaseNode(0)));
    }

    @Test
    public void testGetWeight() {
        graph = createGHStorage();
        EdgeIteratorState edge1 = graph.edge(0, 1);
        EdgeIteratorState edge2 = graph.edge(1, 2);
        graph.freeze();
        RoutingCHGraph g = getRoutingCHGraph(graph);
        assertFalse(g.getEdgeIteratorState(edge1.getEdge(), Integer.MIN_VALUE).isShortcut());
        assertFalse(g.getEdgeIteratorState(edge2.getEdge(), Integer.MIN_VALUE).isShortcut());

        CHStorage s = getCHStore(graph);
        CHStorageBuilder chBuilder = new CHStorageBuilder(s);
        chBuilder.setIdentityLevels();
        // only remove edges
        int flags = PrepareEncoder.getScDirMask();
        chBuilder.addShortcutNodeBased(0, 1, flags, 5, NO_EDGE, NO_EDGE);
        RoutingCHEdgeIteratorState sc1 = g.getEdgeIteratorState(2, 1);
        assertEquals(0, sc1.getBaseNode());
        assertEquals(1, sc1.getAdjNode());
        assertEquals(5, sc1.getWeight(false), 1e-3);
        assertTrue(sc1.isShortcut());
    }

    @Test
    public void testGetWeightIfAdvancedEncoder() {
        FlagEncoder customEncoder = new Bike2WeightFlagEncoder();
        EncodingManager em = EncodingManager.create(customEncoder);
        FastestWeighting weighting = new FastestWeighting(customEncoder);
        GraphHopperStorage ghStorage = new GraphBuilder(em).setCHConfigs(CHConfig.nodeBased("p1", weighting)).create();
        ghStorage.edge(0, 3);
        ghStorage.freeze();

        CHStorage chStore = ghStorage.getCHStore();
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();
        int sc1 = ghStorage.getEdges() + chBuilder.addShortcutNodeBased(0, 1, PrepareEncoder.getScFwdDir(), 100.123, NO_EDGE, NO_EDGE);
        RoutingCHGraph lg = ghStorage.getRoutingCHGraph();
        assertEquals(1, lg.getEdgeIteratorState(sc1, 1).getAdjNode());
        assertEquals(0, lg.getEdgeIteratorState(sc1, 1).getBaseNode());
        assertEquals(100.123, lg.getEdgeIteratorState(sc1, 1).getWeight(false), 1e-3);
        assertEquals(100.123, lg.getEdgeIteratorState(sc1, 0).getWeight(false), 1e-3);

        int sc2 = ghStorage.getEdges() + chBuilder.addShortcutNodeBased(2, 3, PrepareEncoder.getScDirMask(), 1.011011, NO_EDGE, NO_EDGE);
        assertEquals(3, lg.getEdgeIteratorState(sc2, 3).getAdjNode());
        assertEquals(2, lg.getEdgeIteratorState(sc2, 3).getBaseNode());
        assertEquals(1.011011, lg.getEdgeIteratorState(sc2, 2).getWeight(false), 1e-3);
        assertEquals(1.011011, lg.getEdgeIteratorState(sc2, 3).getWeight(false), 1e-3);
    }

    @Test
    public void weightExact() {
        graph = createGHStorage();
        CHStorage chStore = getCHStore(graph);
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(1, 2).setDistance(1));
        graph.freeze();
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();

        // we just make up some weights, they do not really have to be related to our previous edges.
        // 1.004+1.006 = 2.09999999999. we make sure this does not become 2.09 instead of 2.10 (due to truncation)
        double x1 = 1.004;
        double x2 = 1.006;
        RoutingCHGraph rg = getRoutingCHGraph(graph);
        chBuilder.addShortcutNodeBased(0, 2, PrepareEncoder.getScFwdDir(), x1 + x2, 0, 1);
        RoutingCHEdgeIteratorState sc = rg.getEdgeIteratorState(2, 2);
        assertEquals(2.01, sc.getWeight(false), 1.e-6);
    }

    @Test
    @Override
    public void testSave_and_Freeze() throws IOException {
        // belongs to each other
        super.testSave_and_Freeze();
        graph.close();

        // test freeze and shortcut creation & loading
        graph = newGHStorage(true, false);
        graph.edge(1, 0);
        graph.edge(8, 9);
        graph.freeze();
        CHStorage store = getCHStore(graph);
        CHStorageBuilder chBuilder = new CHStorageBuilder(store);
        chBuilder.setIdentityLevels();

        RoutingCHGraph chGraph = getRoutingCHGraph(graph);

        assertEquals(1, GHUtility.count(graph.createEdgeExplorer().setBaseNode(1)));
        // routing ch graph does not see edges without access
        assertEquals(0, GHUtility.count(chGraph.createInEdgeExplorer().setBaseNode(1)));

        chBuilder.addShortcutNodeBased(2, 3, PrepareEncoder.getScDirMask(), 10, NO_EDGE, NO_EDGE);

        // should be identical to results before we added shortcut
        assertEquals(1, GHUtility.count(graph.createEdgeExplorer().setBaseNode(1)));
        assertEquals(0, GHUtility.count(chGraph.createOutEdgeExplorer().setBaseNode(1)));

        // base graph does not see shortcut
        assertEquals(0, GHUtility.count(graph.createEdgeExplorer().setBaseNode(2)));
        assertEquals(1, GHUtility.count(chGraph.createOutEdgeExplorer().setBaseNode(2)));

        graph.flush();
        graph.close();

        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), true);
        chGraph = getRoutingCHGraph(graph);
        assertTrue(graph.loadExisting());
        assertTrue(graph.isFrozen());

        assertEquals(10, chGraph.getNodes());
        assertEquals(2, graph.getEdges());
        assertEquals(3, chGraph.getEdges());
        assertEquals(1, GHUtility.count(chGraph.createOutEdgeExplorer().setBaseNode(2)));
    }

    @Test
    public void testSimpleShortcutCreationAndTraversal() {
        graph = createGHStorage();
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(1, 3).setDistance(10));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(3, 4).setDistance(10));
        graph.freeze();

        CHStorage store = getCHStore(graph);
        CHStorageBuilder chBuilder = new CHStorageBuilder(store);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutNodeBased(1, 4, PrepareEncoder.getScFwdDir(), 3, NO_EDGE, NO_EDGE);

        RoutingCHGraph lg = getRoutingCHGraph(graph);
        RoutingCHEdgeExplorer exp = lg.createOutEdgeExplorer();
        // iteration should result in same nodes even if reusing the iterator
        assertEquals(GHUtility.asSet(3, 4), GHUtility.getNeighbors(exp.setBaseNode(1)));
    }

    @Test
    public void testAddShortcutSkippedEdgesWriteRead() {
        graph = createGHStorage();
        final EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(1, 3).setDistance(10));
        final EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(3, 4).setDistance(10));
        graph.freeze();

        CHStorage chStore = getCHStore(graph);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutNodeBased(1, 4, PrepareEncoder.getScDirMask(), 10, NO_EDGE, NO_EDGE);

        chStore.setSkippedEdges(chStore.toShortcutPointer(0), edge1.getEdge(), edge2.getEdge());
        assertEquals(edge1.getEdge(), chStore.getSkippedEdge1(chStore.toShortcutPointer(0)));
        assertEquals(edge2.getEdge(), chStore.getSkippedEdge2(chStore.toShortcutPointer(0)));
    }

    @Test
    public void testSkippedEdges() {
        graph = createGHStorage();
        final EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(1, 3).setDistance(10));
        final EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(3, 4).setDistance(10));
        graph.freeze();

        CHStorage chStore = getCHStore(graph);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutNodeBased(1, 4, PrepareEncoder.getScDirMask(), 10, edge1.getEdge(), edge2.getEdge());
        assertEquals(edge1.getEdge(), chStore.getSkippedEdge1(chStore.toShortcutPointer(0)));
        assertEquals(edge2.getEdge(), chStore.getSkippedEdge2(chStore.toShortcutPointer(0)));
    }

    @Test
    public void testAddShortcut_edgeBased_throwsIfNotConfiguredForEdgeBased() {
        graph = newGHStorage(false, false);
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(1, 2).setDistance(1));
        graph.freeze();
        CHStorageBuilder chBuilder = new CHStorageBuilder(getCHStore(graph));
        assertThrows(IllegalArgumentException.class, () -> chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScFwdDir(), 10, 0, 1, 0, 1));
    }

    @Test
    public void testAddShortcut_edgeBased() {
        // 0 -> 1 -> 2
        graph = newGHStorage(false, true);
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(1, 2).setDistance(3));
        graph.freeze();
        CHStorage chStore = getCHStore(this.graph);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScFwdDir(), 10, 0, 1, 0, 1);
        assertEquals(0, chStore.getOrigEdgeFirst(chStore.toShortcutPointer(0)));
        assertEquals(1, chStore.getOrigEdgeLast(chStore.toShortcutPointer(0)));
    }

    @Test
    public void outOfBounds() {
        graph = newGHStorage(false, true);
        graph.freeze();
        RoutingCHGraph lg = getRoutingCHGraph(graph);
        assertThrows(IllegalArgumentException.class, () -> lg.getEdgeIteratorState(0, Integer.MIN_VALUE));
    }

    @Test
    public void testGetEdgeIterator() {
        graph = newGHStorage(false, true);
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(1, 2).setDistance(1));
        graph.freeze();
        CHStorage store = getCHStore(graph);
        CHStorageBuilder chBuilder = new CHStorageBuilder(store);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScFwdDir(), 10, 0, 1, 0, 1);

        RoutingCHGraph lg = getRoutingCHGraph(graph);

        RoutingCHEdgeIteratorState sc02 = lg.getEdgeIteratorState(2, 2);
        assertNotNull(sc02);
        assertEquals(0, sc02.getBaseNode());
        assertEquals(2, sc02.getAdjNode());
        assertEquals(2, sc02.getEdge());
        assertEquals(0, sc02.getSkippedEdge1());
        assertEquals(1, sc02.getSkippedEdge2());
        assertEquals(0, sc02.getOrigEdgeFirst());
        assertEquals(1, sc02.getOrigEdgeLast());

        RoutingCHEdgeIteratorState sc20 = lg.getEdgeIteratorState(2, 0);
        assertNotNull(sc20);
        assertEquals(2, sc20.getBaseNode());
        assertEquals(0, sc20.getAdjNode());
        assertEquals(2, sc20.getEdge());
        // note these are not stateful! i.e. even though we are looking at the edge 2->0 the first skipped/orig edge
        // is still edge 0 and the second skipped/last orig edge is edge 1
        assertEquals(0, sc20.getSkippedEdge1());
        assertEquals(1, sc20.getSkippedEdge2());
        assertEquals(0, sc20.getOrigEdgeFirst());
        assertEquals(1, sc20.getOrigEdgeLast());
    }

    @Test
    public void testLoadingWithWrongWeighting_node_throws() {
        assertThrows(IllegalStateException.class, () -> testLoadingWithWrongWeighting_throws(false));
    }

    @Test
    public void testLoadingWithWrongWeighting_edge_throws() {
        assertThrows(IllegalStateException.class, () -> testLoadingWithWrongWeighting_throws(true));
    }

    private void testLoadingWithWrongWeighting_throws(boolean edgeBased) {
        String edgeOrNode = edgeBased ? "edge" : "node";
        // we start with one profile
        GraphHopperStorage ghStorage = createStorageWithWeightings("p1|car|fastest|" + edgeOrNode);
        ghStorage.create(defaultSize);
        ghStorage.flush();

        // but then configure another profile and try to load the graph from disk -> error
        GraphHopperStorage newGHStorage = createStorageWithWeightings("p2|car|shortest|" + edgeOrNode);
        newGHStorage.loadExisting();
    }

    @Test
    public void testLoadingWithExtraWeighting_throws() {
        // we start with one profile
        GraphHopperStorage ghStorage = createStorageWithWeightings("p|car|fastest|node");
        ghStorage.create(defaultSize);
        ghStorage.flush();

        // but then add an additional profile and try to load the graph from disk -> error
        GraphHopperStorage newGHStorage = createStorageWithWeightings("p|car|fastest|node", "q|car|shortest|node");
        assertThrows(IllegalStateException.class, newGHStorage::loadExisting);
    }

    @Test
    public void testLoadingWithLessWeightings_node_works() {
        testLoadingWithLessWeightings_works(false);
    }

    @Test
    public void testLoadingWithLessWeightings_edge_works() {
        testLoadingWithLessWeightings_works(true);
    }

    private void testLoadingWithLessWeightings_works(boolean edgeBased) {
        String edgeOrNode = edgeBased ? "edge" : "node";
        // we start with a gh storage with two ch weightings and flush it to disk
        GraphHopperStorage originalStorage = createStorageWithWeightings(
                "p1|car|fastest|" + edgeOrNode,
                "p2|car|shortest|" + edgeOrNode
        );
        originalStorage.create(defaultSize);
        originalStorage.flush();

        // now we create a new storage but only use one of the weightings, which should be ok
        GraphHopperStorage smallStorage = createStorageWithWeightings("p1|car|fastest|" + edgeOrNode);
        smallStorage.loadExisting();
        assertEquals(edgeBased ? 0 : 1, smallStorage.getCHConfigs(false).size());
        assertEquals(edgeBased ? 1 : 0, smallStorage.getCHConfigs(true).size());
        smallStorage.flush();

        // now we create a new storage without any ch weightings, which should also be ok
        GraphHopperStorage smallerStorage = createStorageWithWeightings();
        smallerStorage.loadExisting();
        assertEquals(0, smallerStorage.getCHConfigs(false).size());
        assertEquals(0, smallerStorage.getCHConfigs(true).size());
        smallerStorage.flush();

        // now we create yet another storage that uses both weightings again, which still works
        GraphHopperStorage fullStorage = createStorageWithWeightings(
                "p1|car|fastest|" + edgeOrNode,
                "p2|car|shortest|" + edgeOrNode
        );
        fullStorage.loadExisting();
        assertEquals(edgeBased ? 0 : 2, fullStorage.getCHConfigs(false).size());
        assertEquals(edgeBased ? 2 : 0, fullStorage.getCHConfigs(true).size());
        fullStorage.flush();
    }

    @Test
    public void testLoadingWithLessWeightings_nodeAndEdge_works() {
        // we start with a gh storage with two node-based and one edge-based ch profiles and flush it to disk
        GraphHopperStorage originalStorage = createStorageWithWeightings(
                "p1|car|fastest|node",
                "p2|car|shortest|node",
                "p3|car|shortest|edge");
        originalStorage.create(defaultSize);
        originalStorage.flush();

        // now we create a new storage but only use the edge profile, which should be ok
        GraphHopperStorage edgeStorage = createStorageWithWeightings(
                "p3|car|shortest|edge"
        );
        edgeStorage.loadExisting();
        assertEquals(0, edgeStorage.getCHConfigs(false).size());
        assertEquals(1, edgeStorage.getCHConfigs(true).size());
        edgeStorage.flush();

        // now we create yet another storage that uses one of the node and the edge profiles, which still works
        GraphHopperStorage mixedStorage = createStorageWithWeightings(
                "p1|car|fastest|node",
                "p3|car|shortest|edge"
        );
        mixedStorage.loadExisting();
        assertEquals(1, mixedStorage.getCHConfigs(false).size());
        assertEquals(1, mixedStorage.getCHConfigs(true).size());
        mixedStorage.flush();
    }

    @Test
    public void testCHProfilesWithDifferentNames() {
        FastestWeighting weighting = new FastestWeighting(carEncoder);
        // creating multiple profiles with the same name is an error
        {
            try {
                new GraphBuilder(encodingManager)
                        .setCHConfigs(
                                CHConfig.nodeBased("a", weighting),
                                CHConfig.nodeBased("b", weighting),
                                CHConfig.nodeBased("a", weighting)
                        )
                        .create();
                fail("creating multiple profiles with the same name should be an error");
            } catch (Exception e) {
                assertTrue(e.getMessage().contains("For the given CH profile a CHStorage already exists"), "unexpected error: " + e.getMessage());
            }
        }
        // ... but using multiple profiles with different names is fine even when their properties/weighting are the same
        {
            GraphHopperStorage storage = new GraphBuilder(encodingManager)
                    .setCHConfigs(
                            CHConfig.nodeBased("a", weighting),
                            CHConfig.nodeBased("b", weighting),
                            CHConfig.nodeBased("c", weighting)
                    )
                    .create();
            assertSame(storage.getRoutingCHGraph("a"), storage.getRoutingCHGraph("a"));
            assertNotNull(storage.getRoutingCHGraph("a"));
            assertNotNull(storage.getRoutingCHGraph("b"));
            assertNotNull(storage.getRoutingCHGraph("c"));
            assertNotSame(storage.getRoutingCHGraph("a"), storage.getRoutingCHGraph("b"));
            assertNotSame(storage.getRoutingCHGraph("b"), storage.getRoutingCHGraph("c"));
            assertNotSame(storage.getRoutingCHGraph("a"), storage.getRoutingCHGraph("c"));
        }
    }

    private GraphHopperStorage createStorageWithWeightings(String... profileStrings) {
        return new GraphBuilder(encodingManager)
                .setCHConfigStrings(profileStrings)
                .setDir(new GHDirectory(defaultGraphLoc, DAType.RAM_STORE))
                .withTurnCosts(true)
                .build();
    }
}
