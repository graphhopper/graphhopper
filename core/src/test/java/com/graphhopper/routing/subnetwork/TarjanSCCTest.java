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

package com.graphhopper.routing.subnetwork;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarjanSCCTest {
    private final FlagEncoder carFlagEncoder = new CarFlagEncoder();
    private final EncodingManager em = EncodingManager.create(carFlagEncoder);
    private final BooleanEncodedValue accessEnc = carFlagEncoder.getAccessEnc();

    @Test
    public void testFindComponents() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        // big network (has two components actually, because 9->12 is a one-way)
        //    ---
        //  /     \
        // 4 < 1 - 2
        // |   |
        // <-- 8 - 11 - 12 < 9 - 15
        g.edge(1, 2, 1, true);
        g.edge(1, 4, 1, false);
        g.edge(1, 8, 1, true);
        g.edge(2, 4, 1, true);
        g.edge(8, 4, 1, false);
        g.edge(8, 11, 1, true);
        g.edge(12, 11, 1, true);
        g.edge(9, 12, 1, false);
        g.edge(9, 15, 1, true);

        // large network
        // 5 --------
        // |        |
        // 3 - 0 - 13
        //   \ |
        //     7
        g.edge(0, 13, 1, true);
        g.edge(0, 3, 1, true);
        g.edge(0, 7, 1, true);
        g.edge(3, 7, 1, true);
        g.edge(3, 5, 1, true);
        g.edge(13, 5, 1, true);

        // small network
        // 6 - 14 - 10
        g.edge(6, 14, 1, true);
        g.edge(10, 14, 1, true);

        TarjanSCC tarjan = new TarjanSCC(g, accessEnc, false);
        TarjanSCC.ConnectedComponents scc = tarjan.findComponentsRecursive();
        List<IntArrayList> components = scc.getComponents();

        assertEquals(4, components.size());
        assertEquals(IntArrayList.from(13, 5, 3, 7, 0), components.get(0));
        assertEquals(IntArrayList.from(2, 4, 12, 11, 8, 1), components.get(1));
        assertEquals(IntArrayList.from(10, 14, 6), components.get(2));
        assertEquals(IntArrayList.from(15, 9), components.get(3));

        assertEquals(16, scc.getNodes());
        assertEquals(0, scc.getSingleNodeComponents().cardinality());
        assertEquals(components.get(1), scc.getBiggestComponent());
    }

    @Test
    public void test481() {
        // 0->1->3->4->5->6->7
        //  \ |      \<-----/
        //    2
        GraphHopperStorage g = new GraphBuilder(em).create();
        g.edge(0, 1, 1, false);
        g.edge(1, 2, 1, false);
        g.edge(2, 0, 1, false);

        g.edge(1, 3, 1, false);
        g.edge(3, 4, 1, false);

        g.edge(4, 5, 1, false);
        g.edge(5, 6, 1, false);
        g.edge(6, 7, 1, false);
        g.edge(7, 4, 1, false);

        TarjanSCC tarjan = new TarjanSCC(g, accessEnc, false);
        TarjanSCC.ConnectedComponents scc = tarjan.findComponentsRecursive();
        List<IntArrayList> components = scc.getComponents();

        assertEquals(3, scc.getTotalComponents());
        assertEquals(2, components.size());
        assertEquals(IntArrayList.from(2, 1, 0), components.get(1));
        assertEquals(IntArrayList.from(7, 6, 5, 4), components.get(0));

        assertEquals(1, scc.getSingleNodeComponents().cardinality());
        assertTrue(scc.getSingleNodeComponents().get(3));

        assertEquals(8, scc.getNodes());
        assertEquals(components.get(0), scc.getBiggestComponent());

        // exclude single
        scc = new TarjanSCC(g, accessEnc, true).findComponentsRecursive();
        assertTrue(scc.getSingleNodeComponents().isEmpty());
        assertEquals(3, scc.getTotalComponents());
        assertEquals(2, scc.getComponents().size());
        assertEquals(8, scc.getNodes());
    }

    @Test
    public void testTarjan_issue761() {
        GraphHopperStorage g = new GraphBuilder(em).create();
        //     11-10-9
        //     |     |
        // 0-1-2->3->4->5
        //        |     |
        //        6     12
        //        |     |
        //        7     13-14
        //        |       \|
        //        8        15-16

        // oneway main road
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, false);
        g.edge(3, 4, 1, false);
        g.edge(4, 5, 1, false);

        // going south from main road
        g.edge(3, 6, 1, true);
        g.edge(6, 7, 1, true);
        g.edge(7, 8, 1, true);

        // connects the two nodes 2 and 4
        g.edge(4, 9, 1, true);
        g.edge(9, 10, 1, true);
        g.edge(10, 11, 1, true);
        g.edge(11, 2, 1, true);

        // eastern part (only connected by a single directed edge to the rest of the graph)
        g.edge(5, 12, 1, true);
        g.edge(12, 13, 1, true);
        g.edge(13, 14, 1, true);
        g.edge(14, 15, 1, true);
        g.edge(15, 13, 1, true);
        g.edge(15, 16, 1, true);

        FlagEncoder encoder = em.fetchEdgeEncoders().iterator().next();
        TarjanSCC tarjan = new TarjanSCC(g, encoder.getAccessEnc(), false);
        TarjanSCC.ConnectedComponents scc = tarjan.findComponentsRecursive();
        assertEquals(2, scc.getTotalComponents());
        assertTrue(scc.getSingleNodeComponents().isEmpty());
        assertEquals(17, scc.getNodes());
        assertEquals(scc.getComponents().get(1), scc.getBiggestComponent());

        assertEquals(2, scc.getComponents().size());
        assertEquals(IntArrayList.from(14, 16, 15, 13, 12, 5), scc.getComponents().get(0));
        assertEquals(IntArrayList.from(8, 7, 6, 3, 4, 9, 10, 11, 2, 1, 0), scc.getComponents().get(1));
    }

    @RepeatedTest(30)
    public void implicitVsExplicitRecursion() {
        doImplicitVsExplicit(true);
        doImplicitVsExplicit(false);
    }

    private void doImplicitVsExplicit(boolean excludeSingle) {
        GraphHopperStorage g = new GraphBuilder(em).create();
        long seed = System.nanoTime();
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(g, rnd, 1_000, 2, true, true, null, 0.8, 0.7, 0);
        TarjanSCC.ConnectedComponents implicit = new TarjanSCC(g, accessEnc, excludeSingle).findComponentsRecursive();
        TarjanSCC.ConnectedComponents explicit = new TarjanSCC(g, accessEnc, excludeSingle).findComponents();

        assertEquals(g.getNodes(), implicit.getNodes(), "total number of nodes in connected components should equal number of nodes in graph");
        assertEquals(g.getNodes(), explicit.getNodes(), "total number of nodes in connected components should equal number of nodes in graph");

        // Unfortunately the results are not expected to be identical because the edges are traversed in reversed order
        // for the explicit stack version. To make sure the components are the same we need to check for every node that
        // the component it is contained in is the same for both algorithms.
        Set<IntWithArray> componentsImplicit = buildComponentSet(implicit.getComponents());
        Set<IntWithArray> componentsExplicit = buildComponentSet(explicit.getComponents());
        if (!componentsExplicit.equals(componentsImplicit)) {
            System.out.println("seed: " + seed);
            GHUtility.printGraphForUnitTest(g, carFlagEncoder);
            assertEquals(componentsExplicit, componentsImplicit, "The components found for this graph are different between the implicit and explicit implementation");
        }

        if (!implicit.getSingleNodeComponents().equals(explicit.getSingleNodeComponents())) {
            System.out.println("seed: " + seed);
            GHUtility.printGraphForUnitTest(g, carFlagEncoder);
            assertEquals(implicit.getSingleNodeComponents(), explicit.getSingleNodeComponents());
        }

        assertEquals(implicit.getBiggestComponent(), explicit.getBiggestComponent(), "seed: " + seed);
        assertEquals(implicit.getNodes(), explicit.getNodes(), "seed: " + seed);
        assertEquals(implicit.getTotalComponents(), explicit.getTotalComponents(), "seed: " + seed);
    }

    /**
     * Takes a list of arrays like [[0,1,3],[2,4],[6]] and turns it into a Set like
     * {[0:[0,1,3], 1:[0,1,3], 2:[2,4], 3:[0,1,3], 4:[2,4], 6:[6]}
     */
    public static Set<IntWithArray> buildComponentSet(List<IntArrayList> arrays) {
        Set<IntWithArray> result = new HashSet<>();
        for (IntArrayList c : arrays) {
            c.trimToSize();
            Arrays.sort(c.buffer);
            for (IntCursor cursor : c) {
                result.add(new IntWithArray(cursor.value, c));
            }
        }
        return result;
    }

    public static class IntWithArray {
        private final int myInt;
        private final IntArrayList array;

        public IntWithArray(int myInt, IntArrayList array) {
            this.myInt = myInt;
            this.array = array;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IntWithArray that = (IntWithArray) o;
            return myInt == that.myInt &&
                    Objects.equals(array, that.array);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myInt, array);
        }
    }

}