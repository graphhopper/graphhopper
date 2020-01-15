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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class OSMNodeTest {
    @Test
    public void testSetTags() {
        ReaderNode instance = new ReaderNode(0, 10, 10);
        assertTrue(Double.isNaN(instance.getEle()));

        instance.setTag("ele", "-10.1");
        assertEquals(-10.1, instance.getEle(), 1e-1);

        // oh OSM
        instance.setTag("ele", "-10,1");
        assertEquals(-10.1, instance.getEle(), 1e-1);
        // do not parse other stuff
        instance.setTag("ele", "-9.1 m.");
        assertEquals(-10.1, instance.getEle(), 1e-1);

        // empty values
        instance.setTag("ele", "");
        assertTrue(Double.isNaN(instance.getEle()));
        instance.setTag("ele", "-10.1");
        assertEquals(-10.1, instance.getEle(), 1e-1);
        instance.setTag("ele", null);
        assertTrue(Double.isNaN(instance.getEle()));
    }
}
