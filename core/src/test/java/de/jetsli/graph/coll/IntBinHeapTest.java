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
package de.jetsli.graph.coll;

import de.jetsli.graph.storage.WeightedEntry;
import java.util.PriorityQueue;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class IntBinHeapTest {

    @Test
    public void test0() {
        IntBinHeap binHeap = new IntBinHeap(100);
        binHeap.insert(0, 123);
        assertEquals(123, binHeap.peekMinPriority(), 1e-4);
        assertEquals(1, binHeap.size());

        binHeap.rekey(0, 12);
        assertEquals(12, binHeap.peekMinPriority(), 1e-4);
        assertEquals(1, binHeap.size());
    }

    @Test
    public void testBasic() {
        IntBinHeap binHeap = new IntBinHeap(100);
        binHeap.insert(1, 20);
        binHeap.insert(2, 123);
        binHeap.insert(3, 120);
        binHeap.insert(4, 130);

        assertEquals(1, binHeap.extractMin());
        assertEquals(3, binHeap.extractMin());
        assertEquals(2, binHeap.extractMin());
        assertEquals(1, binHeap.size());
    }

    @Test
    public void testClear() {
        IntBinHeap binHeap = new IntBinHeap(100);
        binHeap.insert(1, 20);
        binHeap.insert(2, 123);        
        assertEquals(2, binHeap.size());        
        binHeap.clear();
        
        assertEquals(0, binHeap.size());
        binHeap.insert(2, 123); 
        assertEquals(1, binHeap.size());  
        assertEquals(2, binHeap.extractMin());
    }

    @Test
    public void testRekey() {
        IntBinHeap binHeap = new IntBinHeap(100);
        binHeap.insert(1, 20);
        binHeap.insert(2, 123);
        binHeap.rekey(2, 100);
        binHeap.insert(3, 120);
        binHeap.insert(4, 130);

        assertEquals(1, binHeap.extractMin());
        assertEquals(2, binHeap.extractMin());
        assertEquals(2, binHeap.size());
    }

    @Test
    public void testSize() {
        PriorityQueue<WeightedEntry> juQueue = new PriorityQueue<WeightedEntry>(100);
        IntBinHeap binHeap = new IntBinHeap(100);

        Random rand = new Random(1);
        int N = 1000;
        for (int i = 0; i < N; i++) {
            float val = rand.nextFloat();
            binHeap.insert(i, val);
            juQueue.add(new WeightedEntry(i, val));
        }

        assertEquals(juQueue.size(), binHeap.size());

        for (int i = 0; i < N; i++) {
            assertEquals(juQueue.poll().node, binHeap.extractMin(), 1e-5);
        }

        assertEquals(binHeap.size(), 0);
    }
}
