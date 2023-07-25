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

import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static org.junit.jupiter.api.Assertions.*;

public class RoutingCHGraphImplTest {
    @Test
    public void testBaseAndCHEdges() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        graph.edge(1, 0);
        graph.edge(8, 9);
        graph.freeze();

        CHConfig chConfig = CHConfig.nodeBased("p", CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em));
        CHStorage store = CHStorage.fromGraph(graph, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(store);
        chBuilder.setIdentityLevels();
        RoutingCHGraph chGraph = RoutingCHGraphImpl.fromGraph(graph, store, chConfig);

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

        assertEquals(10, chGraph.getNodes());
        assertEquals(2, graph.getEdges());
        assertEquals(3, chGraph.getEdges());
        assertEquals(1, GHUtility.count(chGraph.createOutEdgeExplorer().setBaseNode(2)));
    }

    @Test
    void testShortcutConnection() {
        //   4 ------ 1 > 0
        //            ^ \
        //            3  2
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeExplorer baseCarOutExplorer = graph.createEdgeExplorer(AccessFilter.outEdges(accessEnc));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 1).setDistance(30));
        graph.freeze();

        CHConfig chConfig = CHConfig.nodeBased("ch", CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em));
        CHStorage store = CHStorage.fromGraph(graph, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(store);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutNodeBased(0, 1, PrepareEncoder.getScBwdDir(), 10, 12, 13);
        chBuilder.addShortcutNodeBased(1, 2, PrepareEncoder.getScDirMask(), 10, 10, 11);
        chBuilder.addShortcutNodeBased(1, 3, PrepareEncoder.getScBwdDir(), 10, 14, 15);

        RoutingCHGraph lg = RoutingCHGraphImpl.fromGraph(graph, store, chConfig);
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
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge1 = graph.edge(0, 1);
        EdgeIteratorState edge2 = graph.edge(1, 2);
        graph.freeze();

        CHConfig chConfig = CHConfig.nodeBased("ch", CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em));
        CHStorage store = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph g = RoutingCHGraphImpl.fromGraph(graph, store, chConfig);
        assertFalse(g.getEdgeIteratorState(edge1.getEdge(), Integer.MIN_VALUE).isShortcut());
        assertFalse(g.getEdgeIteratorState(edge2.getEdge(), Integer.MIN_VALUE).isShortcut());

        CHStorageBuilder chBuilder = new CHStorageBuilder(store);
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
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 4, 2, true);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph ghStorage = new BaseGraph.Builder(em).create();
        ghStorage.edge(0, 3);
        ghStorage.freeze();

        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em);
        CHConfig chConfig = CHConfig.nodeBased("p1", weighting);
        CHStorage chStore = CHStorage.fromGraph(ghStorage, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();
        int sc1 = ghStorage.getEdges() + chBuilder.addShortcutNodeBased(0, 1, PrepareEncoder.getScFwdDir(), 100.123, NO_EDGE, NO_EDGE);
        RoutingCHGraph lg = RoutingCHGraphImpl.fromGraph(ghStorage, chStore, chConfig);
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
    public void testWeightExact() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        graph.freeze();

        CHConfig chConfig = CHConfig.nodeBased("ch", CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em));
        CHStorage store = CHStorage.fromGraph(graph, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(store);
        chBuilder.setIdentityLevels();

        // we just make up some weights, they do not really have to be related to our previous edges.
        // 1.004+1.006 = 2.09999999999. we make sure this does not become 2.09 instead of 2.10 (due to truncation)
        double x1 = 1.004;
        double x2 = 1.006;
        RoutingCHGraph rg = RoutingCHGraphImpl.fromGraph(graph, store, chConfig);
        chBuilder.addShortcutNodeBased(0, 2, PrepareEncoder.getScFwdDir(), x1 + x2, 0, 1);
        RoutingCHEdgeIteratorState sc = rg.getEdgeIteratorState(2, 2);
        assertEquals(2.01, sc.getWeight(false), 1.e-6);
    }

    @Test
    public void testSimpleShortcutCreationAndTraversal() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 3).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(10));
        graph.freeze();

        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em);
        CHConfig chConfig = CHConfig.nodeBased("p1", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);

        chBuilder.setIdentityLevels();
        chBuilder.addShortcutNodeBased(1, 4, PrepareEncoder.getScFwdDir(), 3, NO_EDGE, NO_EDGE);

        RoutingCHGraph lg = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);
        RoutingCHEdgeExplorer exp = lg.createOutEdgeExplorer();
        // iteration should result in same nodes even if reusing the iterator
        assertEquals(GHUtility.asSet(3, 4), GHUtility.getNeighbors(exp.setBaseNode(1)));
    }

    @Test
    public void testAddShortcutSkippedEdgesWriteRead() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        final EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 3).setDistance(10));
        final EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(10));
        graph.freeze();

        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em);
        CHConfig chConfig = CHConfig.nodeBased("p1", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutNodeBased(1, 4, PrepareEncoder.getScDirMask(), 10, NO_EDGE, NO_EDGE);

        chStore.setSkippedEdges(chStore.toShortcutPointer(0), edge1.getEdge(), edge2.getEdge());
        assertEquals(edge1.getEdge(), chStore.getSkippedEdge1(chStore.toShortcutPointer(0)));
        assertEquals(edge2.getEdge(), chStore.getSkippedEdge2(chStore.toShortcutPointer(0)));
    }

    @Test
    public void testSkippedEdges() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        final EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 3).setDistance(10));
        final EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(10));
        graph.freeze();

        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em);
        CHConfig chConfig = CHConfig.nodeBased("p1", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutNodeBased(1, 4, PrepareEncoder.getScDirMask(), 10, edge1.getEdge(), edge2.getEdge());
        assertEquals(edge1.getEdge(), chStore.getSkippedEdge1(chStore.toShortcutPointer(0)));
        assertEquals(edge2.getEdge(), chStore.getSkippedEdge2(chStore.toShortcutPointer(0)));
    }

    @Test
    public void testAddShortcut_edgeBased_throwsIfNotConfiguredForEdgeBased() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();

        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        graph.freeze();

        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em);
        CHConfig chConfig = CHConfig.nodeBased("p1", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        assertThrows(IllegalArgumentException.class, () -> chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScFwdDir(), 10, 0, 1, 0, 2));
    }

    @Test
    public void testAddShortcut_edgeBased() {
        // 0 -> 1 -> 2
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).set3D(true).create();
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(3));
        graph.freeze();

        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em);
        CHConfig chConfig = CHConfig.edgeBased("p1", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);

        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScFwdDir(), 10, 0, 1, 0, 2);
        assertEquals(0, chStore.getOrigEdgeKeyFirst(chStore.toShortcutPointer(0)));
        assertEquals(2, chStore.getOrigEdgeKeyLast(chStore.toShortcutPointer(0)));
    }

    @Test
    public void outOfBounds() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).set3D(true).create();
        graph.freeze();
        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em);
        CHConfig chConfig = CHConfig.nodeBased("p1", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph lg = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);
        assertThrows(IllegalArgumentException.class, () -> lg.getEdgeIteratorState(0, Integer.MIN_VALUE));
    }

    @Test
    public void testGetEdgeIterator() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).set3D(true).create();
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        graph.freeze();

        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, em);
        CHConfig chConfig = CHConfig.edgeBased("p1", weighting);
        CHStorage store = CHStorage.fromGraph(graph, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(store);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScFwdDir(), 10, 0, 1, 0, 2);

        RoutingCHGraph lg = RoutingCHGraphImpl.fromGraph(graph, store, chConfig);

        RoutingCHEdgeIteratorState sc02 = lg.getEdgeIteratorState(2, 2);
        assertNotNull(sc02);
        assertEquals(0, sc02.getBaseNode());
        assertEquals(2, sc02.getAdjNode());
        assertEquals(2, sc02.getEdge());
        assertEquals(0, sc02.getSkippedEdge1());
        assertEquals(1, sc02.getSkippedEdge2());
        assertEquals(0, sc02.getOrigEdgeKeyFirst());
        assertEquals(2, sc02.getOrigEdgeKeyLast());

        RoutingCHEdgeIteratorState sc20 = lg.getEdgeIteratorState(2, 0);
        assertNotNull(sc20);
        assertEquals(2, sc20.getBaseNode());
        assertEquals(0, sc20.getAdjNode());
        assertEquals(2, sc20.getEdge());
        // note these are not stateful! i.e. even though we are looking at the edge 2->0 the first skipped/orig edge
        // is still edge 0 and the second skipped/last orig edge is edge 1
        assertEquals(0, sc20.getSkippedEdge1());
        assertEquals(1, sc20.getSkippedEdge2());
        assertEquals(0, sc20.getOrigEdgeKeyFirst());
        assertEquals(2, sc20.getOrigEdgeKeyLast());
    }
}
