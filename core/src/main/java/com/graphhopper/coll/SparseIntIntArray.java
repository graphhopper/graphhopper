/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.coll;

import com.graphhopper.util.Helper;

/**
 * Copied from Android project. android.util.SparseArray.java
 * <p>
 * SparseArrays map ints to ints. Unlike a normal array of ints, there can be gaps in the indices.
 */
public class SparseIntIntArray {
    private static final int DELETED = Integer.MIN_VALUE;
    private boolean mGarbage = false;
    private int[] mKeys;
    private int[] mValues;
    private int mSize;

    /**
     * Creates a new SparseIntIntArray containing no mappings.
     */
    public SparseIntIntArray() {
        this(10);
    }

    /**
     * Creates a new SparseIntIntArray containing no mappings that will not require any additional
     * memory allocation to store the specified number of mappings.
     */
    public SparseIntIntArray(int cap) {
        try {
            cap = Helper.idealIntArraySize(cap);
            mKeys = new int[cap];
            mValues = new int[cap];
            mSize = 0;
        } catch (OutOfMemoryError err) {
            System.err.println("requested capacity " + cap);
            throw err;
        }
    }

    static int binarySearch(int[] a, int start, int len, int key) {
        int high = start + len, low = start - 1, guess;
        while (high - low > 1) {
            // use >>> for average or we could get an integer overflow.
            guess = (high + low) >>> 1;

            if (a[guess] < key) {
                low = guess;
            } else {
                high = guess;
            }
        }

        if (high == start + len) {
            return ~(start + len);
        } else if (a[high] == key) {
            return high;
        } else {
            return ~high;
        }
    }

    /**
     * Gets the Object mapped from the specified key, or <code>-1</code> if no such mapping has
     * been made.
     */
    public int get(int key) {
        return get(key, -1);
    }

    /**
     * Gets the Object mapped from the specified key, or the specified Object if no such mapping has
     * been made.
     */
    private int get(int key, int valueIfKeyNotFound) {
        int i = binarySearch(mKeys, 0, mSize, key);
        if (i < 0 || mValues[i] == DELETED) {
            return valueIfKeyNotFound;
        } else {
            return mValues[i];
        }
    }

    /**
     * Removes the mapping from the specified key, if there was any.
     */
    public void remove(int key) {
        int i = binarySearch(mKeys, 0, mSize, key);
        if (i >= 0 && mValues[i] != DELETED) {
            mValues[i] = DELETED;
            mGarbage = true;
        }
    }

    private void gc() {
        int n = mSize;
        int o = 0;
        int[] keys = mKeys;
        int[] values = mValues;

        for (int i = 0; i < n; i++) {
            int val = values[i];
            if (val != DELETED) {
                if (i != o) {
                    keys[o] = keys[i];
                    values[o] = val;
                }

                o++;
            }
        }

        mGarbage = false;
        mSize = o;
    }

    /**
     * Adds a mapping from the specified key to the specified value, replacing the previous mapping
     * from the specified key if there was one.
     */
    public int put(int key, int value) {
        int i = binarySearch(mKeys, 0, mSize, key);

        if (i >= 0) {
            mValues[i] = value;
        } else {
            i = ~i;

            if (i < mSize && mValues[i] == DELETED) {
                mKeys[i] = key;
                mValues[i] = value;
                return i;
            }

            if (mGarbage && mSize >= mKeys.length) {
                gc();

                // Search again because indices may have changed.
                i = ~binarySearch(mKeys, 0, mSize, key);
            }

            if (mSize >= mKeys.length) {
                int n = Helper.idealIntArraySize(mSize + 1);

                int[] nkeys = new int[n];
                int[] nvalues = new int[n];

                System.arraycopy(mKeys, 0, nkeys, 0, mKeys.length);
                System.arraycopy(mValues, 0, nvalues, 0, mValues.length);

                mKeys = nkeys;
                mValues = nvalues;
            }

            if (mSize - i != 0) {
                System.arraycopy(mKeys, i, mKeys, i + 1, mSize - i);
                System.arraycopy(mValues, i, mValues, i + 1, mSize - i);
            }

            mKeys[i] = key;
            mValues[i] = value;
            mSize++;
        }
        return i;
    }

    /**
     * Returns the number of key-value mappings that this SparseIntIntArray currently stores.
     */
    public int getSize() {
        if (mGarbage) {
            gc();
        }

        return mSize;
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, returns the key from the
     * <code>index</code>th key-value mapping that this SparseIntIntArray stores.
     */
    public int keyAt(int index) {
        if (mGarbage) {
            gc();
        }

        return mKeys[index];
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, sets a new key for the
     * <code>index</code>th key-value mapping that this SparseIntIntArray stores.
     */
    public void setKeyAt(int index, int key) {
        if (mGarbage) {
            gc();
        }

        mKeys[index] = key;
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, returns the value from the
     * <code>index</code>th key-value mapping that this SparseIntIntArray stores.
     */
    public int valueAt(int index) {
        if (mGarbage) {
            gc();
        }

        return mValues[index];
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, sets a new value for the
     * <code>index</code>th key-value mapping that this SparseIntIntArray stores.
     */
    public void setValueAt(int index, int value) {
        if (mGarbage) {
            gc();
        }

        mValues[index] = value;
    }

    /**
     * Removes all key-value mappings from this SparseIntIntArray.
     */
    public void clear() {
        int n = mSize;
        int[] values = mValues;
        for (int i = 0; i < n; i++) {
            values[i] = -1;
        }

        mSize = 0;
        mGarbage = false;
    }

    /**
     * Puts a key/value pair into the array, optimizing for the case where the key is greater than
     * all existing keys in the array.
     */
    public int append(int key, int value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            return put(key, value);
        }

        if (mGarbage && mSize >= mKeys.length) {
            gc();
        }

        int pos = mSize;
        if (pos >= mKeys.length) {
            int n = Helper.idealIntArraySize(pos + 1);

            int[] nkeys = new int[n];
            int[] nvalues = new int[n];

            System.arraycopy(mKeys, 0, nkeys, 0, mKeys.length);
            System.arraycopy(mValues, 0, nvalues, 0, mValues.length);

            mKeys = nkeys;
            mValues = nvalues;
        }

        mKeys[pos] = key;
        mValues[pos] = value;
        mSize = pos + 1;
        return pos;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < getSize(); i++) {
            int k = mKeys[i];
            int v = mValues[i];
            if (i > 0) {
                sb.append(",");
            }
            sb.append(k);
            sb.append(":");
            sb.append(v);
        }
        return sb.toString();
    }

    /**
     * Warning: returns ~index and not -(index+1) like trove and jdk do
     */
    public int binarySearch(int key) {
        return binarySearch(mKeys, 0, mSize, key);
    }
}
