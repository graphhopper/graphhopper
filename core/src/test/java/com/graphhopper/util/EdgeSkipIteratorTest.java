/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.LevelGraph;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class EdgeSkipIteratorTest {

    private final EncodingManager encodingManager = new EncodingManager("CAR");
    private CarFlagEncoder carFlagsEncoder = (CarFlagEncoder) encodingManager.getEncoder("CAR");
    private EdgeFilter carOutFilter = new DefaultEdgeFilter(carFlagsEncoder, false, true);

    LevelGraph createGraph() {
        return new GraphBuilder(encodingManager).levelGraphCreate();
    }

    @Test
    public void testUpdateFlags() {
        LevelGraph g = createGraph();
        g.edge(0, 1, 12, carFlagsEncoder.flags(10, true));
        g.edge(0, 2, 13, carFlagsEncoder.flags(20, true));

        assertEquals(2, GHUtility.count(g.getAllEdges()));
        assertEquals(1, GHUtility.count(g.getEdges(1, carOutFilter)));
        EdgeIterator iter = g.getEdges(0);
        assertTrue(iter.next());
        assertEquals(1, iter.adjNode());
        assertEquals(carFlagsEncoder.flags(10, true), iter.flags());

        // update flags
        iter.flags(carFlagsEncoder.flags(20, false));
        assertEquals(12, iter.distance(), 1e-4);

        // update distance
        iter.distance(10);
        assertEquals(10, iter.distance(), 1e-4);
        assertEquals(0, GHUtility.count(g.getEdges(1, carOutFilter)));
        iter = g.getEdges(0);
        assertTrue(iter.next());
        assertEquals(carFlagsEncoder.flags(20, false), iter.flags());
        assertEquals(10, iter.distance(), 1e-4);
        assertEquals(1, GHUtility.neighbors(g.getEdges(1)).size());
        assertEquals(0, GHUtility.neighbors(g.getEdges(1, carOutFilter)).size());
    }
}
