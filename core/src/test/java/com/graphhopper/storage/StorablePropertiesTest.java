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
package com.graphhopper.storage;

import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class StorablePropertiesTest {
    Directory createDir(String location, boolean store) {
        return new GHDirectory(location, store ? DAType.RAM_STORE : DAType.RAM).create();
    }

    @Test
    public void testLoad() {
        StorableProperties instance = new StorableProperties(createDir("", false));
        // an in-memory storage does not load anything
        assertFalse(instance.loadExisting());

        instance = new StorableProperties(createDir("", true));
        assertFalse(instance.loadExisting());
        instance.close();
    }

    @Test
    public void testStore() {
        String dir = "./target/test";
        Helper.removeDir(new File(dir));
        StorableProperties instance = new StorableProperties(createDir(dir, true));
        instance.create(1000);
        instance.put("test.min", 123);
        instance.put("test.max", 321);

        instance.flush();
        instance.close();

        instance = new StorableProperties(createDir(dir, true));
        assertTrue(instance.loadExisting());
        assertEquals("123", instance.get("test.min"));
        assertEquals("321", instance.get("test.max"));
        instance.close();

        Helper.removeDir(new File(dir));
    }

    @Test
    public void testStoreLarge() {
        String dir = "./target/test";
        Helper.removeDir(new File(dir));
        StorableProperties instance = new StorableProperties(createDir(dir, true));
        instance.create(1000);
        for (int i = 0; i <= 100_000; i++) {
            instance.put(Integer.toString(i), "test." + i);
        }

        instance.flush();
        long bytesWritten = instance.getCapacity();
        instance.close();

        instance = new StorableProperties(createDir(dir, true));
        assertTrue(instance.loadExisting());
        assertEquals(bytesWritten, instance.getCapacity());
        assertEquals("test.0", instance.get("0"));
        assertEquals("test.100000", instance.get("100000"));
        instance.close();

        Helper.removeDir(new File(dir));
    }

    @Test
    public void testLoadProperties() throws IOException {
        Map<String, String> map = new HashMap<>();
        StorableProperties.loadProperties(map, new StringReader("blup=test\n blup2 = xy"));
        assertEquals("test", map.get("blup"));
        assertEquals("xy", map.get("blup2"));
    }

}
