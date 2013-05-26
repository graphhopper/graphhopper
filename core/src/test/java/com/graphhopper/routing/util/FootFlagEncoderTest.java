/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
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
package com.graphhopper.routing.util;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.GHUtility;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class FootFlagEncoderTest {

    private FootFlagEncoder footEncoder = new FootFlagEncoder();

    @Test
    public void testGetSpeed() {
        int fl = footEncoder.flags(10, true);
        assertEquals(10, footEncoder.getSpeed(fl));
    }

    @Test
    public void testBasics() {
        int fl = footEncoder.flagsDefault(true);
        assertEquals(footEncoder.getSpeed("mean").intValue(), footEncoder.getSpeed(fl));

        int fl1 = footEncoder.flagsDefault(false);
        int fl2 = footEncoder.swapDirection(fl1);
        assertEquals(footEncoder.getSpeed(fl2), footEncoder.getSpeed(fl1));
    }

    @Test
    public void testCombined() {
        EdgePropertyEncoder carEncoder = new CarFlagEncoder();
        int fl = footEncoder.flags(10, true) | carEncoder.flags(100, false);
        assertEquals(10, footEncoder.getSpeed(fl));
        assertTrue(footEncoder.isForward(fl));
        assertTrue(footEncoder.isBackward(fl));

        assertEquals(100, carEncoder.getSpeed(fl));
        assertTrue(carEncoder.isForward(fl));
        assertFalse(carEncoder.isBackward(fl));

        assertEquals(0, carEncoder.getSpeed(footEncoder.flags(10, true)));
    }

    @Test
    public void testGraph() {
        Graph g = new GraphBuilder().create();
        g.edge(0, 1, 10, footEncoder.flags(10, true));
        g.edge(0, 2, 10, footEncoder.flags(5, true));
        g.edge(1, 3, 10, footEncoder.flags(10, true));
        EdgeFilter out = new DefaultEdgeFilter(footEncoder, false, true);
        assertEquals(Arrays.asList(1, 2), GHUtility.neighbors(g.getEdges(0, out)));
        assertEquals(Arrays.asList(0, 3), GHUtility.neighbors(g.getEdges(1, out)));
        assertEquals(Arrays.asList(0), GHUtility.neighbors(g.getEdges(2, out)));
    }

    @Test
    public void testAccess() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("sidewalk", "yes");
        assertTrue(footEncoder.isAllowed(map));
        map.put("sidewalk", "left");
        assertTrue(footEncoder.isAllowed(map));
        
        map.put("sidewalk", "none");
        assertFalse(footEncoder.isAllowed(map));
        map.clear();
        
        map.put("highway", "pedestrian");
        assertTrue(footEncoder.isAllowed(map));

        map.put("highway", "footway");
        assertTrue(footEncoder.isAllowed(map));

        map.put("highway", "motorway");
        assertFalse( footEncoder.isAllowed(map));

        map.put("highway", "path");
        assertTrue(footEncoder.isAllowed(map));

        map.put("bicycle", "official");
        assertFalse(footEncoder.isAllowed(map));

        map.put("foot", "official");
        assertTrue(footEncoder.isAllowed(map));

        map.clear();
        map.put("highway", "service");
        map.put("access", "no");
        assertFalse(footEncoder.isAllowed(map));
        
        map.clear();
        map.put("highway", "tertiary");
        map.put("motorroad", "yes");
        assertFalse(footEncoder.isAllowed(map));

        map.clear();
        map.put("highway", "cycleway");
        assertFalse( footEncoder.isAllowed(map));
        map.put("foot", "yes");
        assertTrue(footEncoder.isAllowed(map));        
    }
}