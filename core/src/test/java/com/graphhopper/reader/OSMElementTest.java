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
package com.graphhopper.reader;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class OSMElementTest {
    @Test
    public void testHasTag() {
        ReaderElement instance = new ReaderWay(1);
        instance.setTag("surface", "something");
        assertTrue(instance.hasTag("surface", "now", "something"));
        assertFalse(instance.hasTag("surface", "now", "not"));
    }

    @Test
    public void testSetTags() {
        ReaderElement instance = new ReaderWay(1);
        Map<String, String> map = new HashMap<>();
        map.put("test", "xy");
        instance.setTags(map);
        assertTrue(instance.hasTag("test", "xy"));

        instance.setTags(null);
        assertFalse(instance.hasTag("test", "xy"));
    }
}
