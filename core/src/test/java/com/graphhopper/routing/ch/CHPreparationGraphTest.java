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

package com.graphhopper.routing.ch;

import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * So far there aren't many tests but the graph should be very easy to test as it has basically no dependencies and
 * if a problem arises it should be easy to write a test here.
 */
class CHPreparationGraphTest {

    @Test
    void basic() {
        // 0->4<-2
        // |
        // 3<-1
        CHPreparationGraph pg = CHPreparationGraph.nodeBased(5, 10);
        double inf = Double.POSITIVE_INFINITY;
        pg.addEdge(0, 4, 3, 10, inf);
        pg.addEdge(4, 2, 0, inf, 5);
        pg.addEdge(0, 3, 1, 6, 6);
        pg.addEdge(1, 3, 2, 9, inf);
        pg.prepareForContraction();

        assertEquals(3, pg.getDegree(0));
        assertEquals(2, pg.getDegree(4));

        pg.addShortcut(3, 4, 1, 3, 1, 3, 16, 2);
        pg.disconnect(0);
        PrepareGraphEdgeIterator iter = pg.createOutEdgeExplorer().setBaseNode(3);
        StringBuilder res = new StringBuilder();
        while (iter.next()) {
            res.append(iter).append(",");
        }
        assertEquals("3-4 16.0,", res.toString());
    }

    @Test
    void useLargeEdgeId() {
        CHPreparationGraph.OrigGraph.Builder builder = new CHPreparationGraph.OrigGraph.Builder();
        int largeEdgeID = Integer.MAX_VALUE >> 1;
        assertEquals(1_073_741_823, largeEdgeID);
        // 0->1
        builder.addEdge(0, 1, largeEdgeID, true, false);
        CHPreparationGraph.OrigGraph g = builder.build();
        PrepareGraphOrigEdgeIterator iter = g.createOutOrigEdgeExplorer().setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(largeEdgeID, GHUtility.getEdgeFromEdgeKey(iter.getOrigEdgeKeyFirst()));
        iter = g.createInOrigEdgeExplorer().setBaseNode(0);
        assertFalse(iter.next());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                new CHPreparationGraph.OrigGraph.Builder().addEdge(0, 1, largeEdgeID + 1, true, false)
        );
        assertTrue(e.getMessage().contains("Maximum node or edge key exceeded: -2147483648, max: 2147483647"), e.getMessage());
    }

    /**
     * Pins subtle numeric semantics of the CH preparation data structures that no other test covers
     * directly, see docs/pinned-behavior.md. The germany-map shortcut counts depend on these.
     */
    @Test
    public void baseEdgeWeightsAreNarrowedToFloatPrecision() {
        CHPreparationGraph pg = CHPreparationGraph.nodeBased(2, 1);
        pg.addEdge(0, 1, 0, 0.3, 0.7);
        pg.prepareForContraction();
        PrepareGraphEdgeIterator outIter = pg.createOutEdgeExplorer().setBaseNode(0);
        assertTrue(outIter.next());
        // the prepare graph deliberately stores base edge weights as float (to save memory);
        // reading yields the float widened back to double, NOT the original double. this
        // narrowing determines witness-search tie-breaks and thus the exact shortcut counts.
        assertEquals(0.30000001192092896, outIter.getWeight());
        assertNotEquals(0.3, outIter.getWeight());
        assertFalse(outIter.next());

        PrepareGraphEdgeIterator inIter = pg.createInEdgeExplorer().setBaseNode(0);
        assertTrue(inIter.next());
        assertEquals(0.699999988079071, inIter.getWeight());
        assertFalse(inIter.next());
    }

    @Test
    public void shortcutWeightsKeepDoublePrecision() {
        CHPreparationGraph pg = CHPreparationGraph.nodeBased(3, 2);
        pg.addEdge(0, 1, 0, 1, 1);
        pg.addEdge(1, 2, 1, 1, 1);
        pg.prepareForContraction();
        // in contrast to base edges, shortcut weights are stored as double and must stay exact
        pg.addShortcut(0, 2, -1, -1, 0, 1, 0.3, 2);
        PrepareGraphEdgeIterator iter = pg.createOutEdgeExplorer().setBaseNode(0);
        double shortcutWeight = Double.NaN;
        while (iter.next())
            if (iter.isShortcut())
                shortcutWeight = iter.getWeight();
        assertEquals(0.3, shortcutWeight);
    }

    @Test
    public void prepareCHEntryComparisonReturnsZeroForTies() {
        // like SPTEntry, PrepareCHEntry does no tie-breaking (and no NaN/-0 handling) on equal
        // weights: the PriorityQueue order for ties decides which bridge path wins
        PrepareCHEntry a = new PrepareCHEntry(0, 0, 0, 0, 3.5, 1);
        PrepareCHEntry b = new PrepareCHEntry(9, 9, 9, 9, 3.5, 9);
        assertEquals(0, a.compareTo(b));
        assertEquals(0, b.compareTo(a));
        assertEquals("0 (0) weight: 3.5, incEdgeKey: 0", a.toString());
    }

    @Test
    public void shortcutDirectionBitsAreStorageFormat() {
        // these bits are written to the CH storage (see CHStorage.shortcutEdgeBased/NodeBased)
        // and must never change
        assertEquals(0x1, PrepareEncoder.getScFwdDir());
        assertEquals(0x2, PrepareEncoder.getScBwdDir());
        assertEquals(0x3, PrepareEncoder.getScDirMask());
    }
}
