/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.trees;

import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

/**
 * @author Peter Karich
 */
class QTDataNode<V> implements QTNode<V>
{
    long[] keys;
    /**
     * Use 'null' to mark the end of the array and to avoid an additional capacity int
     */
    V[] values;

    @SuppressWarnings("unchecked")
    public QTDataNode( int entries )
    {
        keys = new long[entries];
        values = (V[]) new Object[entries];
    }

    @Override
    public final boolean hasData()
    {
        return true;
    }

    public boolean isEmpty()
    {
        return values[0] == null;
    }

    public boolean isFull()
    {
        return count() == values.length;
    }

    public int remove( long key )
    {
        int removed = 0;
        for (int i = 0; i < values.length;)
        {
            if (values[i] == null)
            {
                break;
            }
            if (keys[i] == key)
            {
                // is array copy more efficient?
                int max = values.length - 1;
                int j = i;
                for (; j < max; j++)
                {
                    keys[j] = keys[j + 1];
                    values[j] = values[j + 1];
                }
                // new end
                values[j] = null;
                removed++;
            } else
            {
                i++;
            }
        }
        return removed;
    }

    /**
     * @return true if overflow necessary
     */
    public boolean add( long key, V value )
    {
        for (int i = 0; i < values.length; i++)
        {
            if (values[i] == null)
            {
                keys[i] = key;
                values[i] = value;
                i++;
                if (i < values.length)
                {
                    values[i] = null;
                }
                return false;
            }
        }
        return true;
    }

    /**
     * @return true if data is full
     */
    public boolean overwriteFrom( int num, long bitPosition, QTDataNode<V> dn, long key, V value )
    {
        int counter = 0;
        long nextBitPos = bitPosition >>> 1;
        int tmp = (key & bitPosition) == 0 ? 0 : 2;
        if ((key & nextBitPos) != 0)
        {
            tmp++;
        }

        if (tmp == num)
        {
            keys[counter] = key;
            values[counter] = value;
            counter++;
        }
        for (int i = 0; i < dn.values.length; i++)
        {
            if (dn.values[i] == null)
            {
                break;
            }
            tmp = (dn.keys[i] & bitPosition) == 0 ? 0 : 2;
            if ((dn.keys[i] & nextBitPos) != 0)
            {
                tmp++;
            }

            if (tmp == num)
            {
                if (counter >= values.length)
                {
                    return true;
                }
                keys[counter] = dn.keys[i];
                values[counter] = dn.values[i];
                counter++;
            }
        }
        // set last entry to null
        if (counter < values.length)
        {
            values[counter] = null;
        }
        return false;
    }

    V getValue( long key )
    {
        for (int i = 0; i < values.length; i++)
        {
            if (values[i] == null)
            {
                return null;
            }
            if (keys[i] == key)
            {
                return (V) values[i];
            }
        }
        return null;
    }

    @Override
    public QTNode<V> get( int num )
    {
        throw new UnsupportedOperationException("no branch node.");
    }

    @Override
    public void set( int num, QTNode<V> n )
    {
        throw new UnsupportedOperationException("no branch node.");
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("dn:").append(count()).append(" ");
        for (int i = 0; i < keys.length; i++)
        {
            if (values[i] == null)
            {
                break;
            }

            sb.append(values[i]).append(" ");
        }
        return sb.toString();
    }

    public String toString( SpatialKeyAlgo algo )
    {
        StringBuilder sb = new StringBuilder("dn:").append(count()).append(" ");
        GHPoint obj = new GHPoint();
        for (int i = 0; i < values.length; i++)
        {
            if (values[i] == null)
            {
                break;
            }
            algo.decode(keys[i], obj);
            sb.append(values[i]).append(":").append(obj).append(" ");
        }
        return sb.toString();
    }

    @Override
    public long getMemoryUsageInBytes( int factor )
    {
        return Helper.getSizeOfLongArray(keys.length, factor) + Helper.getSizeOfLongArray(values.length, factor);
    }

    @Override
    public long getEmptyEntries( boolean onlyBranches )
    {
        if (onlyBranches)
        {
            return 0;
        }

        return values.length - count();
    }

    @Override
    public int count()
    {
        int i = 0;
        for (; i < values.length; i++)
        {
            if (values[i] == null)
            {
                return i;
            }
        }
        return i;
    }

    @SuppressWarnings("unchecked")
    void ensure( int newSize )
    {
        long[] tmpKeys = new long[newSize];
        Object[] tmpValues = new Object[newSize];
        System.arraycopy(keys, 0, tmpKeys, 0, keys.length);
        System.arraycopy(values, 0, tmpValues, 0, values.length);
        keys = tmpKeys;
        values = (V[]) tmpValues;
    }

    int count( long spatialKey )
    {
        int counter = 0;
        for (int i = 0; i < values.length; i++)
        {
            if (spatialKey == keys[i])
            {
                counter++;
            }
            if (values[i] == null)
            {
                break;
            }
        }
        return counter;
    }
}
