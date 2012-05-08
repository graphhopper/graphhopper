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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class SpatialKeyHashtableOldTest {

    @Test
    public void testHashCollisionAndBucketOverflow() {
        SpatialKeyHashtableOld store = new SpatialKeyHashtableOld().init(1, 2);
        // force same bucket but different geohashcode
        writeAndCheck(store, 0, 8);
        writeAndCheck(store, 1, 8);
    }

    @Test
    public void testDifferentBucketsNoOverflow() {
        SpatialKeyHashtableOld store = new SpatialKeyHashtableOld().init(1, 2);
        // force same bucket but different geohashcode        
        writeAndCheck(store, 0, 4);
        writeAndCheck(store, 1, 4);
    }
    
    private void writeAndCheck(SpatialKeyHashtableOld store, long indexPartOfGeoHash, int maxEntries) {
        for (int i = 1; i <= maxEntries; i++) {
            assertEquals("write i:" + i, 0, store.put(indexPartOfGeoHash | i << 8, Integer.MAX_VALUE / i));
        }

        for (int i = 1; i <= maxEntries; i++) {
            assertEquals("check i:" + i, Integer.MAX_VALUE / i, store.get(indexPartOfGeoHash | i << 8));
        }
    }

    @Test
    public void testAdd() {
        SpatialKeyHashtableOld store = new SpatialKeyHashtableOld().init();
        assertEquals(0, store.put(12, 21, 123));
        assertEquals(123, store.get(12, 21));
        assertEquals(1, store.size());

        assertEquals(0, store.put(12, 21.0001f, 321));
        assertEquals(321, store.get(12, 21.0001f));
        assertEquals(2, store.size());
    }

    @Test
    public void testDuplicates() {
        SpatialKeyHashtableOld store = new SpatialKeyHashtableOld().init();
        assertEquals(0, store.put(12, 21, 123));
        assertEquals(1, store.size());
        assertEquals("location should already exist", 123, store.put(12, 21, 321));
        assertEquals(1, store.size());
    }
}
