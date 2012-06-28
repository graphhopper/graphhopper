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
package de.jetsli.graph.storage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class EdgeTest {

    @Test
    public void testCloneFull() {
        Edge de = new Edge(1, 10);
        Edge de2 = de.prevEntry = new Edge(2, 20);
        Edge de3 = de2.prevEntry = new Edge(3, 30);

        Edge cloning = de.cloneFull();
        Edge tmp1 = de;
        Edge tmp2 = cloning;

        assertNotNull(tmp1);
        while (tmp1 != null) {
            assertFalse(tmp1 == tmp2);
            assertEquals(tmp1.node, tmp2.node);
            tmp1 = tmp1.prevEntry;
            tmp2 = tmp2.prevEntry;
        }
        assertNull(tmp2);
    }
}
