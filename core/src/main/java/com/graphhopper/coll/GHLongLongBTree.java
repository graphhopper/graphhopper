/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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

import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * An in-memory B-Tree with configurable value size (1-8 bytes). Delete not supported.
 * (Later we could use DataAccess to allow on-disc storage for very large data sets.)
 *
 * @author Peter Karich
 */
public class GHLongLongBTree implements LongLongMap {
    private final static Logger logger = LoggerFactory.getLogger(GHLongLongBTree.class);
    private final int noNumberValue = -1;
    private final int maxLeafEntries;
    private final int initLeafSize;
    private final int splitIndex;
    private final float factor;
    private long size;
    private int height;
    private BTreeEntry root;
    private final int bytesPerValue;
    private final long maxValue;

    public GHLongLongBTree(int maxLeafEntries, int bytesPerValue) {
        this.maxLeafEntries = maxLeafEntries;
        this.bytesPerValue = bytesPerValue;
        if (bytesPerValue > 8)
            throw new IllegalArgumentException("Values can have 8 bytes maximum but requested was " + bytesPerValue);

        // reserve one bit for negative values
        this.maxValue = (1L << (bytesPerValue * 8 - 1)) - 1;
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

    static int binarySearch(long[] keys, int start, int len, long key) {
        int high = start + len, low = start - 1, guess;
        while (high - low > 1) {
            // use >>> for average or we could get an integer overflow.
            guess = (high + low) >>> 1;
            long guessedKey = keys[guess];
            if (guessedKey < key) {
                low = guess;
            } else {
                high = guess;
            }
        }

        if (high == start + len) {
            return ~(start + len);
        }

        long highKey = keys[high];
        if (highKey == key) {
            return high;
        } else {
            return ~high;
        }
    }

    @Override
    public long put(long key, long value) {
        if (value > maxValue)
            throw new IllegalArgumentException("Value " + value + " exceeded max value: " + maxValue
                    + ". Increase bytesPerValue (" + bytesPerValue + ")");
        if (value == noNumberValue)
            throw new IllegalArgumentException("Value cannot be no_number_value " + noNumberValue);

        ReturnValue rv = root.put(key, value);
        if (rv.tree != null) {
            height++;
            root = rv.tree;
        }
        if (rv.oldValue == null) {
            // successfully inserted
            size++;
            if (size % 1000000 == 0)
                optimize();
        }
        return rv.oldValue == null ? noNumberValue : toLong(rv.oldValue);
    }

    @Override
    public long get(long key) {
        return root.get(key);
    }

    int height() {
        return height;
    }

    @Override
    public long getSize() {
        return size;
    }

    /**
     * @return memory usage in MB
     */
    @Override
    public int getMemoryUsage() {
        return Math.round(root.getCapacity() / Helper.MB);
    }

    @Override
    public void clear() {
        size = 0;
        height = 1;
        root = new BTreeEntry(initLeafSize, true);
    }

    int getNoNumberValue() {
        return noNumberValue;
    }

    private int getEntries() {
        return root.getEntries();
    }

    @Override
    public void optimize() {
        if (getSize() > 10000) {
//            StopWatch sw = new StopWatch().start();
//            int old = memoryUsage();
            root.compact();
//            logger.info(size + "| osmIdMap.optimize took: " + sw.stop().getSeconds()
//                    + " => freed: " + (old - memoryUsage()) + "MB");
        }
    }

    @Override
    public String toString() {
        return "Height:" + height() + ", entries:" + getEntries();
    }

    @Override
    public long getMaxValue() {
        return maxValue;
    }

    void print() {
        logger.info(root.toString(1));
    }

    static class ReturnValue {
        byte[] oldValue;
        BTreeEntry tree;

        public ReturnValue(byte[] oldValue) {
            this.oldValue = oldValue;
        }
    }

    long toLong(byte[] b) {
        return toLong(b, 0);
    }

    long toLong(byte[] bytes, int offset) {
        long res = 0;
        if (bytesPerValue == 8) res |= (long) bytes[offset + 7] << 56;
        else if (bytesPerValue > 7) res |= ((long) bytes[offset + 7] << 56);

        if (bytesPerValue == 7) res |= (long) bytes[offset + 6] << 48;
        else if (bytesPerValue > 6) res |= ((long) bytes[offset + 6] & 0xFF) << 48;

        if (bytesPerValue == 6) res |= (long) bytes[offset + 5] << 40;
        else if (bytesPerValue > 5) res |= ((long) bytes[offset + 5] & 0xFF) << 40;

        if (bytesPerValue == 5) res |= (long) bytes[offset + 4] << 32;
        else if (bytesPerValue > 4) res |= ((long) bytes[offset + 4] & 0xFF) << 32;

        if (bytesPerValue == 4) res |= (long) bytes[offset + 3] << 24;
        else if (bytesPerValue > 3) res |= ((long) bytes[offset + 3] & 0xFF) << 24;

        if (bytesPerValue == 3) res |= (long) bytes[offset + 2] << 16;
        else if (bytesPerValue > 2) res |= ((long) bytes[offset + 2] & 0xFF) << 16;

        if (bytesPerValue == 2) res |= (long) bytes[offset + 1] << 8;
        else if (bytesPerValue > 1) res |= ((long) bytes[offset + 1] & 0xFF) << 8;

        res |= ((long) bytes[offset] & 0xff);
        return res;
    }

    final byte[] fromLong(long value) {
        byte[] bytes = new byte[bytesPerValue];
        fromLong(bytes, value, 0);
        return bytes;
    }

    final void fromLong(byte[] bytes, long value, int offset) {
        if (bytesPerValue > 7) bytes[offset + 7] = (byte) (value >> 56);
        if (bytesPerValue > 6) bytes[offset + 6] = (byte) (value >> 48);
        if (bytesPerValue > 5) bytes[offset + 5] = (byte) (value >> 40);
        if (bytesPerValue > 4) bytes[offset + 4] = (byte) (value >> 32);
        if (bytesPerValue > 3) bytes[offset + 3] = (byte) (value >> 24);
        if (bytesPerValue > 2) bytes[offset + 2] = (byte) (value >> 16);
        if (bytesPerValue > 1) bytes[offset + 1] = (byte) (value >> 8);
        bytes[offset] = (byte) (value);
    }

    class BTreeEntry {
        int entrySize;
        long[] keys;
        byte[] values;
        BTreeEntry[] children;
        boolean isLeaf;

        public BTreeEntry(int tmpSize, boolean leaf) {
            this.isLeaf = leaf;
            keys = new long[tmpSize];
            values = new byte[tmpSize * bytesPerValue];

            if (!isLeaf) {
                // in a b-tree we need one more entry to point to all children!
                children = new BTreeEntry[tmpSize + 1];
            }
        }

        /**
         * @return the old value which was associated with the specified key or if no update it
         * returns noNumberValue
         */
        ReturnValue put(long key, long newValue) {
            int index = binarySearch(keys, 0, entrySize, key);
            if (index >= 0) {
                // update
                byte[] oldValue = new byte[bytesPerValue];
                System.arraycopy(values, index * bytesPerValue, oldValue, 0, bytesPerValue);
                // copy newValue to values
                fromLong(values, newValue, index * bytesPerValue);
                return new ReturnValue(oldValue);
            }

            index = ~index;
            ReturnValue downTreeRV;
            if (isLeaf || children[index] == null) {
                // insert
                downTreeRV = new ReturnValue(null);
                downTreeRV.tree = checkSplitEntry();
                if (downTreeRV.tree == null) {
                    insertKeyValue(index, key, fromLong(newValue));
                } else if (index <= splitIndex) {
                    downTreeRV.tree.children[0].insertKeyValue(index, key, fromLong(newValue));
                } else {
                    downTreeRV.tree.children[1].insertKeyValue(index - splitIndex - 1, key, fromLong(newValue));
                }
                return downTreeRV;
            }

            downTreeRV = children[index].put(key, newValue);
            if (downTreeRV.oldValue != null) // only update
                return downTreeRV;

            if (downTreeRV.tree != null) {
                // split this treeEntry if it is too big
                BTreeEntry returnTree, downTree = returnTree = checkSplitEntry();
                if (downTree == null) {
                    insertTree(index, downTreeRV.tree);
                } else if (index <= splitIndex) {
                    downTree.children[0].insertTree(index, downTreeRV.tree);
                } else {
                    downTree.children[1].insertTree(index - splitIndex - 1, downTreeRV.tree);
                }

                downTreeRV.tree = returnTree;
            }
            return downTreeRV;
        }

        /**
         * @return null if nothing to do or a new sub tree if this tree capacity is no longer
         * sufficient.
         */
        BTreeEntry checkSplitEntry() {
            if (entrySize < maxLeafEntries) {
                return null;
            }

            // right child: copy from this
            int count = entrySize - splitIndex - 1;
            BTreeEntry newRightChild = new BTreeEntry(Math.max(initLeafSize, count), isLeaf);
            copy(this, newRightChild, splitIndex + 1, count);

            // left child: copy from this
            // avoid: http://stackoverflow.com/q/15897869/194609
            BTreeEntry newLeftChild = new BTreeEntry(Math.max(initLeafSize, splitIndex), isLeaf);
            copy(this, newLeftChild, 0, splitIndex);

            // new tree pointing to left + right tree only
            BTreeEntry newTree = new BTreeEntry(1, false);
            newTree.entrySize = 1;
            newTree.keys[0] = this.keys[splitIndex];

            System.arraycopy(this.values, splitIndex * bytesPerValue, newTree.values, 0, bytesPerValue);
            newTree.children[0] = newLeftChild;
            newTree.children[1] = newRightChild;
            return newTree;
        }

        void copy(BTreeEntry fromChild, BTreeEntry toChild, int from, int count) {
            System.arraycopy(fromChild.keys, from, toChild.keys, 0, count);
            System.arraycopy(fromChild.values, from * bytesPerValue, toChild.values, 0, count * bytesPerValue);
            if (!fromChild.isLeaf) {
                System.arraycopy(fromChild.children, from, toChild.children, 0, count + 1);
            }

            toChild.entrySize = count;
        }

        void insertKeyValue(int index, long key, byte[] newValueFromIdx0) {
            ensureSize(entrySize + 1);
            int count = entrySize - index;
            if (count > 0) {
                System.arraycopy(keys, index, keys, index + 1, count);
                System.arraycopy(values, index * bytesPerValue, values, index * bytesPerValue + bytesPerValue, count * bytesPerValue);
                if (!isLeaf) {
                    System.arraycopy(children, index + 1, children, index + 2, count);
                }
            }

            keys[index] = key;
            System.arraycopy(newValueFromIdx0, 0, values, index * bytesPerValue, bytesPerValue);
            entrySize++;
        }

        void insertTree(int index, BTreeEntry tree) {
            insertKeyValue(index, tree.keys[0], tree.values);
            if (!isLeaf) {
                // overwrite children
                children[index] = tree.children[0];
                // set
                children[index + 1] = tree.children[1];
            }
        }

        long get(long key) {
            int index = binarySearch(keys, 0, entrySize, key);
            if (index >= 0) {
                return toLong(values, index * bytesPerValue);
            }
            index = ~index;
            if (isLeaf || children[index] == null) {
                return noNumberValue;
            }
            return children[index].get(key);
        }

        /**
         * @return used bytes
         */
        long getCapacity() {
            long cap = keys.length * (8 + 4) + 3 * 12 + 4 + 1;
            if (!isLeaf) {
                cap += children.length * 4;
                for (int i = 0; i < children.length; i++) {
                    if (children[i] != null) {
                        cap += children[i].getCapacity();
                    }
                }
            }
            return cap;
        }

        int getEntries() {
            int entries = 1;
            if (!isLeaf) {
                for (int i = 0; i < children.length; i++) {
                    if (children[i] != null) {
                        entries += children[i].getEntries();
                    }
                }
            }
            return entries;
        }

        void ensureSize(int size) {
            if (size <= keys.length) {
                return;
            }
            int newSize = Math.min(maxLeafEntries, Math.max(size + 1, Math.round(size * factor)));
            keys = Arrays.copyOf(keys, newSize);
            values = Arrays.copyOf(values, newSize * bytesPerValue);
            if (!isLeaf) {
                children = Arrays.copyOf(children, newSize + 1);
            }
        }

        void compact() {
            int tolerance = 1;
            if (entrySize + tolerance < keys.length) {
                keys = Arrays.copyOf(keys, entrySize);
                values = Arrays.copyOf(values, entrySize * bytesPerValue);
                if (!isLeaf) {
                    children = Arrays.copyOf(children, entrySize + 1);
                }
            }

            if (!isLeaf) {
                for (int i = 0; i < children.length; i++) {
                    if (children[i] != null) {
                        children[i].compact();
                    }
                }
            }
        }

        String toString(int height) {
            String str = height + ": ";
            for (int i = 0; i < entrySize; i++) {
                if (i > 0) {
                    str += ",";
                }
                str += keys[i];
            }
            str += "\n";
            if (!isLeaf) {
                for (int i = 0; i < entrySize + 1; i++) {
                    if (children[i] != null) {
                        str += children[i].toString(height + 1) + "| ";
                    }
                }
            }
            return str;
        }
    }
}
