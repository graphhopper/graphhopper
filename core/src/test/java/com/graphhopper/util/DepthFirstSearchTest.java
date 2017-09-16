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
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author jansoe
 */
public class DepthFirstSearchTest {

    int counter;
    GHIntHashSet set = new GHIntHashSet();
    IntArrayList list = new IntArrayList();

    @Before
    public void setup() {
        counter = 0;
    }

    @Test
    public void testDFS1() {
        DepthFirstSearch dfs = new DepthFirstSearch() {
            @Override
            public boolean goFurther(int v) {
                counter++;
                assertTrue("v " + v + " is already contained in set. iteration:" + counter, !set.contains(v));
                set.add(v);
                list.add(v);
                return super.goFurther(v);
            }
        };

        EncodingManager em = new EncodingManager.Builder().addGlobalEncodedValues().addAllFlagEncoders("car").build();
        BooleanEncodedValue accessEnc = em.getBooleanEncodedValue(TagParserFactory.Car.ACCESS);
        DecimalEncodedValue avSpeedEnc = em.getDecimalEncodedValue(TagParserFactory.Car.AVERAGE_SPEED);
        Graph g = new GraphBuilder(em).create();
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 2, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 5, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 4, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 2, 3, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 3, 4, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 5, 6, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 6, 4, false, 1);

        dfs.start(g.createEdgeExplorer(new DefaultEdgeFilter(accessEnc, true, false)), 1);

        assertTrue(counter > 0);
        assertEquals("[1, 2, 3, 4, 5, 6]", list.toString());
    }

    @Test
    public void testDFS2() {
        DepthFirstSearch dfs = new DepthFirstSearch() {
            @Override
            public boolean goFurther(int v) {
                counter++;
                assertTrue("v " + v + " is already contained in set. iteration:" + counter, !set.contains(v));
                set.add(v);
                list.add(v);
                return super.goFurther(v);
            }
        };

        EncodingManager em = new EncodingManager.Builder().addGlobalEncodedValues().addAllFlagEncoders("car").build();
        BooleanEncodedValue accessEnc = em.getBooleanEncodedValue(TagParserFactory.Car.ACCESS);
        DecimalEncodedValue avSpeedEnc = em.getDecimalEncodedValue(TagParserFactory.Car.AVERAGE_SPEED);
        Graph g = new GraphBuilder(em).create();
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 2, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 4, true, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 1, 3, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 2, 3, false, 1);
        GHUtility.createEdge(g, avSpeedEnc, 60, accessEnc, 4, 3, true, 1);

        dfs.start(g.createEdgeExplorer(new DefaultEdgeFilter(accessEnc, true, false)), 1);

        assertTrue(counter > 0);
        assertEquals("[1, 2, 3, 4]", list.toString());
    }

}
