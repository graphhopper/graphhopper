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
package com.graphhopper.util.shapes;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class CircleTest {
    @Test
    public void testIntersectCircleCircle() {
        assertTrue(new Circle(0, 0, 80000).intersect(new Circle(1, 1, 80000)));
        assertFalse(new Circle(0, 0, 75000).intersect(new Circle(1, 1, 80000)));
    }

    @Test
    public void testIntersectCircleBBox() {
        assertTrue(new Circle(10, 10, 120000).intersect(new BBox(9, 11, 8, 9)));
        assertTrue(new BBox(9, 11, 8, 9).intersect(new Circle(10, 10, 120000)));

        assertFalse(new Circle(10, 10, 110000).intersect(new BBox(9, 11, 8, 9)));
        assertFalse(new BBox(9, 11, 8, 9).intersect(new Circle(10, 10, 110000)));
    }

    @Test
    public void testContains() {
        Circle c = new Circle(10, 10, 120000);
        assertTrue(c.contains(new BBox(9, 11, 10, 10.1)));
        assertFalse(c.contains(new BBox(9, 11, 8, 9)));
        assertFalse(c.contains(new BBox(9, 12, 10, 10.1)));
    }

    @Test
    public void testGetCenter() {
        Circle c = new Circle(10, 10, 10);
        GHPoint center = c.getCenter();
        assertEquals(10, center.getLat(), .00001);
        assertEquals(10, center.getLon(), .00001);
    }

    @Test
    public void testContainsCircle() {
        Circle c = new Circle(10, 10, 120000);
        assertTrue(c.contains(new Circle(9.9, 10.2, 90000)));
        assertFalse(c.contains(new Circle(10, 10.4, 90000)));
    }
}
