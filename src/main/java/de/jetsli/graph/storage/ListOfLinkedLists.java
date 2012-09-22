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
package de.jetsli.graph.storage;

/**
 * An expandable list which stores one linked list (for integer values) per entry.
 *
 * @author Peter Karich
 */
//
// refs          buckets
//               
// |0--------->  |    ... one bucket contains multiple integers
// |             |
// |1----|       |--| nextBucket for ref 0
// |     |---->  |  |
// |             |  /
//               |<-
// ..
public class ListOfLinkedLists {

    private int noValue;
    private DataAccess refs;
    private DataAccess buckets;
    private boolean ignoreDuplicates = true;
    private int integersPerBucket = 1;
    private int nextBucketPointer;

    public ListOfLinkedLists(Directory dir, String listName, int integers) {
        this(dir.createDataAccess(listName + "refs"), dir.createDataAccess(listName + "buckets"), integers);
    }

    public ListOfLinkedLists(DataAccess refs, DataAccess buckets, int integers) {
        this.refs = refs;
        this.buckets = buckets;
        setNoValue(-1);
        refs.ensureCapacity(integers * 4);        
        buckets.ensureCapacity(integers * 4);        
    }

    public ListOfLinkedLists setIntegersPerBucket(int integersPerBucket) {
        this.integersPerBucket = integersPerBucket;
        return this;
    }

    public void setNoValue(int noValue) {
        this.noValue = noValue;
        refs.setNoValue(noValue);
        buckets.setNoValue(noValue);
    }

    public void add(int index, int value) {
        refs.ensureCapacity((index + 1) * 4);
        int pointer = refs.getInt(index);
        if (pointer == noValue) {
            pointer = nextEntryPointer();
            refs.setInt(index, pointer);
        } else {
            // find empty position
            pointer = findEmptyValue(pointer, value);
        }

        buckets.setInt(pointer, value);
    }

    public IntIterator getIterator(final int index) {
        refs.ensureCapacity((index + 1) * 4);
        return new IntIterator() {
            int pointer = refs.getInt(index);
            int offset = 0;
            int value;

            @Override public boolean next() {
                while (true) {
                    if (pointer == noValue)
                        return false;
                    value = buckets.getInt(pointer + offset);
                    if (value == noValue)
                        return false;

                    if (offset >= integersPerBucket) {
                        pointer = value;
                        offset = 0;
                        continue;
                    }

                    offset++;
                    return true;
                }
            }

            @Override public int value() {
                return value;
            }

            @Override public void remove() {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };
    }

    private int nextEntryPointer() {
        // add one further integer to store a link at the end of every bucket
        nextBucketPointer += integersPerBucket + 1;
        buckets.ensureCapacity((nextBucketPointer + 1) * 4);
        return nextBucketPointer;
    }

    private int findEmptyValue(int pointer, int addValue) {
        int i = pointer;
        while (true) {
            int end = pointer + integersPerBucket;
            for (; i < end; i++) {
                int value = buckets.getInt(i);
                if (value == noValue)
                    return i;

                if (ignoreDuplicates && value == addValue)
                    return i;
            }
            pointer = buckets.getInt(i);
            if (pointer == noValue) {
                int ep = nextEntryPointer();
                buckets.setInt(i, ep);
                return ep;
            }
        }
    }

    public int size() {
        return refs.capacity() / 4;
    }

    public float calcMemInMB() {
        return (float) (refs.capacity() + buckets.capacity()) / (1 << 20);
    }

    public void flush() {
        // TODO HOW TO MAKE this persistent!?
        // private int nextBucketPointer;
        refs.flush();
        buckets.flush();
    }
}
