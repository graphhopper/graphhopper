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
package com.graphhopper.search;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Storable;
import java.io.UnsupportedEncodingException;

/**
 * @author Ottavio Campana
 * @author Peter Karich
 */
public class NameIndex implements Storable<NameIndex>
{
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
        byte[] bytes;
        try
        {
            bytes = name.getBytes("UTF-8");
        } catch (UnsupportedEncodingException ex)
        {
            throw new RuntimeException("Encoding not supported", ex);
        }
        int oldPointer = bytePointer;
        names.ensureCapacity(bytePointer + 1 + bytes.length);
        // store size into one byte
        if (bytes.length > 255)
        {
            throw new IllegalStateException("Way name is too long: " + name);
        }

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
        int size = sizeBytes[0];
        byte[] bytes = new byte[size];
        names.getBytes(pointer + sizeBytes.length, bytes, size);
        try
        {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException ex)
        {
            throw new RuntimeException("Encoding not supported", ex);
        }
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

    public void setSegmentSize( int segments )
    {
        names.setSegmentSize(segments);
    }

    @Override
    public long getCapacity()
    {
        return names.getCapacity();
    }
}
