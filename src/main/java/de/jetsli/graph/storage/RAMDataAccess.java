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

import de.jetsli.graph.util.BitUtil;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * This is an in-memory data structure but with the possibility to be stored on flush().
 *
 * TODO make it possible to store more than 2^32 bytes
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

    public RAMDataAccess setStore(boolean store) {
        this.store = store;
        return this;
    }

    @Override
    public void copyTo(DataAccess da) {
        if (da instanceof RAMDataAccess) {
            RAMDataAccess rda = (RAMDataAccess) da;
            rda.area = Arrays.copyOf(area, area.length);
            rda.increaseFactor = increaseFactor;
            // do leave id, store and close unchanged
        } else
            super.copyTo(da);
    }

    @Override
    public void createNew(long bytes) {
        if (area != null)
            throw new IllegalThreadStateException("already created");

        int intSize = Math.max(10, (int) (bytes >> 2));
        area = new int[intSize];
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
                int byteLen = (int) (readHeader(raFile));
                int len = byteLen / 4;
                area = new int[len];
                byte[] bytes = new byte[byteLen];
                raFile.seek(HEADER_SPACE);
                // raFile.readInt() <- too slow                
                raFile.readFully(bytes);
                for (int i = 0; i < len; i++) {
                    // TODO different system have different default byte order!
                    area[i] = BitUtil.toInt(bytes, i * 4);
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
    public void flush() {
        if (area == null || closed || !store)
            return;
        try {
            RandomAccessFile raFile = new RandomAccessFile(id, "rw");
            try {
                int len = area.length;
                writeHeader(raFile, len * 4);
                raFile.seek(HEADER_SPACE);
                // raFile.writeInt() <- too slow, so copy into byte array
                byte[] byteArea = new byte[len * 4];
                for (int i = 0; i < len; i++) {
                    // TODO different system have different default byte order!
                    BitUtil.fromInt(byteArea, area[i], i * 4);
                }
                raFile.write(byteArea);
            } finally {
                raFile.close();
            }
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
    public void close() {
        super.close();
        area = null;
        closed = true;
    }

    @Override
    public long capacity() {
        return (long) area.length * 4;
    }

    @Override
    public String toString() {
        return id;
    }
}
