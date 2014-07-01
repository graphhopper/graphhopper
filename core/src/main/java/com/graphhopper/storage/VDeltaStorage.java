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
package com.graphhopper.storage;

import com.graphhopper.util.Helper;
import com.graphhopper.util.NotThreadSafe;
import java.util.Arrays;

/**
 * Similar to VLongStorage but with delta encoding (precision is fixed at 1e5) and negative value
 * support.
 */
@NotThreadSafe
public class VDeltaStorage
{
    private byte[] bytes;
    private int pointer = 0;
    private long lastVal;

    public VDeltaStorage()
    {
        this(10);
    }

    public VDeltaStorage( int cap )
    {
        this(new byte[cap]);
    }

    public VDeltaStorage( byte[] bytes )
    {
        this.bytes = bytes;
    }

    private byte readByte()
    {
        byte b = bytes[pointer];
        pointer++;
        return b;
    }

    private void writeByte( byte b )
    {
        if (pointer >= bytes.length)
        {
            int cap = Math.max(10, (int) (pointer * 1.5f));
            bytes = Arrays.copyOf(bytes, cap);
        }
        bytes[pointer] = b;
        pointer++;
    }

    /**
     * Writes an long in a variable-length format. Writes between one and nine bytes. Smaller values
     * take fewer bytes.
     */
    public final void writeLong( long val )
    {
        // do delta
        long tmp = val;
        val = val - lastVal;
        lastVal = tmp;

        // make place on first bit for negative sign
        val = val << 1;
        if (val < 0)
            val = ~val;

        while ((val & ~0x7FL) != 0L)
        {
            writeByte((byte) ((val & 0x7FL) | 0x80L));
            val >>>= 7;
        }
        writeByte((byte) val);
    }

    /**
     * Reads a long stored in variable-length format. Reads between one and nine bytes. Smaller
     * values take fewer bytes.
     */
    public long readLong()
    {
        byte b = readByte();
        long res = b & 0x7F;
        for (int shift = 7; (b & 0x80) != 0; shift += 7)
        {
            b = readByte();
            res |= (b & 0x7FL) << shift;
        }
        res = (res & 1) != 0 ? ~(res >> 1) : (res >> 1);

        // take into account the delta encoding
        res += lastVal;
        lastVal = res;
        return res;
    }

    public void trimToSize()
    {
        if (bytes.length > pointer)
        {
            byte[] tmp = new byte[pointer];
            System.arraycopy(bytes, 0, tmp, 0, pointer);
            bytes = tmp;
        }
    }

    public void writeDouble( double val )
    {
        writeLong(degreeToLong(val));
    }

    public double readDouble()
    {
        return longToDegree(readLong());
    }

    // copy from Helper class but use long and a smaller precision
    static final long degreeToLong( double deg )
    {
        if (deg >= Double.MAX_VALUE)
            return Integer.MAX_VALUE;
        return Math.round(deg * 1e5);
    }

    static final double longToDegree( long storedInt )
    {
        if (storedInt >= Integer.MAX_VALUE)
            return Double.MAX_VALUE;
        return (double) storedInt / 1e5;
    }

    public byte[] getBytes()
    {
        return bytes;
    }

    // necessary to be called if one writes and then reads
    void reset()
    {
        lastVal = 0;
        pointer = 0;
    }
}
