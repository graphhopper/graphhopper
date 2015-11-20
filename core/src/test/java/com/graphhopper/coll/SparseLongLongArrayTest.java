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
package com.graphhopper.coll;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class SparseLongLongArrayTest
{
    @Test
    public void testBinarySearch()
    {
        long a[] = new long[]
                {
                        9, 53, 100
                };
        assertEquals(~1, SparseLongLongArray.binarySearch(a, 0, 3, 50));
        assertEquals(~2, SparseLongLongArray.binarySearch(a, 0, 3, 55));
        assertEquals(~3, SparseLongLongArray.binarySearch(a, 0, 3, 155));

        a = new long[]
                {
                        9
                };
        assertEquals(~0, SparseLongLongArray.binarySearch(a, 0, 1, 5));
        assertEquals(~1, SparseLongLongArray.binarySearch(a, 0, 1, 50));
    }
}
