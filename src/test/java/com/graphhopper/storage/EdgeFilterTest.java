/*
 *  Copyright 2012 Peter Karich
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class EdgeFilterTest {

    @Test
    public void testString() {
        assertEquals("1111111111111111" + "1111111111111111", EdgeFilter.ALL.toString());
        assertEquals("0000000000000000" + "0000000000000001", EdgeFilter.OUT.toString());
        assertEquals("0000000000000000" + "0000000000000010", EdgeFilter.IN.toString());
        assertEquals("0000000000000000" + "0000000000000100", EdgeFilter.TOWER_NODES.toString());
    }

    @Test
    public void testContains() {
        assertTrue(EdgeFilter.ALL.contains(EdgeFilter.TOWER_NODES));
        assertTrue(EdgeFilter.ALL.contains(EdgeFilter.OUT));
        assertTrue(EdgeFilter.ALL.contains(EdgeFilter.IN));

        assertFalse(EdgeFilter.OUT.contains(EdgeFilter.TOWER_NODES));
    }

    @Test
    public void testCombine() {
        EdgeFilter outTower = EdgeFilter.OUT.combine(EdgeFilter.TOWER_NODES);
        assertTrue(outTower.contains(EdgeFilter.TOWER_NODES));
        assertTrue(outTower.contains(EdgeFilter.OUT));
        assertFalse(outTower.contains(EdgeFilter.IN));
        assertTrue(EdgeFilter.ALL.contains(outTower));
    }
}
