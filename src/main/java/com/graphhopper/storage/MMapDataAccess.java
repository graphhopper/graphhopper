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
package com.graphhopper.storage;

import com.graphhopper.util.Helper;
import com.graphhopper.util.Helper7;
import com.graphhopper.util.NotThreadSafe;
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

    private RandomAccessFile raFile;
    private List<ByteBuffer> segments = new ArrayList<ByteBuffer>();
    private ByteOrder order;
    private float increaseFactor = 1.5f;
    private transient boolean closed = false;

    private MMapDataAccess() {
        this(null, null);
        throw new IllegalStateException("reserved for direct mapped memory");
    }

    public MMapDataAccess(String id, String location) {
        super(id, location);        
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
    public DataAccess copyTo(DataAccess da) {
        // if(da instanceof MMapDataAccess) {
        // TODO make copying into mmap a lot faster via bytebuffer
        // also copying into RAMDataAccess could be faster via bytebuffer
        // is a flush necessary then?
        // }
        return super.copyTo(da);
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
            throw new IllegalStateException("problem while file mapping " + id);
    }

    protected boolean mapIt(long offset, long byteCount, boolean clearNew) {
        try {
            if (byteCount <= capacity())
                return true;

            raFile.setLength(offset + byteCount);
            if (!segments.isEmpty())
                byteCount = (long) (increaseFactor * byteCount);

            // - can we really assume that this process sees its own changes immediately?
            //   if not we need to expand instead of re-initialize
            // - do we need to clean and release the ByteBuffer or is it even problematic?
            segments.clear();
            int buffersToMap = (int) (byteCount / segmentSizeInBytes);
            if (byteCount % segmentSizeInBytes != 0)
                buffersToMap++;
            int bufferStart = 0;
            for (int i = 0; i < buffersToMap; i++) {
                int bufSize = (int) ((byteCount > (bufferStart + segmentSizeInBytes))
                        ? segmentSizeInBytes
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

            long byteCount = readHeader(raFile);
            if (byteCount < 0)
                return false;
            if (mapIt(HEADER_OFFSET, byteCount - HEADER_OFFSET, false))
                return true;
        } catch (IOException ex) {
            // ex.printStackTrace();
        }
        return false;
    }

    @Override
    public void flush() {
        if (closed)
            throw new IllegalStateException("already closed");
        try {
            if (!segments.isEmpty() && segments.get(0) instanceof MappedByteBuffer) {
                for (ByteBuffer bb : segments) {
                    ((MappedByteBuffer) bb).force();
                }
            }
            writeHeader(raFile, raFile.length(), segmentSizeInBytes);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void close() {
        Helper.close(raFile);
        closed = true;
    }

    @Override
    public void setInt(long intIndex, int value) {
        intIndex *= 4;
        int bufferIndex = (int) (intIndex / segmentSizeInBytes);
        int index = (int) (intIndex % segmentSizeInBytes);
        segments.get(bufferIndex).putInt(index, value);
    }

    @Override
    public int getInt(long intIndex) {
        intIndex *= 4;
        int bufferIndex = (int) (intIndex / segmentSizeInBytes);
        int index = (int) (intIndex % segmentSizeInBytes);
        return segments.get(bufferIndex).getInt(index);
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
    public int getSegments() {
        return segments.size();
    }

    @Override
    public void trimTo(long capacity) {
        if (capacity < segmentSizeInBytes)
            capacity = segmentSizeInBytes;
        int remainingSegNo = (int) (capacity / segmentSizeInBytes);
        if (capacity % segmentSizeInBytes != 0)
            remainingSegNo++;
        List<ByteBuffer> remainingSegments = segments.subList(0, remainingSegNo);
        List<ByteBuffer> delSegments = segments.subList(remainingSegNo, segments.size());
        for (ByteBuffer bb : delSegments) {
            Helper7.cleanMappedByteBuffer(bb);
        }
        segments = remainingSegments;
        try {
            raFile.setLength(HEADER_OFFSET + remainingSegNo * segmentSizeInBytes);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
