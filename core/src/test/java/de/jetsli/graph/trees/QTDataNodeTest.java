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
package de.jetsli.graph.trees;

import de.jetsli.graph.geohash.SpatialKeyAlgo;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class QTDataNodeTest {

    @Test
    public void testGetMemoryUsageInBytes() {
        QTDataNode<Integer> dn = new QTDataNode<Integer>(8);
        dn.keys[1] = 111;
        dn.values[1] = 222;
        assertEquals(0, dn.count());
        dn.add(1, 1);
        assertEquals(1, dn.count());
    }
}
