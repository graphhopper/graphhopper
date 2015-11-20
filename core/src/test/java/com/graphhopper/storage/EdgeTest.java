/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.storage;

import com.graphhopper.util.EdgeIterator;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class EdgeTest
{
    @Test
    public void testCloneFull()
    {
        EdgeEntry de = new EdgeEntry(EdgeIterator.NO_EDGE, 1, 10);
        EdgeEntry de2 = de.parent = new EdgeEntry(EdgeIterator.NO_EDGE, -2, 20);
        EdgeEntry de3 = de2.parent = new EdgeEntry(EdgeIterator.NO_EDGE, 3, 30);

        EdgeEntry cloning = de.cloneFull();
        EdgeEntry tmp1 = de;
        EdgeEntry tmp2 = cloning;

        assertNotNull(tmp1);
        while (tmp1 != null)
        {
            assertFalse(tmp1 == tmp2);
            assertEquals(tmp1.edge, tmp2.edge);
            tmp1 = tmp1.parent;
            tmp2 = tmp2.parent;
        }
        assertNull(tmp2);
    }
}
