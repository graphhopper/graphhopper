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
package de.jetsli.graph.geohash;

import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class SpatialKeyTreeTest {
//extends QuadTreeTester {
//
//    @Override
//    protected QuadTree<Integer> createQuadTree(int items) {
//        try {
//            return new SpatialKeyTree().init(items);
//        } catch (Exception ex) {
//            throw new RuntimeException(ex);
//        }
//    }

    @Test
    public void testMaxBuckets() throws Exception {
        for (int i = 0; i < 24; i += 3) {
            int maxBuckets = createSKTWithoutBuffer(i).init(2000000).getMaxBuckets();
            assertTrue(i + " " + maxBuckets, maxBuckets > 600000);
            assertTrue(i + " " + maxBuckets, maxBuckets < 700000);

            maxBuckets = createSKTWithoutBuffer(i).init(10000000).getMaxBuckets();
            assertTrue(i + " " + maxBuckets, maxBuckets > 3000000);
            assertTrue(i + " " + maxBuckets, maxBuckets < 4000000);

            maxBuckets = createSKTWithoutBuffer(i).init(100000000).getMaxBuckets();
            assertTrue(i + " " + maxBuckets, maxBuckets > 30000000);
            assertTrue(i + " " + maxBuckets, maxBuckets < 40000000);
        }
    }

    SpatialKeyTree createSKTWithoutBuffer(int i) {
        return new SpatialKeyTree(i) {

            @Override protected void initBuffers() {
            }
        };
    }
}
