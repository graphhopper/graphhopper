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
package com.graphhopper.coll;

import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class MyLongIntHashMapTest {

    @Test
    public void testThrowException_IfPutting_NoNumber() {
        MyLongIntHashMap instance = new MyLongIntHashMap(new RAMDirectory());
        try {
            instance.put(0, 1);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testPut() {
        MyLongIntHashMap instance = new MyLongIntHashMap(new RAMDirectory());
        int result = instance.put(100, 10);
        assertEquals(instance.getNoNumberValue(), result);

        result = instance.get(100);
        assertEquals(10, result);

        result = instance.put(100, 9);
        assertEquals(10, result);

        result = instance.get(100);
        assertEquals(9, result);
    }
}
