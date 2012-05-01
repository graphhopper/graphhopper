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

import java.util.Random;
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
    public void testBucketIndex() throws Exception {
        for (int i = 9; i < 20; i += 3) {
            SpatialKeyTree tree = createSKTWithoutBuffer(i);
            SpatialKeyAlgo algo = tree.getAlgo();
            Random rand = new Random();
            for (int j = 0; j < 10000; j++) {
                double lat = rand.nextDouble() * 5;
                double lon = rand.nextDouble() * 5;
                try {
                    tree.getBucketIndex(algo.encode(lat, lon));
                } catch (Exception ex) {
                    assertFalse("Problem while " + lat + "," + lon + " " + ex.getMessage(), false);
                }
            }
        }
    }

    SpatialKeyTree createSKTWithoutBuffer(int i) {
        return new SpatialKeyTree(i) {

            @Override protected void initBuffers() {
            }
        };
    }
}
