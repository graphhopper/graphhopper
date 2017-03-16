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

import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class BBoxTest {
    @Test
    public void testCreate() {
        DistanceCalc c = new DistanceCalcEarth();
        BBox b = c.createBBox(52, 10, 100000);

        // The calclulated bounding box has no negative values (also for southern hemisphere and negative meridians)
        // and the ordering is always the same (top to bottom and left to right)
        assertEquals(52.8993, b.maxLat, 1e-4);
        assertEquals(8.5393, b.minLon, 1e-4);

        assertEquals(51.1007, b.minLat, 1e-4);
        assertEquals(11.4607, b.maxLon, 1e-4);
    }

    @Test
    public void testContains() {
        assertTrue(new BBox(1, 2, 0, 1).contains(new BBox(1, 2, 0, 1)));
        assertTrue(new BBox(1, 2, 0, 1).contains(new BBox(1.5, 2, 0.5, 1)));
        assertFalse(new BBox(1, 2, 0, 0.5).contains(new BBox(1.5, 2, 0.5, 1)));

        Circle c = new Circle(10, 10, 120000);
        assertTrue(c.getBounds().contains(c));
        assertFalse(new BBox(8.9, 11.09, 8.9, 11.2).contains(c));
    }

    @Test
    public void testGetCenter() {
        BBox bBox = new BBox(0, 2, 0, 2);
        GHPoint center = bBox.getCenter();
        assertEquals(1, center.getLat(), .00001);
        assertEquals(1, center.getLon(), .00001);
    }

    @Test
    public void testIntersect() {
        //    ---
        //    | |
        // ---------
        // |  | |  |
        // --------
        //    |_|
        //

        // use ISO 19115 standard (minLon, maxLon followed by minLat(south!),maxLat)
        assertTrue(new BBox(12, 15, 12, 15).intersect(new BBox(13, 14, 11, 16)));
        // assertFalse(new BBox(15, 12, 12, 15).intersect(new BBox(16, 15, 11, 14)));

        // DOES NOT WORK: use bottom to top coord for lat
        // assertFalse(new BBox(6, 2, 11, 6).intersect(new BBox(5, 3, 12, 5)));
        // so, use bottom-left and top-right corner!
        assertTrue(new BBox(2, 6, 6, 11).intersect(new BBox(3, 5, 5, 12)));

        // DOES NOT WORK: use bottom to top coord for lat and right to left for lon
        // assertFalse(new BBox(6, 11, 11, 6).intersect(new BBox(5, 10, 12, 7)));
        // so, use bottom-right and top-left corner
        assertTrue(new BBox(6, 11, 6, 11).intersect(new BBox(7, 10, 5, 12)));
    }

    @Test
    public void testCalculateIntersection() {

        BBox b1 = new BBox(0, 2, 0, 1);
        BBox b2 = new BBox(-1, 1, -1, 2);
        BBox expected = new BBox(0, 1, 0, 1);

        assertEquals(expected, b1.calculateIntersection(b2));

        //No intersection
        b2 = new BBox(100, 200, 100, 200);
        assertNull(b1.calculateIntersection(b2));
    }

    @Test
    public void testBasicJavaOverload() {
        new BBox(2, 4, 0, 1) {
            @Override
            public boolean intersect(Circle c) {
                assertTrue(true);
                return super.intersect(c);
            }

            @Override
            public boolean intersect(Shape c) {
                assertTrue(false);
                return true;
            }

            @Override
            public boolean intersect(BBox c) {
                assertTrue(false);
                return true;
            }
        }.intersect(new Circle(1, 2, 3) {
            @Override
            public boolean intersect(Circle c) {
                assertTrue(false);
                return true;
            }

            @Override
            public boolean intersect(Shape b) {
                assertTrue(false);
                return true;
            }

            @Override
            public boolean intersect(BBox b) {
                assertTrue(true);
                return true;
            }
        });
    }

    @Test
    public void testParseTwoPoints() {
        assertEquals(new BBox(2, 4, 1, 3), BBox.parseTwoPoints("1,2,3,4"));
        // stable parsing, i.e. if first point is in north or south it does not matter:
        assertEquals(new BBox(2, 4, 1, 3), BBox.parseTwoPoints("3,2,1,4"));
    }
}
