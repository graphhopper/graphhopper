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
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.storage.BaseGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jansoe
 */
public class DepthFirstSearchTest {

    int counter;
    GHIntHashSet set = new GHIntHashSet();
    IntArrayList list = new IntArrayList();

    @BeforeEach
    public void setup() {
        counter = 0;
    }

    @Test
    public void testDFS1() {
        DepthFirstSearch dfs = new DepthFirstSearch() {
            @Override
            protected GHBitSet createBitSet() {
                return new GHBitSetImpl();
            }

            @Override
            public boolean goFurther(int v) {
                counter++;
                assertTrue(!set.contains(v), "v " + v + " is already contained in set. iteration:" + counter);
                set.add(v);
                list.add(v);
                return super.goFurther(v);
            }
        };

        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        EncodedValue.InitializerConfig evConf = new EncodedValue.InitializerConfig();
        accessEnc.init(evConf);
        BaseGraph g = new BaseGraph.Builder(evConf.getRequiredBytes()).create();
        g.edge(1, 2).setDistance(1).set(accessEnc, true, false);
        g.edge(1, 5).setDistance(1).set(accessEnc, true, false);
        g.edge(1, 4).setDistance(1).set(accessEnc, true, false);
        g.edge(2, 3).setDistance(1).set(accessEnc, true, false);
        g.edge(3, 4).setDistance(1).set(accessEnc, true, false);
        g.edge(5, 6).setDistance(1).set(accessEnc, true, false);
        g.edge(6, 4).setDistance(1).set(accessEnc, true, false);

        dfs.start(g.createEdgeExplorer(AccessFilter.outEdges(accessEnc)), 1);

        assertTrue(counter > 0);
        assertEquals(list.toString(), "[1, 2, 3, 4, 5, 6]");
    }

    @Test
    public void testDFS2() {
        DepthFirstSearch dfs = new DepthFirstSearch() {
            @Override
            protected GHBitSet createBitSet() {
                return new GHBitSetImpl();
            }

            @Override
            public boolean goFurther(int v) {
                counter++;
                assertTrue(!set.contains(v), "v " + v + " is already contained in set. iteration:" + counter);
                set.add(v);
                list.add(v);
                return super.goFurther(v);
            }
        };

        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        EncodedValue.InitializerConfig evConf = new EncodedValue.InitializerConfig();
        accessEnc.init(evConf);
        BaseGraph g = new BaseGraph.Builder(evConf.getRequiredBytes()).create();
        g.edge(1, 2).setDistance(1).set(accessEnc, true, false);
        g.edge(1, 4).setDistance(1).set(accessEnc, true, true);
        g.edge(1, 3).setDistance(1).set(accessEnc, true, false);
        g.edge(2, 3).setDistance(1).set(accessEnc, true, false);
        g.edge(4, 3).setDistance(1).set(accessEnc, true, true);

        dfs.start(g.createEdgeExplorer(AccessFilter.outEdges(accessEnc)), 1);

        assertTrue(counter > 0);
        assertEquals(list.toString(), "[1, 2, 3, 4]");
    }

}
