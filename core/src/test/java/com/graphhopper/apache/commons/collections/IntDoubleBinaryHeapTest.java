package com.graphhopper.apache.commons.collections;

import com.graphhopper.coll.AbstractBinHeapTest;
import com.graphhopper.coll.BinHeapWrapper;

/**
 * @author Peter Karich
 */
public class IntDoubleBinaryHeapTest extends AbstractBinHeapTest {
    @Override
    public BinHeapWrapper<Number, Integer> createHeap(int capacity) {
        return new IntDoubleBinaryHeap(capacity);
    }
}
