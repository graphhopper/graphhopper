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
package com.graphhopper.util;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class BreadthFirstSearchTest {
    int counter;
    GHIntHashSet set = new GHIntHashSet();
    IntArrayList list = new IntArrayList();

    @BeforeEach
    public void setup() {
        counter = 0;
    }

    @Test
    public void testBFS() {
        BreadthFirstSearch bfs = new BreadthFirstSearch() {
            @Override
            protected GHBitSet createBitSet() {
                return new GHTBitSet();
            }

            @Override
            public boolean goFurther(int v) {
                counter++;
                assertFalse(set.contains(v), "v " + v + " is already contained in set. iteration:" + counter);
                set.add(v);
                list.add(v);
                return super.goFurther(v);
            }
        };

        CarFlagEncoder encoder = new CarFlagEncoder();
        BaseGraph g = new BaseGraph.Builder(EncodingManager.create(encoder)).create();
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 1).setDistance(85));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 2).setDistance(217));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 3).setDistance(173));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(0, 5).setDistance(173));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(1, 6).setDistance(75));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(2, 7).setDistance(51));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(3, 8).setDistance(23));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(4, 8).setDistance(793));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(8, 10).setDistance(343));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(6, 9).setDistance(72));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(9, 10).setDistance(8));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(5, 10).setDistance(1));

        bfs.start(g.createEdgeExplorer(), 0);

        assertTrue(counter > 0);
        assertEquals(g.getNodes(), counter);
        assertEquals("[0, 5, 3, 2, 1, 10, 8, 7, 6, 9, 4]", list.toString());
    }

    @Test
    public void testBFS2() {
        BreadthFirstSearch bfs = new BreadthFirstSearch() {
            @Override
            protected GHBitSet createBitSet() {
                return new GHTBitSet();
            }

            @Override
            public boolean goFurther(int v) {
                counter++;
                assertFalse(set.contains(v), "v " + v + " is already contained in set. iteration:" + counter);
                set.add(v);
                list.add(v);
                return super.goFurther(v);
            }
        };

        CarFlagEncoder encoder = new CarFlagEncoder();
        BaseGraph g = new BaseGraph.Builder(EncodingManager.create(encoder)).create();
        GHUtility.setSpeed(60, true, false, encoder, g.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(1, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, g.edge(6, 4).setDistance(1));

        bfs.start(g.createEdgeExplorer(), 1);

        assertTrue(counter > 0);
        assertEquals("[1, 5, 2, 6, 3, 4]", list.toString());
    }

}
