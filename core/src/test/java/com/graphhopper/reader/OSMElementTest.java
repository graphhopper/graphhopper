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

import com.graphhopper.reader.ReaderRestriction.Restriction;

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
    public void testRestriction() {
        List<Long> ways = new ArrayList<Long>();
        ways.add(12L);
        ways.add(23L);
        ways.add(34L);

        HashMap<Long, Long[]> wayNodesMap = new HashMap<>();
        wayNodesMap.put(12L, new Long[]{1L, 2L});
        wayNodesMap.put(23L, new Long[]{2L, 3L});
        wayNodesMap.put(34L, new Long[]{3L, 4L});

        ReaderRestriction restriction = new ReaderRestriction(ways);
        restriction.buildRestriction(wayNodesMap);

        ReaderRestriction.Restriction r1 = restriction.getRestrictions().get(0);
        ReaderRestriction.Restriction r2 = restriction.getRestrictions().get(1);

        assertEquals(r1.toString(), new ReaderRestriction.Restriction(12L, 2L, 23L).toString());
        assertEquals(r2.toString(), new ReaderRestriction.Restriction(23L, 3L, 34L).toString());
    }

}
