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
package com.graphhopper.coll;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class GHTreeMapComposedTest {
    @Test
    public void testInsert() {
        GHTreeMapComposed instance = new GHTreeMapComposed();
        instance.insert(1, 100);
        assertEquals(1, instance.peekKey());
        assertEquals(100, instance.peekValue());

        instance.insert(2, 99);
        instance.insert(3, 101);
        assertEquals(2, instance.peekKey());
        assertEquals(99, instance.peekValue());

        assertEquals(2, instance.pollKey());
    }
}
