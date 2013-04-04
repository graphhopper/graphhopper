/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage.index;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.IntIterator;
import com.graphhopper.storage.Storable;
import gnu.trove.list.array.TIntArrayList;

/**
 * List of List<Integer>
 *
 * @author Peter Karich
 */
class ListOfArrays implements Storable<ListOfArrays> {

    private DataAccess refs;
    private DataAccess entries;
    // not necessary but use the common law: "avoid valid references being 0"
    private int nextArrayPointer = 1;

    public ListOfArrays(Directory dir, String listName) {
        this.refs = dir.findCreate(listName + "refs");
        this.entries = dir.findCreate(listName + "entries");
    }

    @Override
    public boolean loadExisting() {
        if (refs.loadExisting()) {
            if (!entries.loadExisting())
                throw new IllegalStateException("corrupted files or incompatible graphhopper versions?");
            return true;
        }
        return false;
    }

    @Override
    public ListOfArrays create(long size) {
        refs.create((long) size * 4);
        entries.create((long) size * 4);
        return this;
    }

    public void setSameReference(int indexTo, int indexFrom) {
        refs.setInt(indexTo, refs.getInt(indexFrom));
    }

    public void set(int index, TIntArrayList list) {
        int tmpPointer = nextArrayPointer;
        refs.setInt(index, nextArrayPointer);
        // reserver the integers and one integer for the size
        int len = list.size();
        nextArrayPointer += len + 1;
        entries.ensureCapacity((nextArrayPointer + 1) * 4);
        entries.setInt(tmpPointer, len);
        for (int i = 0; i < len; i++) {
            tmpPointer++;
            entries.setInt(tmpPointer, list.get(i));
        }
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

    @Override
    public long capacity() {
        return refs.capacity() + entries.capacity();
    }

    public void setHeader(int index, int value) {
        refs.setHeader(index, value);
    }

    public int getHeader(int index) {
        return refs.getHeader(index);
    }

    @Override
    public void flush() {
        refs.flush();
        entries.flush();
    }

    @Override
    public void close() {
        refs.close();
        entries.close();
    }
}
