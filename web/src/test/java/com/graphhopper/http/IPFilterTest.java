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
package com.graphhopper.http;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class IPFilterTest {
    @Test
    public void testAcceptWhite() {
        IPFilter instance = new IPFilter("1.2.3.4, 4.5.67.1", "");
        assertTrue(instance.accept("1.2.3.4"));
        assertTrue(instance.accept("4.5.67.1"));
        assertFalse(instance.accept("1.2.3.5"));

        instance = new IPFilter("1.2.3*, 4.5.67.1, 7.8.*.3", "");
        assertTrue(instance.accept("1.2.3.4"));
        assertTrue(instance.accept("4.5.67.1"));
        assertTrue(instance.accept("1.2.3.5"));
        assertFalse(instance.accept("1.3.5.7"));

        assertTrue(instance.accept("7.8.5.3"));
        assertFalse(instance.accept("7.88.5.3"));
    }

    @Test
    public void testAcceptBlack() {
        IPFilter instance = new IPFilter("", "1.2.3.4, 4.5.67.1");

        assertFalse(instance.accept("1.2.3.4"));
        assertFalse(instance.accept("4.5.67.1"));
        assertTrue(instance.accept("1.2.3.5"));
    }

    @Test
    public void testFilterSpecialCases() {
        IPFilter instance = new IPFilter("", "");
        assertTrue(instance.accept("1.2.3.4"));

        try {
            new IPFilter("1.2.3.4, 4.5.67.1", "8.9.7.3");
            assertFalse("black and white", true);
        } catch (Exception ex) {

        }
    }
}
