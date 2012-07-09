/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class EdgeWrapperTest {

    @Test
    public void testPut() {
        EdgeWrapper instance = new EdgeWrapper(5);
        int edgeId = instance.add(10, 100f);
        assertEquals(100f, instance.getDistance(edgeId), 1e-4);
        assertEquals(-1, instance.getLink(edgeId));
        assertEquals(10, instance.getNode(edgeId));
    }
}
