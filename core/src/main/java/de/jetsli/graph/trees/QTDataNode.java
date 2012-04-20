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
package de.jetsli.graph.trees;

import de.jetsli.graph.geohash.SpatialKeyAlgo;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.Helper;

/**
 * @author Peter Karich
 */
class QTDataNode<V> implements QTNode<V> {

    long[] keys;
    Object[] values;

    public QTDataNode(int entries) {
        keys = new long[entries];
        values = new Object[entries];
    }

    @Override
    public final boolean hasData() {
        return true;
    }

    public boolean isEmpty() {
        return values[0] == null;
    }

    public boolean remove(long key) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null)
                return false;
            if (keys[i] == key) {
                // is array copy more efficient when some null entries? does it create a temp array?
                // System.arraycopy(keys, i + 1, keys, i, keys.length - i - 1);
                // System.arraycopy(values, i + 1, values, i, values.length - i - 1);
                int max = values.length - 1;
                for (; i < max; i++) {
                    keys[i] = keys[i + 1];
                    values[i] = values[i + 1];
                }
                // new end
                values[i] = null;
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if overflow necessary
     */
    public boolean put(long key, V value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null || keys[i] == key) {
                keys[i] = key;
                values[i] = value;
                i++;
                if (i < values.length)
                    values[i] = null;
                return false;
            }
        }
        return true;
    }

    /**
     * @return true if data is full
     */
    public boolean overwriteFrom(int num, long bitPosition, QTDataNode<V> dn, long key, V value) {
        int counter = 0;
        long nextBitPos = bitPosition >>> 1;
        int tmp = (key & bitPosition) == 0 ? 0 : 2;
        if ((key & nextBitPos) != 0)
            tmp |= 1;

        if (tmp == num) {
            keys[counter] = key;
            values[counter] = value;
            counter++;
        }
        for (int i = 0; i < dn.values.length; i++) {
            if (dn.values[i] == null)
                break;
            tmp = (dn.keys[i] & bitPosition) == 0 ? 0 : 2;
            if ((dn.keys[i] & nextBitPos) != 0)
                tmp |= 1;

            if (tmp == num) {
                if (counter >= values.length)
                    return true;
                keys[counter] = dn.keys[i];
                values[counter] = dn.values[i];
                counter++;
            }
        }
        // set last entry to null
        if (counter < values.length)
            values[counter] = null;
        return false;
    }

    public V getValue(long key) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null)
                return null;
            if (keys[i] == key)
                return (V) values[i];
        }
        return null;
    }

    @Override
    public int count() {
        int i = 0;
        for (; i < values.length; i++) {
            if (values[i] == null)
                return i;
        }
        return i;
    }

    @Override
    public QTNode<V> get(int num) {
        throw new UnsupportedOperationException("no branch node.");
    }

    @Override
    public void set(int num, QTNode<V> n) {
        throw new UnsupportedOperationException("no branch node.");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("dn:").append(count()).append(" ");
        for (int i = 0; i < keys.length; i++) {
            if (values[i] == null)
                break;

            sb.append(values[i]).append(" ");
        }
        return sb.toString();
    }

    public String toString(SpatialKeyAlgo algo) {
        StringBuilder sb = new StringBuilder("dn:").append(count()).append(" ");
        CoordTrig obj = new CoordTrig();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null)
                break;
            algo.decode(keys[i], obj);
            sb.append(values[i]).append(":").append(obj).append(" ");
        }
        return sb.toString();
    }

    @Override
    public long getMemoryUsageInBytes(int factor) {
        return Helper.sizeOfLongArray(keys.length, factor) + Helper.sizeOfLongArray(values.length, factor);
    }
}
