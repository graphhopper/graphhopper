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
package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeExplorer;
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
public class FootFlagEncoderTest
{
    private EncodingManager encodingManager = new EncodingManager("CAR,BIKE,FOOT");
    private FootFlagEncoder footEncoder = (FootFlagEncoder) encodingManager.getEncoder("FOOT");

    @Test
    public void testGetSpeed()
    {
        int fl = footEncoder.flags(10, true);
        assertEquals(10, footEncoder.getSpeed(fl));
    }

    @Test
    public void testBasics()
    {
        int fl = footEncoder.flagsDefault(true);
        assertEquals(FootFlagEncoder.MEAN, footEncoder.getSpeed(fl));

        int fl1 = footEncoder.flagsDefault(false);
        int fl2 = footEncoder.swapDirection(fl1);
        assertEquals(footEncoder.getSpeed(fl2), footEncoder.getSpeed(fl1));
    }

    @Test
    public void testCombined()
    {
        FlagEncoder carEncoder = encodingManager.getEncoder("CAR");
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
    public void testGraph()
    {
        Graph g = new GraphBuilder(encodingManager).create();
        g.edge(0, 1, 10, footEncoder.flags(10, true));
        g.edge(0, 2, 10, footEncoder.flags(5, true));
        g.edge(1, 3, 10, footEncoder.flags(10, true));
        EdgeExplorer out = g.createEdgeExplorer(new DefaultEdgeFilter(footEncoder, false, true));
        assertEquals(Arrays.asList(1, 2), GHUtility.getNeighbors(out.setBaseNode(0)));
        assertEquals(Arrays.asList(0, 3), GHUtility.getNeighbors(out.setBaseNode(1)));
        assertEquals(Arrays.asList(0), GHUtility.getNeighbors(out.setBaseNode(2)));
    }

    @Test
    public void testAccess()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);

        map.put("highway", "motorway");
        map.put("sidewalk", "yes");
        assertTrue(footEncoder.isAllowed(way) > 0);
        map.put("sidewalk", "left");
        assertTrue(footEncoder.isAllowed(way) > 0);

        map.put("sidewalk", "none");
        assertFalse(footEncoder.isAllowed(way) > 0);
        map.clear();

        map.put("highway", "pedestrian");
        assertTrue(footEncoder.isAllowed(way) > 0);

        map.put("highway", "footway");
        assertTrue(footEncoder.isAllowed(way) > 0);

        map.put("highway", "motorway");
        assertFalse(footEncoder.isAllowed(way) > 0);

        map.put("highway", "path");
        assertTrue(footEncoder.isAllowed(way) > 0);

        map.put("bicycle", "official");
        assertFalse(footEncoder.isAllowed(way) > 0);

        map.put("foot", "official");
        assertTrue(footEncoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "service");
        map.put("access", "no");
        assertFalse(footEncoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "tertiary");
        map.put("motorroad", "yes");
        assertFalse(footEncoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "cycleway");
        assertFalse(footEncoder.isAllowed(way) > 0);
        map.put("foot", "yes");
        assertTrue(footEncoder.isAllowed(way) > 0);

        map.clear();
        map.put("highway", "track");
        map.put("ford", "yes");
        assertFalse(footEncoder.isAllowed(way) > 0);
        map.put("foot", "yes");
        assertTrue(footEncoder.isAllowed(way) > 0);

        map.clear();
        map.put("route", "ferry");
        assertTrue(footEncoder.isAllowed(way) > 0);
        map.put("foot", "no");
        assertFalse(footEncoder.isAllowed(way) > 0);
    }

    @Test
    public void testMixSpeedAndSafe()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);

        map.put("highway", "motorway");
        int flags = footEncoder.handleWayTags(footEncoder.isAllowed(way), way);
        assertEquals(0, flags);

        map.put("sidewalk", "yes");
        flags = footEncoder.handleWayTags(footEncoder.isAllowed(way), way);
        assertEquals(5, footEncoder.getSpeed(flags));

        map.clear();
        map.put("highway", "track");
        flags = footEncoder.handleWayTags(footEncoder.isAllowed(way), way);
        assertEquals(5, footEncoder.getSpeed(flags));
    }

    @Test
    public void testParseDuration()
    {
        assertEquals(10, FootFlagEncoder.parseDuration("00:10"));
        assertEquals(70, FootFlagEncoder.parseDuration("01:10"));
        assertEquals(0, FootFlagEncoder.parseDuration("oh"));
        assertEquals(0, FootFlagEncoder.parseDuration(null));
        assertEquals(60 * 20, FootFlagEncoder.parseDuration("20:00"));
    }

    @Test
    public void testSlowHiking()
    {
        Map<String, String> map = new HashMap<String, String>();
        OSMWay way = new OSMWay(1, map);
        map.put("highway", "track");
        map.put("sac_scale", "hiking");
        int flags = footEncoder.handleWayTags(footEncoder.isAllowed(way), way);
        assertEquals(FootFlagEncoder.MEAN, footEncoder.getSpeed(flags));

        map.put("highway", "track");
        map.put("sac_scale", "mountain_hiking");
        flags = footEncoder.handleWayTags(footEncoder.isAllowed(way), way);
        assertEquals(FootFlagEncoder.SLOW, footEncoder.getSpeed(flags));
    }
}