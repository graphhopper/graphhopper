/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.util.shapes;

import de.jetsli.graph.util.shapes.BBox;
import de.jetsli.graph.reader.CalcDistance;
import java.util.concurrent.CountDownLatch;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class BBoxTest {

    @Test
    public void testCreate() {
        CalcDistance c = new CalcDistance();
        BBox b = c.createBBox(52, 10, 100);

        // The calclulated bounding box has no negative values (also for southern hemisphere and negative meridians)
        // and the ordering is always the same (top to bottom and left to right)
        assertEquals(52.8993, b.lat1, 1e-4);
        assertEquals(8.5393, b.lon1, 1e-4);

        assertEquals(51.1007, b.lat2, 1e-4);
        assertEquals(11.4607, b.lon2, 1e-4);

        // something about 141 = sqrt(2*100^2)
//        System.out.println(c.calcDistKm(52, 10, 52.8993, 11.4607));
//        System.out.println(c.calcDistKm(52, 10, 51.1007, 8.5393));
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

        // use bottom-to-top coord for lat
        assertTrue(new BBox(15, 12, 12, 15).intersect(new BBox(16, 13, 11, 14)));
        // assertFalse(new BBox(15, 12, 12, 15).intersect(new BBox(16, 15, 11, 14)));

        // DOES NOT WORK: use bottom to top coord for lat
        // assertFalse(new BBox(6, 2, 11, 6).intersect(new BBox(5, 3, 12, 5)));
        // so, use bottom-left and top-right corner!
        assertTrue(new BBox(11, 2, 6, 6).intersect(new BBox(12, 3, 5, 5)));

        // DOES NOT WORK: use bottom to top coord for lat and right to left for lon
        // assertFalse(new BBox(6, 11, 11, 6).intersect(new BBox(5, 10, 12, 7)));
        // so, use bottom-right and top-left corner
        assertTrue(new BBox(11, 6, 6, 11).intersect(new BBox(12, 7, 5, 10)));
    }

    @Test
    public void testBasicJavaOverload() {
        new BBox(1, 2, 0, 4) {

            @Override public boolean intersect(Circle c) {
                assertTrue(true);
                return super.intersect(c);
            }

            @Override public boolean intersect(Shape c) {
                assertTrue(false);
                return true;
            }

            @Override public boolean intersect(BBox c) {
                assertTrue(false);
                return true;
            }
        }.intersect(new Circle(1, 2, 3) {

            @Override public boolean intersect(Circle c) {
                assertTrue(false);
                return true;
            }

            @Override public boolean intersect(Shape b) {
                assertTrue(false);
                return true;
            }

            @Override public boolean intersect(BBox b) {
                assertTrue(true);
                return true;
            }
        });
    }
}
