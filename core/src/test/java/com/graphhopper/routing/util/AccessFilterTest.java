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
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CHEdgeExplorer;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccessFilterTest {
    private final CarFlagEncoder encoder = new CarFlagEncoder();
    private final EncodingManager encodingManager = EncodingManager.create(encoder);
    private final GraphHopperStorage graph = new GraphBuilder(encodingManager)
            .withTurnCosts(true)
            .setCHConfigStrings("profile|car|shortest|edge")
            .create();
    private final CHGraph chGraph = graph.getCHGraph();

    @Test
    public void testAccept_fwdLoopShortcut_acceptedByInExplorer() {
        // 0-1
        //  \|
        //   2
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 0).setDistance(3));
        graph.freeze();
        // add loop shortcut in 'fwd' direction
        addShortcut(chGraph, 0, 0, true, 0, 2);
        CHEdgeExplorer outExplorer = chGraph.createEdgeExplorer(AccessFilter.outEdges(encoder.getAccessEnc()));
        CHEdgeExplorer inExplorer = chGraph.createEdgeExplorer(AccessFilter.inEdges(encoder.getAccessEnc()));

        IntSet inEdges = new IntHashSet();
        IntSet outEdges = new IntHashSet();
        CHEdgeIterator outIter = outExplorer.setBaseNode(0);
        while (outIter.next()) {
            outEdges.add(outIter.getEdge());
        }
        CHEdgeIterator inIter = inExplorer.setBaseNode(0);
        while (inIter.next()) {
            inEdges.add(inIter.getEdge());
        }
        // the loop should be accepted by in- and outExplorers
        assertEquals(IntHashSet.from(0, 3), outEdges, "Wrong outgoing edges");
        assertEquals(IntHashSet.from(2, 3), inEdges, "Wrong incoming edges");
    }

    private void addShortcut(CHGraph chGraph, int from, int to, boolean fwd, int skip1, int skip2) {
        int accessFlags = fwd ? PrepareEncoder.getScFwdDir() : PrepareEncoder.getScBwdDir();
        chGraph.shortcut(from, to, accessFlags, 5, skip1, skip2);
    }

}