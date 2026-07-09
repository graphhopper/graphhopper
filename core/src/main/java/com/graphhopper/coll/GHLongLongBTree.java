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
import java.util.function.LongUnaryOperator;

/**
 * An in-memory B-Tree with configurable value size (1-8 bytes). Delete not supported.
 * (Later we could use DataAccess to allow on-disc storage for very large data sets.)
 *
 * @author Peter Karich
 */
public class GHLongLongBTree implements LongLongMap {
    private final static Logger logger = LoggerFactory.getLogger(GHLongLongBTree.class);
    private final long emptyValue;
    private final int maxLeafEntries;
    private final int initLeafSize;
    private final int splitIndex;
    private final float factor;
    private long size;
    private int height;
    private BTreeEntry root;
    private final int bytesPerValue;
    private final long maxValue;

    // Scratch state used to make put/putOrCompute allocation-free: instead of allocating a
    // ReturnValue (and a byte[] for the old/new value) on every insert, the recursive BTreeEntry
    // methods write their results into these fields. Safe because writes are single-threaded
    // (the tree is already not thread-safe: put mutates root/size/height without synchronization).
    private BTreeEntry splitResult;   // a new sub tree to graft one level up, or null
    private boolean keyExisted;       // whether the key was already present (i.e. an update)
    private long oldValueResult;      // the previous value, valid only when keyExisted

    public GHLongLongBTree(int maxLeafEntries, int bytesPerValue, long emptyValue) {
        this.maxLeafEntries = maxLeafEntries;
        this.bytesPerValue = bytesPerValue;
        if (bytesPerValue > 8)
            throw new IllegalArgumentException("Values can have 8 bytes maximum but requested was " + bytesPerValue);
        this.emptyValue = emptyValue;

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
            // The `continue;` below looks pointless (an if/else would be equivalent), but it is a
            // ~30% speedup for this hot lookup. Why:
            //
            // This search is a *dependent-load chain*: the address of the next key we read
            // (keys[guess]) depends on the result of the current comparison. Written as a plain
            // if/else, both arms rejoin at a single "merge block" and the loop has one "back-edge"
            // (the jump from the bottom back up to the top for the next iteration). The JIT (C2)
            // recognises that split-then-merge diamond and "if-converts" it into a branchless
            // conditional-move (CMOV) - normally a good thing, since a CMOV can't be mis-predicted.
            //
            // But here branchless is SLOWER. The CMOV's output is the very index used to compute the
            // next load address, so each iteration cannot begin its load until the previous CMOV has
            // resolved: the whole loop serialises at one memory-load latency per step. A real branch
            // instead lets the CPU *predict* the direction and start the next load speculatively,
            // before the comparison resolves - so several iterations' loads are in flight at once
            // (memory-level parallelism), hiding the latency. For a cache-resident B-tree node that
            // overlap easily beats the occasional mis-prediction.
            //
            // The `continue;` is what keeps the branch: it gives the taken arm its own back-edge
            // (jump straight to the loop head) instead of routing through the shared merge block, so
            // C2 no longer sees a convertible diamond and leaves the branch in. (This is the entire
            // reason the Kotlin build's binarySearch was faster - kotlinc happens to emit this same
            // two-back-edge shape; -XX:ConditionalMoveLimit=0 reproduces the win on the plain
            // if/else form, confirming CMOV is the sole cause.) The logic is identical to if/else.
            if (keys[guess] >= key) {
                high = guess;
                continue;
            }
            low = guess;

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
        if (value == emptyValue)
            throw new IllegalArgumentException("Value cannot be the 'empty value' " + emptyValue);

        splitResult = null;
        keyExisted = false;
        root.put(key, value);
        if (splitResult != null) {
            height++;
            root = splitResult;
            splitResult = null;
        }
        if (!keyExisted) {
            // successfully inserted
            size++;
            if (size % 1000000 == 0)
                optimize();
            return emptyValue;
        }
        return oldValueResult;
    }

    @Override
    public long putOrCompute(long key, long valueIfAbsent, LongUnaryOperator computeIfPresent) {
        if (valueIfAbsent > maxValue)
            throw new IllegalArgumentException("Value " + valueIfAbsent + " exceeded max value: " + maxValue
                    + ". Increase bytesPerValue (" + bytesPerValue + ")");
        if (valueIfAbsent == emptyValue)
            throw new IllegalArgumentException("Value cannot be the 'empty value' " + emptyValue);

        splitResult = null;
        keyExisted = false;
        root.putOrCompute(key, valueIfAbsent, computeIfPresent);
        if (splitResult != null) {
            height++;
            root = splitResult;
            splitResult = null;
        }
        if (!keyExisted) {
            // successfully inserted (was absent)
            size++;
            if (size % 1000000 == 0)
                optimize();
            return emptyValue;
        }
        return oldValueResult;
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

    public long getEmptyValue() {
        return emptyValue;
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

    /**
     * Writes all entries into the given arrays in ascending key order (in-order traversal).
     * {@code outKeys}/{@code outValues} must be at least {@link #getSize()} long.
     *
     * @return the number of entries written
     */
    public int fillSorted(long[] outKeys, long[] outValues) {
        return root.fillSorted(outKeys, outValues, 0);
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
        void put(long key, long newValue) {
            int index = binarySearch(keys, 0, entrySize, key);
            if (index >= 0) {
                // update
                oldValueResult = toLong(values, index * bytesPerValue);
                keyExisted = true;
                // copy newValue to values
                fromLong(values, newValue, index * bytesPerValue);
                splitResult = null;
                return;
            }

            index = ~index;
            if (isLeaf || children[index] == null) {
                // insert
                keyExisted = false;
                BTreeEntry split = checkSplitEntry();
                if (split == null) {
                    insertKeyValue(index, key, newValue);
                } else if (index <= splitIndex) {
                    split.children[0].insertKeyValue(index, key, newValue);
                } else {
                    split.children[1].insertKeyValue(index - splitIndex - 1, key, newValue);
                }
                splitResult = split;
                return;
            }

            children[index].put(key, newValue);
            if (keyExisted) // only update happened below
                return;

            BTreeEntry childSplit = splitResult;
            if (childSplit != null) {
                // split this treeEntry if it is too big
                BTreeEntry split = checkSplitEntry();
                if (split == null) {
                    insertTree(index, childSplit);
                } else if (index <= splitIndex) {
                    split.children[0].insertTree(index, childSplit);
                } else {
                    split.children[1].insertTree(index - splitIndex - 1, childSplit);
                }

                splitResult = split;
            }
            // else childSplit == null: splitResult is already null from the insert below
        }

        /**
         * Like put, but uses valueIfAbsent when key is not found, and computeIfPresent when key is found.
         * This avoids a separate get+put traversal.
         */
        void putOrCompute(long key, long valueIfAbsent, LongUnaryOperator computeIfPresent) {
            int index = binarySearch(keys, 0, entrySize, key);
            if (index >= 0) {
                // key exists: compute new value from old value
                long oldLong = toLong(values, index * bytesPerValue);
                long newValue = computeIfPresent.applyAsLong(oldLong);
                if (newValue > maxValue)
                    throw new IllegalArgumentException("Computed value " + newValue + " exceeded max value: " + maxValue
                            + ". Increase bytesPerValue (" + bytesPerValue + ")");
                if (newValue == emptyValue)
                    throw new IllegalArgumentException("Computed value cannot be the 'empty value' " + emptyValue);
                fromLong(values, newValue, index * bytesPerValue);
                oldValueResult = oldLong;
                keyExisted = true;
                splitResult = null;
                return;
            }

            // key does not exist: insert valueIfAbsent
            index = ~index;
            if (isLeaf || children[index] == null) {
                // insert
                keyExisted = false;
                BTreeEntry split = checkSplitEntry();
                if (split == null) {
                    insertKeyValue(index, key, valueIfAbsent);
                } else if (index <= splitIndex) {
                    split.children[0].insertKeyValue(index, key, valueIfAbsent);
                } else {
                    split.children[1].insertKeyValue(index - splitIndex - 1, key, valueIfAbsent);
                }
                splitResult = split;
                return;
            }

            children[index].putOrCompute(key, valueIfAbsent, computeIfPresent);
            if (keyExisted) // only update happened below
                return;

            BTreeEntry childSplit = splitResult;
            if (childSplit != null) {
                // split this treeEntry if it is too big
                BTreeEntry split = checkSplitEntry();
                if (split == null) {
                    insertTree(index, childSplit);
                } else if (index <= splitIndex) {
                    split.children[0].insertTree(index, childSplit);
                } else {
                    split.children[1].insertTree(index - splitIndex - 1, childSplit);
                }

                splitResult = split;
            }
            // else childSplit == null: splitResult is already null from the insert below
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

        // in-order traversal -> entries in ascending key order
        int fillSorted(long[] outKeys, long[] outValues, int pos) {
            for (int i = 0; i < entrySize; i++) {
                if (!isLeaf && children[i] != null)
                    pos = children[i].fillSorted(outKeys, outValues, pos);
                outKeys[pos] = keys[i];
                outValues[pos] = toLong(values, i * bytesPerValue);
                pos++;
            }
            if (!isLeaf && children[entrySize] != null)
                pos = children[entrySize].fillSorted(outKeys, outValues, pos);
            return pos;
        }

        // writes the value directly from a long, avoiding a temporary byte[]
        // allocation on the (very hot) leaf-insert path.
        void insertKeyValue(int index, long key, long value) {
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
            fromLong(values, value, index * bytesPerValue);
            entrySize++;
        }

        void insertTree(int index, BTreeEntry tree) {
            insertKeyValue(index, tree.keys[0], toLong(tree.values, 0));
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
                return emptyValue;
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
