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

import java.util.Random;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GHIntArrayListTest {

    @Test
    public void testReverse() {
        assertEquals(GHIntArrayList.from(4, 3, 2, 1), GHIntArrayList.from(1, 2, 3, 4).reverse());
        assertEquals(GHIntArrayList.from(5, 4, 3, 2, 1), GHIntArrayList.from(1, 2, 3, 4, 5).reverse());
    }

    @Test
    public void testShuffle() {
        assertEquals(GHIntArrayList.from(4, 1, 3, 2), GHIntArrayList.from(1, 2, 3, 4).shuffle(new Random(0)));
        assertEquals(GHIntArrayList.from(4, 3, 2, 1, 5), GHIntArrayList.from(1, 2, 3, 4, 5).shuffle(new Random(1)));
    }

    @Test
    public void testFill() {
        assertEquals(GHIntArrayList.from(-1, -1, -1, -1), new GHIntArrayList(4).fill(4, -1));
    }
}
