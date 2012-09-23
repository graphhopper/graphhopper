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
package de.jetsli.graph.storage;

import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * This is an in-memory data structure but with the possibility to be stored on flush().
 *
 * @author Peter Karich
 */
public class RAMDataAccess extends AbstractDataAccess {

    private String id;
    private int[] area;
    private float increaseFactor = 1.5f;
    private boolean closed = false;
    private boolean store;

    public RAMDataAccess(String id) {
        this(id, false);
    }

    public RAMDataAccess(String id, boolean store) {
        this.id = id;
        this.store = store;
        if (id == null)
            throw new IllegalStateException("RAMDataAccess id cannot be null");
    }

    @Override
    public DataAccess createNew(long bytes) {
        if (area != null)
            throw new IllegalThreadStateException("already created");
        int intSize = (int) (bytes >> 2);
        area = new int[intSize];
        return this;
    }

    @Override
    public void ensureCapacity(long bytes) {
        int intSize = (int) (bytes >> 2);
        if (intSize <= area.length)
            return;

        area = Arrays.copyOf(area, (int) (intSize * increaseFactor));
    }

    @Override
    public boolean loadExisting() {
        if (area != null || closed || !store)
            return false;
        try {
            RandomAccessFile raFile = new RandomAccessFile(id, "r");
            try {
                raFile.seek(0);
                if (!raFile.readUTF().equals(DATAACESS_MARKER))
                    return false;
                int len = (int) (readHeader(raFile) / 4);
                area = new int[len];
                raFile.seek(HEADER_SPACE);
                for (int i = 0; i < len; i++) {
                    area[i] = raFile.readInt();
                }

                return true;
            } finally {
                raFile.close();
            }
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public DataAccess flush() {
        if (area == null || closed || !store)
            return this;
        try {
            RandomAccessFile out = new RandomAccessFile(id, "rw");
            try {
                int len = area.length;
                writeHeader(out, len * 4);
                out.seek(HEADER_SPACE);
                for (int i = 0; i < len; i++) {
                    out.writeInt(area[i]);
                }
            } finally {
                out.close();
            }
            return this;
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't store integers to " + id, ex);
        }
    }

    @Override
    public void setInt(long index, int value) {
        area[(int) index] = value;
    }

    @Override
    public int getInt(long index) {
        return area[(int) index];
    }

    @Override
    public DataAccess close() {
        super.close();
        area = null;
        closed = true;
        return this;
    }

    @Override
    public int capacity() {
        return area.length * 4;
    }

    @Override
    public String toString() {
        return id;
    }
}
