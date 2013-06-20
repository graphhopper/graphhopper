/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.coll;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class GHDijkstraHeapTest extends AbstractBinHeapTest {

    @Override
    public BinHeapWrapper<Number, Integer> createHeap(int capacity) {
        return new GHDijkstraHeap(capacity / 5);
    }

    @Test
    public void testMove() {
        IntDoubleBinHeap from = new IntDoubleBinHeap();
        from.insert(100, 101);
        from.insert(50, 51);
        from.insert(70, 71);
        from.insert(30, 31);
        for (int i = 0; i < 20; i++) {
            from.insert(i * 10, i * 11);
        }
        from.insert(59, 61);
        from.insert(160, 161);
        IntDoubleBinHeap to = new IntDoubleBinHeap();
        to.insert(99, 91);

        assertEquals(26, from.size());
        assertEquals(1, to.size());

        from = GHDijkstraHeap.move(20, from, to);
        assertEquals(13, from.size());
        assertEquals(14, to.size());

        assertEquals("0.0, 10.0, 20.0, 30.0, 30.0, 40.0, 50.0, 50.0, 59.0, 60.0, 70.0, 70.0, 80.0", from.toKeyString());
        assertEquals("90.0, 99.0, 100.0, 100.0, 110.0, 120.0, 130.0, 140.0, "
                + "150.0, 160.0, 160.0, 170.0, 180.0, 190.0", to.toKeyString());
    }
//    @Test
//    public void testTodoUpdate() {
//        MyDijkstraHeap heap = new MyDijkstraHeap(4, 16, 100);
//        heap.insert(1.1, 1);
//        heap.insert(1.2, 2);
//        heap.insert(1.3, 3);
//        heap.insert(1.4, 4);
//        heap.insert(1.5, 5);
//        heap.update_(1.5, 1.0, 5);
//        assertEquals(1.0, heap.peek_key(), 1e-6);
//        assertEquals(5, heap.peek_element());
//    }
}
