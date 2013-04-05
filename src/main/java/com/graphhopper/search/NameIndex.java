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
import com.graphhopper.util.BitUtil;

/**
 * @author Ottavio Campana
 * @author Peter Karich
 */
public class NameIndex implements Storable<NameIndex> {

    private static final int START_POINTER = 1;
    private int nameCount = START_POINTER;
    private DataAccess names;

    public NameIndex(Directory dir) {
        names = dir.findCreate("names");
    }

    @Override
    public NameIndex create(long cap) {
        names.create(cap);
        return this;
    }

    @Override
    public boolean loadExisting() {
        if (names.loadExisting()) {
            nameCount = names.getHeader(0);
            return true;
        }

        return false;
    }

    public int put(String name) {
        // We add street names in UTF-32 format. 
        // This is waste of space, but DataAccess interface is integer oriented (for now).
        byte[] nameAsBytes;
        try {
            nameAsBytes = name.getBytes("UTF-32");
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }

        int offset = START_POINTER;
        for (int i = 0; i < nameCount; i++) {
            int size = names.getInt(offset);
            byte[] bytes = new byte[4 * size];
            boolean found = true;
            int j;

            // search existing strings
            for (j = 0; j < size; j++) {
                int value = names.getInt(offset + 1 + j);
                int tmp = j * 4;
                BitUtil.fromInt(bytes, value, tmp);
                if (tmp + 3 > nameAsBytes.length
                        || bytes[tmp] != nameAsBytes[tmp]
                        || bytes[tmp + 1] != nameAsBytes[tmp + 1]
                        || bytes[tmp + 2] != nameAsBytes[tmp + 2]
                        || bytes[tmp + 3] != nameAsBytes[tmp + 3]) {
                    found = false;
                    break;
                }
            }

            // found the name in DataAccess
            if (found && j == size && bytes.length == nameAsBytes.length)
                return offset;

            offset += size + 1;
        }

        // if we arrive here, we need to add the name to the DataAccess
        names.ensureCapacity(4 * (offset + 1 + nameAsBytes.length / 4));
        names.setInt(offset, nameAsBytes.length / 4);
        for (int i = 0; i < name.length(); i++) {
            int value = BitUtil.toInt(nameAsBytes, i * 4);
            names.setInt(offset + 1 + i, value);
        }

        nameCount++;
        return offset;
    }

    public String get(int index) {
        int size = names.getInt(index);
        byte[] bytes = new byte[4 * size];
        index++;
        for (int i = 0; i < size; i++) {
            int value = names.getInt(index + i);
            BitUtil.fromInt(bytes, value, i * 4);
        }
        try {
            return new String(bytes, "UTF-32");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void segmentSize(int bytes) {
        names.segmentSize(bytes);
    }

    @Override
    public void flush() {
        names.setHeader(0, nameCount);
        names.flush();
    }

    @Override
    public void close() {
        names.close();
    }

    @Override
    public long capacity() {
        return names.capacity();
    }
}
