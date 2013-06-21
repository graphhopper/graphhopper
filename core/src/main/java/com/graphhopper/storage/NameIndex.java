/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
package com.graphhopper.storage;

/**
 * @author Ottavio Campana
 * @author Peter Karich
 */
public class NameIndex implements Storable<NameIndex>
{
    // currently only 4 * 2GB is supported
    private int nextBytePos;
    private DataAccess names;

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
            nextBytePos = names.getHeader(0);
            return true;
        }

        return false;
    }

    public int put( String name )
    {
        byte[] nameAsBytes;
        try
        {
            nameAsBytes = name.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException ex)
        {
            throw new RuntimeException(ex);
        }

        // do not search for existing names as it does not scale for larger areas
//        long offset = 0;
//        for (int i = 0; i < nameCount; i++) {
//            int size = names.getInt(offset);
//            byte[] bytes = new byte[4 * size];
//            boolean found = true;
//            int j;
//
//            // search existing strings
//            for (j = 0; j < size; j++) {
//                int value = names.getInt(offset + 1 + j);
//                int tmp = j * 4;
//                BitUtil.fromInt(bytes, value, tmp);
//                if (tmp + 3 > nameAsBytes.length
//                        || bytes[tmp] != nameAsBytes[tmp]
//                        || bytes[tmp + 1] != nameAsBytes[tmp + 1]
//                        || bytes[tmp + 2] != nameAsBytes[tmp + 2]
//                        || bytes[tmp + 3] != nameAsBytes[tmp + 3]) {
//                    found = false;
//                    break;
//                }
//            }
//
//            // found the name in DataAccess
//            if (found && j == size && bytes.length == nameAsBytes.length)
//                return offset;
//
//            offset += size + 1;
//        }

        // if we arrive here, we need to add the name to the DataAccess        

        names.ensureCapacity(4 * 2 + nameAsBytes.length);
        names.setInt(nextBytePos, nameAsBytes.length);
        int oldOffset = nextBytePos;
        nextBytePos += 4;
        names.setBytes(nextBytePos, nameAsBytes, nameAsBytes.length);
        nextBytePos += nameAsBytes.length;
        return oldOffset;
    }

    public String get( int index )
    {
        int size = names.getInt(index);
        byte[] bytes = new byte[size];
        index += 4;
        names.getBytes(index, bytes, size);
        try
        {
            return new String(bytes, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void setSegmentSize( int bytes )
    {
        names.setSegmentSize(bytes);
    }

    @Override
    public void flush()
    {
        names.setHeader(0, nextBytePos);
        names.flush();
    }

    @Override
    public void close()
    {
        names.close();
    }

    @Override
    public long getCapacity()
    {
        return names.getCapacity();
    }
}
