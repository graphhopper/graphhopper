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

import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class LinkedDistEntryWithFlagsTest {

    @Test
    public void testFlags() {
        LinkedDistEntryWithFlags de = new LinkedDistEntryWithFlags(1, 12, (byte) 1);
        de.directionFlag |= 1;
        assertEquals(1, de.directionFlag);
        de.directionFlag |= 2;
        assertEquals(3, de.directionFlag);

        de = new LinkedDistEntryWithFlags(1, 12, (byte) 1);
        de.directionFlag |= 2;
        assertEquals(3, de.directionFlag);

        de = new LinkedDistEntryWithFlags(1, 12, (byte) 2);
        de.directionFlag |= 1;
        assertEquals(3, de.directionFlag);
    }
}
