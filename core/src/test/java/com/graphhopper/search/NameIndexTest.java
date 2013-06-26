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
package com.graphhopper.search;

import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class NameIndexTest {

    @Test
    public void testPut() {
        NameIndex instance = new NameIndex(new RAMDirectory()).create(1000);
        int result = instance.put("Something Streetä");
        assertEquals("Something Streetä", instance.get(result));

        int existing = instance.put("Something Streetä");
        assertEquals(result, existing);

        result = instance.put("testing");
        assertEquals("testing", instance.get(result));                
        
        assertEquals(0, instance.put(""));
        assertEquals(0, instance.put(null));
        assertEquals("", instance.get(0));
    }
    
    @Test
    public void testCreate()
    {
        NameIndex result = new NameIndex(new RAMDirectory()).create(1000);
        String str1 = "nice";
        int pointer1 = result.put(str1);

        String str2 = "nice work äöß";
        int pointer2 = result.put(str2);

        assertEquals(str2, result.get(pointer2));
        assertEquals(str1, result.get(pointer1));
    }
}
