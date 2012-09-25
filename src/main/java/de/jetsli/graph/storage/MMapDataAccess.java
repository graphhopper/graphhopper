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

import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.NotThreadSafe;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * This is a data structure which uses off-heap memory and the OS to flush(), which is always
 * amazingly fast.
 *
 * TODO make it possible to store more than 2^32 bytes
 *
 * @author Peter Karich
 */
@NotThreadSafe
public class MMapDataAccess extends AbstractDataAccess {

    private String location;
    private RandomAccessFile raFile;
    private MappedByteBuffer bBuffer;
    private ByteOrder order;
    private float increaseFactor = 1.5f;
    private transient boolean closed = false;
    private static byte[] EMPTY = new byte[1024];

    public MMapDataAccess(String location) {
        this.location = location;
        try {
            // raFile necessary for loadExisting and alloc
            raFile = new RandomAccessFile(location, "rw");
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public void createNew(long bytes) {
        if (bBuffer != null)
            throw new IllegalThreadStateException("already created");
        ensureCapacity(bytes);
    }

    /**
     * Makes it possible to force the order. E.g. if we create the file on a host system and copy it
     * to a different like android. http://en.wikipedia.org/wiki/Endianness
     */
    public MMapDataAccess setByteOrder(ByteOrder order) {
        this.order = order;
        return this;
    }

    @Override
    public void ensureCapacity(long bytes) {
        if (!mapIt(HEADER_SPACE, bytes, true))
            throw new IllegalStateException("problem while file mapping " + location);
    }

    protected boolean mapIt(long start, long bytes, boolean clearNew) {
        try {
            int oldCap = 0;
            if (bBuffer != null) {
                oldCap = bBuffer.capacity();
                if (bytes <= oldCap)
                    return true;

                bytes = (long) (increaseFactor * bytes);
                raFile.setLength(bytes + start);
            }
            bBuffer = raFile.getChannel().map(FileChannel.MapMode.READ_WRITE, start, bytes);
            if (order != null)
                bBuffer.order(order);

            if (clearNew) {
                bBuffer.position(oldCap);
                bytes -= oldCap;
                int count = (int) (bytes / EMPTY.length);
                for (int i = 0; i < count; i++) {
                    bBuffer.put(EMPTY);
                }
                int len = (int) (bytes % EMPTY.length);
                if (len > 0)
                    bBuffer.put(EMPTY, 0, len);
            }
            return true;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean loadExisting() {
        try {
            if (closed)
                return false;
            raFile.seek(0);
            if (!raFile.readUTF().equals(DATAACESS_MARKER))
                return false;
            long bytes = readHeader(raFile);
            if (mapIt(HEADER_SPACE, bytes, false))
                return true;
        } catch (Exception ex) {
        }
        return false;
    }

    public void flush() {
        try {
            bBuffer.force();
            writeHeader(raFile, raFile.length());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setInt(long intIndex, int value) {
        bBuffer.putInt((int) intIndex * 4, value);
    }

    @Override
    public int getInt(long intIndex) {
        return bBuffer.getInt((int) intIndex * 4);
    }

    public void close() {
        super.close();
        Helper.close(raFile);
        closed = true;
    }

    @Override
    public int capacity() {
        return bBuffer.capacity();
    }

    @Override
    public String toString() {
        return location;
    }
}
