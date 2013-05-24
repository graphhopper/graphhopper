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
package com.graphhopper.storage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class StorablePropertiesTest {

    @Test
    public void testVersionCheck() {
        StorableProperties instance = new StorableProperties(new RAMDirectory("", false), "prop");
        instance.putCurrentVersions();
        assertTrue(instance.checkVersions(true));

        instance.put("nodes.version", 0);
        assertFalse(instance.checkVersions(true));

        try {
            instance.checkVersions(false);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }
}
