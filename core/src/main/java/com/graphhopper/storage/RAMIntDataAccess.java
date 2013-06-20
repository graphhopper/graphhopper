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

import com.graphhopper.util.BitUtil;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * This is an in-memory data structure based on an integer array. With the possibility to be stored
 * on flush().
 * <p/>
 * @author Peter Karich
 */
public class RAMIntDataAccess extends AbstractDataAccess
{
    private int[][] segments = new int[0][];
    private boolean closed = false;
    private boolean store;
    private transient int segmentSizeIntsPower;

    RAMIntDataAccess()
    {
        this("", "", false);
    }

    RAMIntDataAccess( String name )
    {
        this(name, name, false);
    }

    RAMIntDataAccess( String name, String location, boolean store )
    {
        super(name, location);
        this.store = store;
    }

    /**
     * @param store true if in-memory data should be saved when calling flush
     */
    public RAMIntDataAccess store( boolean store )
    {
        this.store = store;
        return this;
    }

    @Override
    public boolean isStoring()
    {
        return store;
    }

    @Override
    public DataAccess copyTo( DataAccess da )
    {
        if (da instanceof RAMIntDataAccess)
        {
            RAMIntDataAccess rda = (RAMIntDataAccess) da;
            // TODO we could reuse rda segments!
            rda.segments = new int[segments.length][];
            for (int i = 0; i < segments.length; i++)
            {
                int[] area = segments[i];
                rda.segments[i] = Arrays.copyOf(area, area.length);
            }
            rda.segmentSize(segmentSizeInBytes);
            // leave id, store and close unchanged
            return da;
        } else
        {
            return super.copyTo(da);
        }
    }

    @Override
    public RAMIntDataAccess create( long bytes )
    {
        if (segments.length > 0)
        {
            throw new IllegalThreadStateException("already created");
        }

        // initialize transient values
        segmentSize(segmentSizeInBytes);
        ensureCapacity(Math.max(10 * 4, bytes));
        return this;
    }

    @Override
    public void ensureCapacity( long bytes )
    {
        long cap = capacity();
        long todoBytes = bytes - cap;
        if (todoBytes <= 0)
        {
            return;
        }

        int segmentsToCreate = (int) (todoBytes / segmentSizeInBytes);
        if (todoBytes % segmentSizeInBytes != 0)
        {
            segmentsToCreate++;
        }

        try
        {
            int[][] newSegs = Arrays.copyOf(segments, segments.length + segmentsToCreate);
            for (int i = segments.length; i < newSegs.length; i++)
            {
                newSegs[i] = new int[1 << segmentSizeIntsPower];
            }
            segments = newSegs;
        } catch (OutOfMemoryError err)
        {
            throw new OutOfMemoryError(err.getMessage() + " - problem when allocating new memory. Old capacity: "
                    + cap + ", new bytes:" + todoBytes + ", segmentSizeIntsPower:" + segmentSizeIntsPower
                    + ", new segments:" + segmentsToCreate + ", existing:" + segments.length);
        }
    }

    @Override
    public boolean loadExisting()
    {
        if (segments.length > 0)
        {
            throw new IllegalStateException("already initialized");
        }
        if (!store || closed)
        {
            return false;
        }
        File file = new File(fullName());
        if (!file.exists() || file.length() == 0)
        {
            return false;
        }
        try
        {
            RandomAccessFile raFile = new RandomAccessFile(fullName(), "r");
            try
            {
                long byteCount = readHeader(raFile) - HEADER_OFFSET;
                if (byteCount < 0)
                {
                    return false;
                }
                byte[] bytes = new byte[segmentSizeInBytes];
                raFile.seek(HEADER_OFFSET);
                // raFile.readInt() <- too slow                
                int segmentCount = (int) (byteCount / segmentSizeInBytes);
                if (byteCount % segmentSizeInBytes != 0)
                {
                    segmentCount++;
                }
                segments = new int[segmentCount][];
                for (int s = 0; s < segmentCount; s++)
                {
                    int read = raFile.read(bytes) / 4;
                    int area[] = new int[read];
                    for (int j = 0; j < read; j++)
                    {
                        // TODO different system have different default byte order!
                        area[j] = BitUtil.toInt(bytes, j * 4);
                    }
                    segments[s] = area;
                }
                return true;
            } finally
            {
                raFile.close();
            }
        } catch (IOException ex)
        {
            throw new RuntimeException("Problem while loading " + fullName(), ex);
        }
    }

    @Override
    public void flush()
    {
        if (closed)
        {
            throw new IllegalStateException("already closed");
        }
        if (!store)
        {
            return;
        }
        try
        {
            RandomAccessFile raFile = new RandomAccessFile(fullName(), "rw");
            try
            {
                long len = capacity();
                writeHeader(raFile, len, segmentSizeInBytes);
                raFile.seek(HEADER_OFFSET);
                // raFile.writeInt() <- too slow, so copy into byte array
                for (int s = 0; s < segments.length; s++)
                {
                    int area[] = segments[s];
                    int intLen = area.length;
                    byte[] byteArea = new byte[intLen * 4];
                    for (int i = 0; i < intLen; i++)
                    {
                        // TODO different system have different default byte order!
                        BitUtil.fromInt(byteArea, area[i], i * 4);
                    }
                    raFile.write(byteArea);
                }
            } finally
            {
                raFile.close();
            }
        } catch (Exception ex)
        {
            throw new RuntimeException("Couldn't store integers to " + toString(), ex);
        }
    }

    @Override
    public final void setInt( long longIndex, int value )
    {
        assert segmentSizeIntsPower > 0 : "call create or loadExisting before usage!";
        longIndex >>>= 2;
        int bufferIndex = (int) (longIndex >>> segmentSizeIntsPower);
        int index = (int) (longIndex & indexDivisor);
        segments[bufferIndex][index] = value;
    }

    @Override
    public final int getInt( long longIndex )
    {
        assert segmentSizeIntsPower > 0 : "call create or loadExisting before usage!";
        longIndex >>>= 2;
        int bufferIndex = (int) (longIndex >>> segmentSizeIntsPower);
        int index = (int) (longIndex & indexDivisor);
        return segments[bufferIndex][index];
    }

    @Override
    public void getBytes( long bytePos, byte[] values, int length )
    {
        throw new UnsupportedOperationException(toString() + " does not support byte based acccess. Use RAMDataAccess instead");
    }

    @Override
    public void setBytes( long bytePos, byte[] values, int length )
    {
        throw new UnsupportedOperationException(toString() + " does not support byte based acccess. Use RAMDataAccess instead");
    }

    @Override
    public void close()
    {
        super.close();
        segments = new int[0][];
        closed = true;
    }

    @Override
    public long capacity()
    {
        return (long) segments() * segmentSizeInBytes;
    }

    @Override
    public int segments()
    {
        return segments.length;
    }

    @Override
    public DataAccess segmentSize( int bytes )
    {
        super.segmentSize(bytes);
        segmentSizeIntsPower = (int) (Math.log(segmentSizeInBytes / 4) / Math.log(2));
        indexDivisor = segmentSizeInBytes / 4 - 1;
        return this;
    }

    @Override
    public void trimTo( long capacity )
    {
        if (capacity < segmentSizeInBytes)
        {
            capacity = segmentSizeInBytes;
        }
        int remainingSegments = (int) (capacity / segmentSizeInBytes);
        if (capacity % segmentSizeInBytes != 0)
        {
            remainingSegments++;
        }

        segments = Arrays.copyOf(segments, remainingSegments);
    }

    boolean releaseSegment( int segNumber )
    {
        segments[segNumber] = null;
        return true;
    }

    @Override
    public void rename( String newName )
    {
        if (!checkBeforeRename(newName))
        {
            return;
        }
        if (store)
        {
            super.rename(newName);
        }

        // in every case set the name
        name = newName;
    }

    @Override
    protected boolean isIntBased()
    {
        return true;
    }
}