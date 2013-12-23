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
package com.graphhopper.coll;

import com.graphhopper.util.Helper;
import gnu.trove.map.hash.TLongIntHashMap;

/**
 * Segmented HashMap to make it possible to store more than Integer.MAX values.
 * <p/>
 * @author Peter Karich
 */
public class BigLongIntMap implements LongIntMap
{
    private TLongIntHashMap[] maps;
//    private MyLongIntHashMap[] maps;

    public BigLongIntMap( long maxSize, int noNumber )
    {
        this(maxSize, Math.max(1, (int) (maxSize / 10000000)), noNumber);
    }

    public BigLongIntMap( long maxSize, int minSegments, int noNumber )
    {
        if (maxSize < 0)
        {
            throw new IllegalArgumentException("Maximum size illegal " + maxSize);
        }
        if (minSegments < 1)
        {
            throw new IllegalArgumentException("Minimun segment number illegal " + minSegments);
        }
        minSegments = Math.max((int) (maxSize / Integer.MAX_VALUE), minSegments);
        maps = new TLongIntHashMap[minSegments];
        int size = (int) (maxSize / minSegments) + 1;
        for (int i = 0; i < maps.length; i++)
        {
            maps[i] = new TLongIntHashMap(size, 1.4f, noNumber, noNumber);
        }
    }

    @Override
    public int put( long key, int value )
    {
        int segment = Math.abs((int) ((key >> 32) ^ key)) % maps.length;
        return maps[segment].put(key, value);
    }

    @Override
    public int get( long key )
    {
        int segment = Math.abs((int) ((key >> 32) ^ key)) % maps.length;
        return maps[segment].get(key);
    }

    public long getCapacity()
    {
        long cap = 0;
        for (int i = 0; i < maps.length; i++)
        {
            cap += maps[i].capacity();
        }
        return cap;
    }

    @Override
    public long getSize()
    {
        long size = 0;
        for (int i = 0; i < maps.length; i++)
        {
            size += maps[i].size();
        }
        return size;
    }

    @Override
    public String toString()
    {
        String str = "";
        for (int i = 0; i < maps.length; i++)
        {
            str += Helper.nf(maps[i].size()) + ", ";
        }
        return str;
    }

    public void clear()
    {
        for (int i = 0; i < maps.length; i++)
        {
            maps[i].clear();
        }
    }

    /**
     * memory usage in MB
     */
    @Override
    public int getMemoryUsage()
    {
        return Math.round(getCapacity() * (8 + 4 + 1) / Helper.MB);
    }

    @Override
    public void optimize()
    {
    }
}
