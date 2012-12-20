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
import java.io.File;
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
    private transient boolean closed = false;

    MMapDataAccess() {
        this(null, null);
        throw new IllegalStateException("reserved for direct mapped memory");
    }

    MMapDataAccess(String name, String location) {
        super(name, location);
    }

    private void initRandomAccessFile() {
        if (raFile != null)
            return;

        try {
            // raFile necessary for loadExisting and createNew
            raFile = new RandomAccessFile(getFullName(), "rw");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void createNew(long bytes) {
        if (!segments.isEmpty())
            throw new IllegalThreadStateException("already created");
        initRandomAccessFile();
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
        mapIt(HEADER_OFFSET, bytes, true);
    }

    protected void mapIt(long offset, long byteCount, boolean clearNew) {
        if (byteCount <= capacity())
            return;

        int i = 0;
        int buffersToMap = (int) (byteCount / segmentSizeInBytes);
        if (byteCount % segmentSizeInBytes != 0)
            buffersToMap++;
        int bufferStart = 0;
        try {
            raFile.setLength(offset + byteCount);
            // RE-MAP all buffers and add as many as needed!
            segments.clear();
            for (; i < buffersToMap; i++) {
                int bufSize = (int) ((byteCount > (bufferStart + segmentSizeInBytes))
                        ? segmentSizeInBytes
                        : (byteCount - bufferStart));
                segments.add(newByteBuffer(offset + bufferStart, bufSize));
                bufferStart += bufSize;
            }

            // IMPORTANT NOTICE regarding only the newly mapped buffers:
            // If file length was increased clearing (copying 0 into it) is not necessary!
            // You only have to take care that previous existing files should be removed.
        } catch (Exception ex) {
            // we could get an exception here if buffer is too small and area too large
            // e.g. I got an exception for the 65421th buffer (probably around 2**16 == 65536)
            throw new RuntimeException("Couldn't map buffer " + i + " of " + buffersToMap
                    + " at position " + bufferStart + " for " + byteCount + " bytes with offset " + offset, ex);
        }
    }

    private ByteBuffer newByteBuffer(long offset, int byteCount)
            throws IOException {
        ByteBuffer buf = raFile.getChannel().map(FileChannel.MapMode.READ_WRITE, offset, byteCount);
        if (order != null)
            buf.order(order);

        boolean tmp = false;
        if (tmp) {
            int count = (int) (byteCount / EMPTY.length);
            for (int i = 0; i < count; i++) {
                buf.put(EMPTY);
            }
            int len = (int) (byteCount % EMPTY.length);
            if (len > 0)
                buf.put(EMPTY, count * EMPTY.length, len);
        }
        return buf;
    }

    @Override
    public boolean loadExisting() {
        if (segments.size() > 0)
            throw new IllegalStateException("already initialized");
        if (closed)
            return false;
        File file = new File(getFullName());
        if (!file.exists() || file.length() == 0)
            return false;
        initRandomAccessFile();
        try {
            long byteCount = readHeader(raFile);
            if (byteCount < 0)
                return false;
            mapIt(HEADER_OFFSET, byteCount - HEADER_OFFSET, false);
            return true;
        } catch (IOException ex) {
            throw new RuntimeException("Problem while loading " + getFullName(), ex);
        }
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
        // cleaning all ByteBuffers?
        // Helper7.cleanMappedByteBuffer(bb);
        Helper.close(raFile);
        segments.clear();
        closed = true;
    }

    @Override
    public void setInt(long intIndex, int value) {
        intIndex *= 4;
        // TODO improve via bit operations! see RAMDataAccess
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

    boolean releaseSegment(int segNumber) {
        ByteBuffer segment = segments.get(segNumber);
        if (segment instanceof MappedByteBuffer)
            ((MappedByteBuffer) segment).force();

        Helper7.cleanMappedByteBuffer(segment);
        segments.set(segNumber, null);
        return true;
    }

    @Override
    public void rename(String newName) {
        if (!checkBeforeRename(newName))
            return;
        close();

        super.rename(newName);
        // 'reopen' with newName
        raFile = null;
        closed = false;
        loadExisting();
    }
}
