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
package com.graphhopper.search;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Storable;
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Ottavio Campana
 * @author Peter Karich
 */
public class NameIndex implements Storable<NameIndex>
{
    private static Logger logger = LoggerFactory.getLogger(NameIndex.class);
    private static final int START_POINTER = 1;
    private int bytePointer = START_POINTER;
    private DataAccess names;
    // minor optimization for the previous stored name
    private String lastName;
    private int lastIndex;

    public NameIndex( Directory dir )
    {
        names = dir.find("names");
    }

    @Override
    public NameIndex create( long cap )
    {
        names.create(cap);
        return this;
    }

    @Override
    public boolean loadExisting()
    {
        if (names.loadExisting())
        {
            bytePointer = names.getHeader(0);
            return true;
        }

        return false;
    }

    /**
     * @return the integer reference
     */
    public int put( String name )
    {
        if (name == null || name.isEmpty())
        {
            return 0;
        }
        if (name.equals(lastName))
        {
            return lastIndex;
        }
        byte[] bytes = getBytes(name);
        int oldPointer = bytePointer;
        names.incCapacity(bytePointer + 1 + bytes.length);
        byte[] sizeBytes = new byte[]
        {
            (byte) bytes.length
        };
        names.setBytes(bytePointer, sizeBytes, sizeBytes.length);
        bytePointer++;
        names.setBytes(bytePointer, bytes, bytes.length);
        bytePointer += bytes.length;
        if (bytePointer < 0)
        {
            throw new IllegalStateException("Way index is too large. Cannot contain more than 2GB");
        }
        lastName = name;
        lastIndex = oldPointer;
        return oldPointer;
    }

    private byte[] getBytes( String name )
    {
        byte[] bytes = null;
        for (int i = 0; i < 2; i++)
        {
            bytes = name.getBytes(Helper.UTF_CS);
            // we have to store the size of the array into *one* byte
            if (bytes.length > 255)
            {
                String newName = name.substring(0, 256 / 4);
                logger.info("Way name is too long: " + name + " truncated to " + newName);
                name = newName;
                continue;
            }
            break;
        }
        if (bytes.length > 255)
        {
            // really make sure no such problem exists
            throw new IllegalStateException("Way name is too long: " + name);
        }
        return bytes;
    }

    public String get( int pointer )
    {
        if (pointer < 0)
        {
            throw new IllegalStateException("pointer cannot be negative:" + pointer);
        }
        if (pointer == 0)
        {
            return "";
        }
        byte[] sizeBytes = new byte[1];
        names.getBytes(pointer, sizeBytes, 1);
        int size = sizeBytes[0] & 0xFF;
        byte[] bytes = new byte[size];
        names.getBytes(pointer + sizeBytes.length, bytes, size);
        return new String(bytes, Helper.UTF_CS);
    }

    @Override
    public void flush()
    {
        names.setHeader(0, bytePointer);
        names.flush();
    }

    @Override
    public void close()
    {
        names.close();
    }

    @Override
    public boolean isClosed()
    {
        return names.isClosed();
    }

    public void setSegmentSize( int segments )
    {
        names.setSegmentSize(segments);
    }

    @Override
    public long getCapacity()
    {
        return names.getCapacity();
    }

    public void copyTo( NameIndex nameIndex )
    {
        names.copyTo(nameIndex.names);
    }
}
