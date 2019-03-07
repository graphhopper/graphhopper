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
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CHEdgeExplorer;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.CHEdgeIteratorState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultEdgeFilterTest {
    private final CarFlagEncoder encoder = new CarFlagEncoder();
    private final EncodingManager encodingManager = EncodingManager.create(encoder);
    private final Weighting weighting = new ShortestWeighting(encoder);
    private final GraphHopperStorage graph = new GraphBuilder(encodingManager).setCHGraph(weighting).setEdgeBasedCH(true).create();
    private final CHGraph chGraph = graph.getGraph(CHGraph.class);

    @Test
    public void testAccept_fwdLoopShortcut_acceptedByInExplorer() {
        // 0-1
        //  \|
        //   2
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 2, false);
        graph.edge(2, 0, 3, false);
        graph.freeze();
        // add loop shortcut in 'fwd' direction
        addShortcut(chGraph, 0, 0, true, 0, 2);
        CHEdgeExplorer outExplorer = chGraph.createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder));
        CHEdgeExplorer inExplorer = chGraph.createEdgeExplorer(DefaultEdgeFilter.inEdges(encoder));

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
        assertEquals("Wrong outgoing edges", IntHashSet.from(0, 3), outEdges);
        assertEquals("Wrong incoming edges", IntHashSet.from(2, 3), inEdges);
    }

    private void addShortcut(CHGraph chGraph, int from, int to, boolean fwd, int firstOrigEdge, int lastOrigEdge) {
        CHEdgeIteratorState shortcut = chGraph.shortcut(from, to);
        shortcut.setFlagsAndWeight(fwd ? PrepareEncoder.getScFwdDir() : PrepareEncoder.getScBwdDir(), 0);
        shortcut.setFirstAndLastOrigEdges(firstOrigEdge, lastOrigEdge);
    }

}