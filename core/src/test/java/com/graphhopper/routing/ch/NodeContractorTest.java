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

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class NodeContractorTest {
    private final CarFlagEncoder encoder = new CarFlagEncoder();
    private final EncodingManager encodingManager = new EncodingManager(encoder);
    private final Weighting weighting = new ShortestWeighting(encoder);
    private final GraphHopperStorage g = new GraphBuilder(encodingManager).setCHGraph(weighting).create();
    private final CHGraph lg = g.getGraph(CHGraph.class);
    private final TraversalMode traversalMode = TraversalMode.NODE_BASED;
    private Directory dir;

    @Before
    public void setUp() {
        dir = new GHDirectory("", DAType.RAM_INT);
    }

    @Test
    public void testShortcutMergeBug() {
        // We refer to this real world situation http://www.openstreetmap.org/#map=19/52.71205/-1.77326
        // assume the following graph:
        //
        // ---1---->----2-----3
        //    \--------/
        //
        // where there are two roads from 1 to 2 and the directed road has a smaller weight
        // leading to two shortcuts sc1 (unidir) and sc2 (bidir) where the second should NOT be rejected due to the larger weight
        g.edge(1, 2, 1, true);
        g.edge(1, 2, 1, false);
        g.edge(2, 3, 1, true);
        g.freeze();

        // order is important here
        NodeContractor.Shortcut sc1 = new NodeContractor.Shortcut(1, 3, 6.81620625, 121.18);
        NodeContractor.Shortcut sc2 = new NodeContractor.Shortcut(1, 3, 6.82048125, 121.25);
        sc2.flags = PrepareEncoder.getScDirMask();
        List<NodeContractor.Shortcut> list = Arrays.asList(sc1, sc2);
        NodeContractor nodeContractor = new NodeContractor(dir, g, lg, weighting, traversalMode);
        nodeContractor.initFromGraph();
        assertEquals(2, nodeContractor.addShortcuts(list));
    }
}