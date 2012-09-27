/*
 *  Copyright 2012 Peter Karich 
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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a data structure which uses the operating system to synchronize between disc and memory.
 *
 * @author Peter Karich
 */
@NotThreadSafe
public class MMapDataAccess extends AbstractDataAccess {

    private String location;
    private RandomAccessFile raFile;
    private List<ByteBuffer> segments = new ArrayList<ByteBuffer>();
    private ByteOrder order;
    private float increaseFactor = 1.5f;
    private transient boolean closed = false;

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
        if (!segments.isEmpty())
            throw new IllegalThreadStateException("already created");
        bytes = Math.max(10 * 4, bytes);
        ensureCapacity(bytes);
    }

    @Override
    public void copyTo(DataAccess da) {
        // if(da instanceof MMapDataAccess) {
        // TODO make copying into mmap a lot faster via bytebuffer
        // also copying into RAMDataAccess could be faster via bytebuffer
        // is a flush necessary then?
        // }
        super.copyTo(da);
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
        if (!mapIt(HEADER_OFFSET, bytes, true))
            throw new IllegalStateException("problem while file mapping " + location);
    }

    protected boolean mapIt(long offset, long byteCount, boolean clearNew) {
        try {
            if (byteCount <= capacity())
                return true;

            raFile.setLength(offset + byteCount);
            if (!segments.isEmpty())
                byteCount = (long) (increaseFactor * byteCount);

            // can we really assume that this process sees its own changes immediately?
            // if not we need to expand instead of re-initialize
            segments.clear();
            int buffersToMap = (int) (byteCount / segmentSize) + 1;
            int bufferStart = 0;
            for (int i = 0; i < buffersToMap; i++) {
                int bufSize = (int) ((byteCount > (bufferStart + segmentSize))
                        ? segmentSize
                        : (byteCount - bufferStart));
                segments.add(newByteBuffer(offset + bufferStart, bufSize, false));
                bufferStart += bufSize;
            }
            return true;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private ByteBuffer newByteBuffer(long offset, long byteCount, boolean clearNew)
            throws IOException {
        ByteBuffer buf = raFile.getChannel().map(FileChannel.MapMode.READ_WRITE, offset, byteCount);
        if (order != null)
            buf.order(order);

        // TODO
        if (clearNew) {
//            buf.position(oldCap);
//            byteCount -= oldCap;
            int count = (int) (byteCount / EMPTY.length);
            for (int i = 0; i < count; i++) {
                buf.put(EMPTY);
            }
            int len = (int) (byteCount % EMPTY.length);
            if (len > 0)
                buf.put(EMPTY, 0, len);
        }
        return buf;
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
            if (mapIt(HEADER_OFFSET, bytes - HEADER_OFFSET, false))
                return true;
        } catch (Exception ex) {
            // ex.printStackTrace();
        }
        return false;
    }

    @Override
    public void flush() {
        try {
            if (closed)
                throw new IllegalStateException("already closed");

            if (!segments.isEmpty() && segments.get(0) instanceof MappedByteBuffer) {
                for (ByteBuffer bb : segments) {
                    ((MappedByteBuffer) bb).force();
                }
            }
            writeHeader(raFile, raFile.length(), segmentSize);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setInt(long intIndex, int value) {
        intIndex *= 4;
        int bufferIndex = (int) (intIndex / segmentSize);
        int index = (int) (intIndex % segmentSize);
        segments.get(bufferIndex).putInt(index, value);
    }

    @Override
    public int getInt(long intIndex) {
        intIndex *= 4;
        int bufferIndex = (int) (intIndex / segmentSize);
        int index = (int) (intIndex % segmentSize);
        return segments.get(bufferIndex).getInt(index);
    }

    @Override
    public void close() {
        super.close();
        Helper.close(raFile);
        closed = true;
    }

    @Override
    public long capacity() {
        long cap = 0;
        for (ByteBuffer bb : segments) {
            cap += bb.capacity();
        }
        return cap;
    }

    @Override
    public String toString() {
        return location;
    }

    @Override
    public int getSegments() {
        return segments.size();
    }
}
