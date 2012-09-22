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

import gnu.trove.list.array.TIntArrayList;

/**
 * @author Peter Karich
 */
public class ListOfArrays {

    private int headerInfos = 1;
    private DataAccess refs;
    private DataAccess entries;
    private int nextArrayPointer = headerInfos;
    private int currentSize;

    public ListOfArrays(Directory dir, String listName, int integers) {
        this(dir.createDataAccess(listName + "refs"), dir.createDataAccess(listName + "buckets"), integers);
    }

    public ListOfArrays(DataAccess refs, DataAccess entries, int integers) {
        this.refs = refs;
        this.entries = entries;
        refs.ensureCapacity(integers * 4);
        entries.ensureCapacity(integers * 4);
    }

    public void setSameReference(int indexFrom, int indexTo) {
        int max = Math.max(indexFrom, indexTo) + 1;
        refs.ensureCapacity(max * 4);
        refs.setInt(indexTo, refs.getInt(indexFrom));
    }

    public void add(TIntArrayList list) {
        refs.ensureCapacity((currentSize + 1) * 4);
        int tmpPointer = nextArrayPointer;
        refs.setInt(currentSize, nextArrayPointer);
        // reserver the integers and one integer for the size
        nextArrayPointer += list.size() + 1;
        entries.ensureCapacity((nextArrayPointer + 1) * 4);

        int len = list.size();
        entries.setInt(tmpPointer, len);
        for (int i = 0; i < len; i++) {
            tmpPointer++;
            entries.setInt(tmpPointer, list.get(i));
        }

        currentSize++;
        entries.setInt(0, currentSize);
    }

    public IntIterator getIterator(final int index) {
        final int pointer = refs.getInt(index);
        int size = entries.getInt(pointer);
        final int end = pointer + size;
        return new IntIterator() {
            int tmpPointer = pointer;
            int value;

            @Override public boolean next() {
                tmpPointer++;
                if (tmpPointer > end)
                    return false;
                value = entries.getInt(tmpPointer);                
                return true;
            }

            @Override public int value() {
                return value;
            }

            @Override public void remove() {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };
    }

    public int size() {
        return currentSize;
    }

    public int capacity() {
        return refs.capacity() + entries.capacity();
    }

    public void flush() {
        refs.flush();
        entries.flush();
    }
}
