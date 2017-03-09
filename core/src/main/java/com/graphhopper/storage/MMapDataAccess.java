/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
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
package com.graphhopper.storage;

import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;

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
 * A DataAccess implementation using a memory-mapped file, i.e. a facility of the
 * operating system to access a file like an area of RAM.
 *
 * Java presents the mapped memory as a ByteBuffer, and ByteBuffer is not
 * thread-safe, which means that access to a ByteBuffer must be externally
 * synchronized.
 *
 * This class itself is intended to be as thread-safe as other DataAccess
 * implementations are.
 *
 * The exact behavior of memory-mapping is reported to be wildly platform-dependent.
 *
 * <p>
 *
 * @author Peter Karich
 * @author Michael Zilske
 */
public final class MMapDataAccess extends AbstractDataAccess {
    private final boolean allowWrites;
    private RandomAccessFile raFile;
    private List<ByteBuffer> segments = new ArrayList<>();

    MMapDataAccess(String name, String location, ByteOrder order, boolean allowWrites) {
        super(name, location, order);
        this.allowWrites = allowWrites;
    }

    private void initRandomAccessFile() {
        if (raFile != null) {
            return;
        }

        try {
            // raFile necessary for loadExisting and create
            raFile = new RandomAccessFile(getFullName(), allowWrites ? "rw" : "r");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public MMapDataAccess create(long bytes) {
        if (!segments.isEmpty()) {
            throw new IllegalThreadStateException("already created");
        }
        initRandomAccessFile();
        bytes = Math.max(10 * 4, bytes);
        setSegmentSize(segmentSizeInBytes);
        ensureCapacity(bytes);
        return this;
    }

    @Override
    public DataAccess copyTo(DataAccess da) {
        // if(da instanceof MMapDataAccess) {
        // TODO PERFORMANCE make copying into mmap a lot faster via bytebuffer
        // also copying into RAMDataAccess could be faster via bytebuffer
        // is a flush necessary then?
        // }
        return super.copyTo(da);
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        return mapIt(HEADER_OFFSET, bytes);
    }

    private boolean mapIt(long offset, long byteCount) {
        if (byteCount < 0)
            throw new IllegalArgumentException("new capacity has to be strictly positive");

        if (byteCount <= getCapacity())
            return false;

        long longSegmentSize = segmentSizeInBytes;
        int segmentsToMap = (int) (byteCount / longSegmentSize);
        if (segmentsToMap < 0)
            throw new IllegalStateException("Too many segments needs to be allocated. Increase segmentSize.");

        if (byteCount % longSegmentSize != 0)
            segmentsToMap++;

        if (segmentsToMap == 0)
            throw new IllegalStateException("0 segments are not allowed.");

        long bufferStart = offset;
        int newSegments;
        int i = 0;
        long newFileLength = offset + segmentsToMap * longSegmentSize;
        try {
            // ugly remapping
            // http://stackoverflow.com/q/14011919/194609
            // This approach is probably problematic but a bit faster if done often.
            // Here we rely on the OS+file system that increasing the file
            // size has no effect on the old mappings!
            bufferStart += segments.size() * longSegmentSize;
            newSegments = segmentsToMap - segments.size();
            // rely on automatically increasing when mapping
            // raFile.setLength(newFileLength);
            for (; i < newSegments; i++) {
                segments.add(newByteBuffer(bufferStart, longSegmentSize));
                bufferStart += longSegmentSize;
            }
            return true;
        } catch (IOException ex) {
            // we could get an exception here if buffer is too small and area too large
            // e.g. I got an exception for the 65421th buffer (probably around 2**16 == 65536)
            throw new RuntimeException("Couldn't map buffer " + i + " of " + segmentsToMap
                    + " for " + name + " at position " + bufferStart + " for " + byteCount
                    + " bytes with offset " + offset + ", new fileLength:" + newFileLength, ex);
        }
    }

    private ByteBuffer newByteBuffer(long offset, long byteCount) throws IOException {
        // If we request a buffer larger than the file length, it will automatically increase the file length!
        // Will this cause problems? http://stackoverflow.com/q/14011919/194609
        // For trimTo we need to reset the file length later to reduce that size
        ByteBuffer buf = null;
        IOException ioex = null;
        // One retry if it fails. It could fail e.g. if previously buffer wasn't yet unmapped from the jvm
        for (int trial = 0; trial < 1; ) {
            try {
                buf = raFile.getChannel().map(
                        allowWrites ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY,
                        offset, byteCount);
                break;
            } catch (IOException tmpex) {
                ioex = tmpex;
                trial++;
                Helper.cleanHack();
                try {
                    // mini sleep to let JVM do unmapping
                    Thread.sleep(5);
                } catch (InterruptedException iex) {
                    throw new IOException(iex);
                }
            }
        }
        if (buf == null) {
            if (ioex == null) {
                throw new AssertionError("internal problem as the exception 'ioex' shouldn't be null");
            }
            throw ioex;
        }

        buf.order(byteOrder);
        return buf;
    }

    @Override
    public boolean loadExisting() {
        if (segments.size() > 0)
            throw new IllegalStateException("already initialized");

        if (isClosed())
            throw new IllegalStateException("already closed");

        File file = new File(getFullName());
        if (!file.exists() || file.length() == 0)
            return false;

        initRandomAccessFile();
        try {
            long byteCount = readHeader(raFile);
            if (byteCount < 0)
                return false;

            mapIt(HEADER_OFFSET, byteCount - HEADER_OFFSET);
            return true;
        } catch (IOException ex) {
            throw new RuntimeException("Problem while loading " + getFullName(), ex);
        }
    }

    @Override
    public void flush() {
        if (isClosed())
            throw new IllegalStateException("already closed");

        try {
            if (!segments.isEmpty() && segments.get(0) instanceof MappedByteBuffer) {
                for (ByteBuffer bb : segments) {
                    ((MappedByteBuffer) bb).force();
                }
            }
            writeHeader(raFile, raFile.length(), segmentSizeInBytes);

            // this could be necessary too
            // http://stackoverflow.com/q/14011398/194609
            raFile.getFD().sync();
            // equivalent to raFile.getChannel().force(true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void close() {
        super.close();
        close(true);
    }

    /**
     * @param forceClean if true the clean hack (system.gc) will be executed and forces the system
     *                   to cleanup the mmap resources. Set false if you need to close many MMapDataAccess objects.
     */
    void close(boolean forceClean) {
        clean(0, segments.size());
        segments.clear();
        Helper.close(raFile);
        if (forceClean)
            Helper.cleanHack();
    }

    @Override
    public final void setInt(long bytePos, int value) {
        int bufferIndex = (int) (bytePos >> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        ByteBuffer byteBuffer = segments.get(bufferIndex);
        synchronized (byteBuffer) {
            byteBuffer.putInt(index, value);
        }
    }

    @Override
    public final int getInt(long bytePos) {
        int bufferIndex = (int) (bytePos >> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        ByteBuffer byteBuffer = segments.get(bufferIndex);
        synchronized (byteBuffer) {
            return byteBuffer.getInt(index);
        }
    }

    @Override
    public final void setShort(long bytePos, short value) {
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        ByteBuffer byteBuffer = segments.get(bufferIndex);
        synchronized (byteBuffer) {
            byteBuffer.putShort(index, value);
        }
    }

    @Override
    public final short getShort(long bytePos) {
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        ByteBuffer byteBuffer = segments.get(bufferIndex);
        synchronized (byteBuffer) {
            return byteBuffer.getShort(index);
        }
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        final int bufferIndex = (int) (bytePos >>> segmentSizePower);
        final int index = (int) (bytePos & indexDivisor);
        final int delta = index + length - segmentSizeInBytes;
        final ByteBuffer bb1 = segments.get(bufferIndex);
        synchronized (bb1) {
            bb1.position(index);
            if (delta > 0) {
                length -= delta;
                bb1.put(values, 0, length);
            } else {
                bb1.put(values, 0, length);
            }
        }
        if (delta > 0) {
            final ByteBuffer bb2 = segments.get(bufferIndex + 1);
            synchronized (bb2) {
                bb2.position(0);
                bb2.put(values, length, delta);
            }
        }
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        int delta = index + length - segmentSizeInBytes;
        final ByteBuffer bb1 = segments.get(bufferIndex);
        synchronized (bb1) {
            bb1.position(index);
            if (delta > 0) {
                length -= delta;
                bb1.get(values, 0, length);
            } else {
                bb1.get(values, 0, length);
            }
        }
        if (delta > 0) {
            final ByteBuffer bb2 = segments.get(bufferIndex + 1);
            synchronized (bb2) {
                bb2.position(0);
                bb2.get(values, length, delta);
            }
        }
    }

    @Override
    public long getCapacity() {
        long cap = 0;
        for (ByteBuffer bb : segments) {
            synchronized (bb) {
                cap += bb.capacity();
            }
        }
        return cap;
    }

    @Override
    public int getSegments() {
        return segments.size();
    }

    /**
     * Cleans up MappedByteBuffers. Be sure you bring the segments list in a consistent state
     * afterwards.
     * <p>
     *
     * @param from inclusive
     * @param to   exclusive
     */
    private void clean(int from, int to) {
        for (int i = from; i < to; i++) {
            ByteBuffer bb = segments.get(i);
            Helper.cleanMappedByteBuffer(bb);
            segments.set(i, null);
        }
    }

    @Override
    public void trimTo(long capacity) {
        if (capacity < segmentSizeInBytes) {
            capacity = segmentSizeInBytes;
        }
        int remainingSegNo = (int) (capacity / segmentSizeInBytes);
        if (capacity % segmentSizeInBytes != 0) {
            remainingSegNo++;
        }

        clean(remainingSegNo, segments.size());
        Helper.cleanHack();
        segments = new ArrayList<>(segments.subList(0, remainingSegNo));

        try {
            // windows does not allow changing the length of an open files
            if (!Constants.WINDOWS) {
                // reduce file size
                raFile.setLength(HEADER_OFFSET + remainingSegNo * segmentSizeInBytes);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void rename(String newName) {
        if (!checkBeforeRename(newName)) {
            return;
        }
        close();

        super.rename(newName);
        // 'reopen' with newName
        raFile = null;
        closed = false;
        loadExisting();
    }

    @Override
    public DAType getType() {
        return DAType.MMAP;
    }
}
