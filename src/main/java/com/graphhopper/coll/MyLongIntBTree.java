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
package com.graphhopper.coll;

import com.graphhopper.util.Helper;
import java.util.Arrays;

/**
 * A HashMap which can be stored in RAM or on disc, using open addressing
 * hashing. In combination with MMapDirectory and BigLongIntMap this can be used
 * for very large data sets.
 *
 * @see http://en.wikipedia.org/wiki/Hash_table#Open_addressing
 * @author Peter Karich
 */
public class MyLongIntBTree {

    private final int noNumberValue = -1;
    private long size;
    private int maxLeafEntries;
    private int initLeafSize;
    private int splitIndex;
    private float factor;
    private int height;
    private BTreeEntry root;

    public MyLongIntBTree(int maxLeafEntries) {
        this.maxLeafEntries = maxLeafEntries;
        if (maxLeafEntries < 1)
            throw new IllegalArgumentException("illegal maxLeafEntries:" + maxLeafEntries);
        if (maxLeafEntries % 2 == 0)
            maxLeafEntries++;

        splitIndex = maxLeafEntries / 2;
        if (maxLeafEntries < 10) {
            factor = 2;
            initLeafSize = 1;
        } else if (maxLeafEntries < 20) {
            factor = 2;
            initLeafSize = 4;
        } else {
            factor = 1.7f;
            initLeafSize = maxLeafEntries / 10;
        }
        clear();
    }

    public int put(long key, int value) {
        if (key == noNumberValue)
            throw new IllegalArgumentException("Illegal key " + key);

        ReturnValue rv = root.put(key, value);
        if (rv.tree != null) {
            height++;
            root = rv.tree;
        }
        if (rv.oldValue == noNumberValue)
            // successfully inserted
            size++;
        return rv.oldValue;
    }

    public int get(long key) {
        return root.get(key);
    }

    int height() {
        return height;
    }

    public long size() {
        return size;
    }

    /**
     * @return memory usage in MB
     */
    public int memoryUsage() {
        return Math.round(root.capacity() / Helper.MB);
    }

    void clear() {
        size = 0;
        height = 1;
        root = new BTreeEntry(initLeafSize);
    }

    int getNoNumberValue() {
        return noNumberValue;
    }

    void flush() {
        // TODO set headers with prefix, loadFactor etc
//        keys.flush();
//        values.flush();
        throw new IllegalStateException("not supported yet");
    }

    private int entries() {
        return root.entries();
    }

    static class ReturnValue {

        int oldValue;
        BTreeEntry tree;

        public ReturnValue() {
        }

        public ReturnValue(int oldValue) {
            this.oldValue = oldValue;
        }
    }

    class BTreeEntry {

        int entrySize;
        long keys[];
        int values[];
        BTreeEntry children[];

        public BTreeEntry(int tmpSize) {
            keys = new long[tmpSize];
            values = new int[tmpSize];
            // in a b-tree we need one more entry to point to all children!
            children = new BTreeEntry[tmpSize + 1];
        }

        /**
         * @return the old value which was associated with the specified key or
         * if no update it returns noNumberValue
         */
        ReturnValue put(long key, int newValue) {
            int index = binarySearch(keys, 0, entrySize, key);
            if (index >= 0) {
                // update
                int oldValue = values[index];
                values[index] = newValue;
                return new ReturnValue(oldValue);
            }

            index = ~index;
            ReturnValue downTreeRV;
            if (children[index] == null) {
                // insert
                downTreeRV = new ReturnValue(noNumberValue);
                downTreeRV.tree = checkSplitEntry();
                if (downTreeRV.tree == null) {
                    insertKeyValue(index, key, newValue);
                } else {
                    if (index <= splitIndex)
                        downTreeRV.tree.children[0].insertKeyValue(index, key, newValue);
                    else
                        downTreeRV.tree.children[1].insertKeyValue(index - splitIndex - 1, key, newValue);
                }
                return downTreeRV;
            }

            downTreeRV = children[index].put(key, newValue);
            if (downTreeRV.oldValue != noNumberValue)
                // only update
                return downTreeRV;

            if (downTreeRV.tree != null) {
                // split this treeEntry if it is too big
                BTreeEntry returnTree, downTree = returnTree = checkSplitEntry();
                if (downTree == null)
                    insertTree(index, downTreeRV.tree);
                else {
                    if (index <= splitIndex) {
                        downTree.children[0].insertTree(index, downTreeRV.tree);
                    } else {
                        downTree.children[1].insertTree(index - splitIndex - 1, downTreeRV.tree);
                    }
                }

                downTreeRV.tree = returnTree;
            }
            return downTreeRV;
        }

        /**
         * @return null if nothing to do or a new sub tree if this tree capacity
         * is no longer sufficient.
         */
        BTreeEntry checkSplitEntry() {
            if (entrySize < maxLeafEntries)
                return null;

            // right child: copy from this
            int count = entrySize - splitIndex - 1;
            BTreeEntry newRightChild = new BTreeEntry(Math.max(initLeafSize, count));
            System.arraycopy(keys, splitIndex + 1, newRightChild.keys, 0, count);
            System.arraycopy(values, splitIndex + 1, newRightChild.values, 0, count);
            System.arraycopy(children, splitIndex + 1, newRightChild.children, 0, count + 1);

            newRightChild.entrySize = count;

            // left child: just copy pointer and decrease size to index
            BTreeEntry newLeftChild = this;
            newLeftChild.entrySize = splitIndex;

            // new tree pointing to left + right tree only
            BTreeEntry newTree = new BTreeEntry(1);
            newTree.entrySize = 1;
            newTree.keys[0] = this.keys[splitIndex];
            newTree.values[0] = this.values[splitIndex];
            newTree.children[0] = newLeftChild;
            newTree.children[1] = newRightChild;
            return newTree;
        }

        void insertKeyValue(int index, long key, int newValue) {
            ensureSize(entrySize + 1);
            int count = entrySize - index;
            if (count > 0) {
                System.arraycopy(keys, index, keys, index + 1, count);
                System.arraycopy(values, index, values, index + 1, count);
                System.arraycopy(children, index + 1, children, index + 2, count);
            }

            keys[index] = key;
            values[index] = newValue;
            entrySize++;
        }

        void insertTree(int index, BTreeEntry tree) {
            insertKeyValue(index, tree.keys[0], tree.values[0]);
            // overwrite children
            children[index] = tree.children[0];
            // set
            children[index + 1] = tree.children[1];
        }

        int get(long key) {
            int index = binarySearch(keys, 0, entrySize, key);
            if (index >= 0)
                return values[index];
            index = ~index;
            if (children[index] == null)
                return noNumberValue;
            return children[index].get(key);
        }

        /**
         * @return used bytes
         */
        long capacity() {
            long cap = keys.length * (8 + 4 + 4) + 3 * 12 + 4;
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null)
                    cap += children[i].capacity();
            }
            return cap;
        }

        int entries() {
            int entries = 1;
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null)
                    entries += children[i].entries();
            }
            return entries;
        }

        void ensureSize(int size) {
            if (size <= keys.length)
                return;
            int newSize = Math.min(maxLeafEntries, Math.max(size + 1, Math.round(size * factor)));
            keys = Arrays.copyOf(keys, newSize);
            values = Arrays.copyOf(values, newSize);
            children = Arrays.copyOf(children, newSize + 1);
        }

        void compact() {
            int tolerance = 1;
            if (entrySize + tolerance >= keys.length)
                return;
            keys = Arrays.copyOf(keys, entrySize);
            values = Arrays.copyOf(values, entrySize);
            children = Arrays.copyOf(children, entrySize + 1);
        }

        String toString(int height) {
            String str = height + ": ";
            for (int i = 0; i < entrySize; i++) {
                if (i > 0)
                    str += ",";
                if (keys[i] == noNumberValue)
                    str += "-";
                else
                    str += keys[i];
            }
            str += "\n";
            for (int i = 0; i < entrySize + 1; i++) {
                if (children[i] != null)
                    str += children[i].toString(height + 1) + "| ";
            }
            return str;
        }
    }

    @Override
    public String toString() {
        return "Height:" + height() + ", entries:" + entries();
    }

    void print() {
        System.out.println(root.toString(1));
    }

    // LATER: see OSMIDMap for a version where we use DataAccess
    static int binarySearch(long keys[], int start, int len, long key) {
        int high = start + len, low = start - 1, guess;
        while (high - low > 1) {
            guess = (high + low) >>> 1;
            long guessedKey = keys[guess];
            if (guessedKey < key)
                low = guess;
            else
                high = guess;
        }

        if (high == start + len)
            return ~(start + len);

        long highKey = keys[high];
        if (highKey == key)
            return high;
        else
            return ~high;
    }
}
