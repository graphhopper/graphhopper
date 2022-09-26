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
package com.graphhopper.reader;

import org.junit.jupiter.api.Test;

import com.carrotsearch.hppc.LongArrayList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class OSMElementTest {
    @Test
    public void testHasTag() {
        ReaderElement instance = new ReaderWay(1);
        instance.setTag("surface", "something");
        assertTrue(instance.hasTag("surface", "now", "something"));
        assertFalse(instance.hasTag("surface", "now", "not"));
    }

    @Test
    public void testSetTags() {
        ReaderElement instance = new ReaderWay(1);
        Map<String, String> map = new HashMap<>();
        map.put("test", "xy");
        instance.setTags(map);
        assertTrue(instance.hasTag("test", "xy"));

        instance.setTags(null);
        assertFalse(instance.hasTag("test", "xy"));
    }

    @Test
    public void testInvalidIDs() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new ReaderWay(-1);
        });
        assertTrue(exception.getMessage().contains("Invalid OSM WAY Id: -1;"));
    }

    @Test
    public void testWayRestrictionBuild() {
    	// the ways listed inside a via way restriction
        List<Long> ways = new ArrayList<Long>();
        ways.add(12L); // from
        ways.add(23L); // via
        ways.add(34L); // to

    	// mocking these ways
    	LongArrayList nodes12 = new LongArrayList();
    	nodes12.add(1L);
    	nodes12.add(2L);
    	ReaderWay way12 = new ReaderWay(12L, nodes12);
    	LongArrayList nodes23 = new LongArrayList();
    	nodes23.add(2L);
    	nodes23.add(3L);
    	ReaderWay way23 = new ReaderWay(23L, nodes23);
    	LongArrayList nodes34 = new LongArrayList();
    	nodes34.add(3L);
    	nodes34.add(4L);
    	ReaderWay way34 = new ReaderWay(34L, nodes34);
    	
    	// mocking the wayNodesMap created during first parse
        HashMap<Long, ReaderWay> wayNodesMap = new HashMap<>();
        wayNodesMap.put(12L, way12);
        wayNodesMap.put(23L, way23);
        wayNodesMap.put(34L, way34);

        WayRestriction restriction = new WayRestriction(1L, ways);
        restriction.buildRestriction(wayNodesMap);

        NodeRestriction r1 = restriction.getRestrictions().get(0);
        NodeRestriction r2 = restriction.getRestrictions().get(1);

        assertEquals(r1.toString(), new NodeRestriction(12L, 2L, 23L).toString());
        assertEquals(r2.toString(), new NodeRestriction(23L, 3L, 34L).toString());
    }

}
