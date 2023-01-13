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
package com.graphhopper.core.util;

import com.graphhopper.core.util.BreadthFirstSearch;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.coll.GHTBitSet;
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

        BaseGraph g = new BaseGraph.Builder(1).create();
        g.edge(0, 1);
        g.edge(0, 2);
        g.edge(0, 3);
        g.edge(0, 5);
        g.edge(1, 6);
        g.edge(2, 7);
        g.edge(3, 8);
        g.edge(4, 8);
        g.edge(8, 10);
        g.edge(6, 9);
        g.edge(9, 10);
        g.edge(5, 10);

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

        BaseGraph g = new BaseGraph.Builder(1).create();
        g.edge(1, 2);
        g.edge(2, 3);
        g.edge(3, 4);
        g.edge(1, 5);
        g.edge(5, 6);
        g.edge(6, 4);

        bfs.start(g.createEdgeExplorer(), 1);

        assertTrue(counter > 0);
        assertEquals("[1, 5, 2, 6, 3, 4]", list.toString());
    }

}
