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
package com.graphhopper.routing.util;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntSet;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.storage.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccessFilterTest {
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
    private final DecimalEncodedValue turnCostEnc = TurnCost.create("car", 1);
    private final EncodingManager encodingManager = EncodingManager.start().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
    private final BaseGraph graph = new BaseGraph.Builder(encodingManager)
            .withTurnCosts(true)
            .create();

    @Test
    public void testAccept_fwdLoopShortcut_acceptedByInExplorer() {
        // 0-1
        //  \|
        //   2
        graph.edge(0, 1).setDistance(100).set(speedEnc, 60, 0);
        graph.edge(1, 2).setDistance(200).set(speedEnc, 60, 0);
        graph.edge(2, 0).setDistance(300).set(speedEnc, 60, 0);
        graph.freeze();
        // add loop shortcut in 'fwd' direction
        CHConfig chConfig = CHConfig.edgeBased("profile", new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), 50));
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutEdgeBased(0, 0, PrepareEncoder.getScFwdDir(), 5, 0, 2, 0, 5);
        RoutingCHGraph chGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);
        RoutingCHEdgeExplorer outExplorer = chGraph.createOutEdgeExplorer();
        RoutingCHEdgeExplorer inExplorer = chGraph.createInEdgeExplorer();

        IntSet inEdges = new IntHashSet();
        IntSet outEdges = new IntHashSet();
        RoutingCHEdgeIterator outIter = outExplorer.setBaseNode(0);
        while (outIter.next()) {
            outEdges.add(outIter.getEdge());
        }
        RoutingCHEdgeIterator inIter = inExplorer.setBaseNode(0);
        while (inIter.next()) {
            inEdges.add(inIter.getEdge());
        }
        // the loop should be accepted by in- and outExplorers
        assertEquals(IntHashSet.from(0, 3), outEdges, "Wrong outgoing edges");
        assertEquals(IntHashSet.from(2, 3), inEdges, "Wrong incoming edges");
    }

}
