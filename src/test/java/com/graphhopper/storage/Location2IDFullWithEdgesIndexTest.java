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
public class Location2IDFullWithEdgesIndexTest {

    @Test
    public void testFullIndex() {
        Location2IDIndex idx = new Location2IDFullWithEdgesIndex(Location2IDQuadtreeTest.createSampleGraph());
        assertEquals(5, idx.findID(2, 3));
        assertEquals(10, idx.findID(4, 1));
        // 6, 9 or 10
        assertEquals(10, idx.findID(3.6, 1.4));
    }
}
