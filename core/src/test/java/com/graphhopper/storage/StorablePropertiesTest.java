/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
package com.graphhopper.storage;

import com.graphhopper.util.Helper;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class StorablePropertiesTest {

    @Test
    public void testLoad() {
        StorableProperties instance = new StorableProperties(new RAMDirectory("", false));
        // an in-memory storage does not load anything
        assertFalse(instance.loadExisting());

        instance = new StorableProperties(new RAMDirectory("", true));
        assertFalse(instance.loadExisting());
        instance.close();
    }

    @Test
    public void testVersionCheck() {
        StorableProperties instance = new StorableProperties(new RAMDirectory("", false));
        instance.putCurrentVersions();
        assertTrue(instance.checkVersions(true));

        instance.put("nodes.version", 0);
        assertFalse(instance.checkVersions(true));

        try {
            instance.checkVersions(false);
            assertTrue(false);
        } catch (Exception ex) {
        }
        instance.close();
    }

    @Test
    public void testStore() {
        String dir = "./target/test";
        Helper.removeDir(new File(dir));
        StorableProperties instance = new StorableProperties(new RAMDirectory(dir, true));
        instance.create(1000);
        instance.put("test.min", 123);
        instance.put("test.max", 321);

        instance.flush();
        instance.close();

        instance = new StorableProperties(new RAMDirectory(dir, true));
        assertTrue(instance.loadExisting());
        assertEquals("123", instance.get("test.min"));
        assertEquals("321", instance.get("test.max"));
        instance.close();
        
        Helper.removeDir(new File(dir));
    }
}
