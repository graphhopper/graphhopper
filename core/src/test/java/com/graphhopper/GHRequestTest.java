/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class GHRequestTest
{
    @Test
    public void testGetHint()
    {
        GHRequest instance = new GHRequest(10, 12, 12, 10);
        instance.putHint("something", 1);
        assertEquals(1, (Number) instance.getHint("something", 2));
        // #173 - will throw an error: Integer cannot be cast to Double
        assertEquals(1, instance.getHint("something", 2d), 1e1);
        
        instance = new GHRequest(10, 12, 12, 10);
        instance.putHint("something", 1d);
        assertEquals(1d, (Number) instance.getHint("something", 2));
        assertEquals(1d, (Number) instance.getHint("something", 2d));
    }
}
