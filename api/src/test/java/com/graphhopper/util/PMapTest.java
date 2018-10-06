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
package com.graphhopper.util;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class PMapTest {

    @Test
    public void singleStringPropertyCanBeRetrieved() {
        PMap subject = new PMap("foo=bar");

        Assert.assertEquals("bar", subject.get("foo", ""));
    }

    @Test
    public void propertyFromStringWithMultiplePropertiesCanBeRetrieved() {
        PMap subject = new PMap("foo=valueA|bar=valueB");

        Assert.assertEquals("valueA", subject.get("foo", ""));
        Assert.assertEquals("valueB", subject.get("bar", ""));
    }

    @Test
    public void keyCannotHaveAnyCasing() {
        PMap subject = new PMap("foo=valueA|bar=valueB");

        assertEquals("valueA", subject.get("foo", ""));
        assertEquals("", subject.get("Foo", ""));
    }

    @Test
    public void numericPropertyCanBeRetrievedAsLong() {
        PMap subject = new PMap("foo=1234|bar=5678");

        assertEquals(1234L, subject.getLong("foo", 0));
    }

    @Test
    public void numericPropertyCanBeRetrievedAsDouble() {
        PMap subject = new PMap("foo=123.45|bar=56.78");

        assertEquals(123.45, subject.getDouble("foo", 0), 1e-4);
    }

    @Test
    public void numericPropertiesCanBeRetrievedWithNull() {
        PMap subject = new PMap("x=123|y=26.3|z=6garble");
        assertEquals(123, (int) subject.getIntOrNull("x"));
        assertNull(subject.getIntOrNull("y"));
        assertNull(subject.getIntOrNull("z"));
        assertNull(subject.getIntOrNull("xyz"));
        assertEquals(123, subject.getDoubleOrNull("x"), 1.e-4);
        assertNull(subject.getLongOrNull("y"));
    }

    @Test
    public void hasReturnsCorrectResult() {
        PMap subject = new PMap("foo=123.45|bar=56.78");

        assertTrue(subject.has("foo"));
        assertTrue(subject.has("bar"));
        assertFalse(subject.has("baz"));
    }

}
