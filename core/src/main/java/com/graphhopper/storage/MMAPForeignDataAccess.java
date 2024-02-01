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

import com.graphhopper.util.Helper;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class MMAPForeignDataAccess extends AbstractDataAccess {

    private final Arena arena = Arena.ofShared();
    private final boolean allowWrites;
    private RandomAccessFile raFile;
    private final List<MemorySegment> segments = new ArrayList<>();

    MMAPForeignDataAccess(String name, String location, boolean allowWrites, int segmentSize) {
        super(name, location, segmentSize);
        this.allowWrites = allowWrites;
    }

    private void initRandomAccessFile() {
        if (raFile != null)
            return;

        try {
            // raFile necessary for loadExisting and create
            raFile = new RandomAccessFile(getFullName(), allowWrites ? "rw" : "r");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public MMAPForeignDataAccess create(long bytes) {
        if (!segments.isEmpty()) {
            throw new IllegalThreadStateException("already created");
        }
        initRandomAccessFile();
        bytes = Math.max(10 * 4, bytes);
        ensureCapacity(bytes);
        return this;
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
                segments.add(raFile.getChannel().map(allowWrites ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY,
                        bufferStart,
                        longSegmentSize,
                        arena));

                bufferStart += longSegmentSize;
            }
            return true;
        } catch (IOException ex) {
            // we could get an exception here if buffer is too small and area too large
            // e.g. I got an exception for the 65421th buffer (probably around 2**16 == 65536)
            throw new RuntimeException("Couldn't map buffer " + i + " of " + segmentsToMap + " with " + longSegmentSize
                    + " for " + name + " at position " + bufferStart + " for " + byteCount + " bytes with offset " + offset
                    + ", new fileLength:" + newFileLength + ", " + Helper.getMemInfo(), ex);
        }
    }

    @Override
    public boolean loadExisting() {
        if (!segments.isEmpty())
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
            for (MemorySegment ms : segments) {
                ms.force();
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

    /**
     * Load memory mapped files into physical memory.
     */
    public void load(int percentage) {
        if (percentage < 0 || percentage > 100)
            throw new IllegalArgumentException("Percentage for MMapDataAccess.load for " + getName() + " must be in [0,100] but was " + percentage);
        int max = Math.round(segments.size() * percentage / 100f);
        for (int i = 0; i < max; i++) {
            segments.get(i).load();
        }
    }

    @Override
    public void close() {
        super.close();
        clean(0, segments.size());
        segments.clear();
        Helper.close(raFile);
    }

    @Override
    public void setInt(long bytePos, int value) {
        int bufferIndex = (int) (bytePos >> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 4 > segmentSizeInBytes)
            throw new IllegalStateException("Padding required. Currently an int cannot be distributed over two segments. " + bytePos);
        MemorySegment ms = segments.get(bufferIndex);
        ms.set(ValueLayout.JAVA_INT_UNALIGNED, index, value);
    }

    @Override
    public int getInt(long bytePos) {
        int bufferIndex = (int) (bytePos >> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 4 > segmentSizeInBytes)
            throw new IllegalStateException("Padding required. Currently an int cannot be distributed over two segments. " + bytePos);
        MemorySegment ms = segments.get(bufferIndex);
        return ms.get(ValueLayout.JAVA_INT_UNALIGNED, index);
    }

    @Override
    public void setShort(long bytePos, short value) {
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        MemorySegment ms = segments.get(bufferIndex);
        if (index + 2 > segmentSizeInBytes) {
            MemorySegment msNext = segments.get(bufferIndex + 1);
            // special case if short has to be written into two separate segments
            ms.set(JAVA_BYTE, index, (byte) value);
            msNext.set(JAVA_BYTE, 0, (byte) (value >>> 8));
        } else {
            ms.set(ValueLayout.JAVA_SHORT_UNALIGNED, index, value);
        }
    }

    @Override
    public short getShort(long bytePos) {
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        MemorySegment ms = segments.get(bufferIndex);
        if (index + 2 > segmentSizeInBytes) {
            MemorySegment msNext = segments.get(bufferIndex + 1);
            return (short) ((msNext.get(JAVA_BYTE, 0) & 0xFF) << 8 | ms.get(JAVA_BYTE, index) & 0xFF);
        }
        return ms.get(ValueLayout.JAVA_SHORT_UNALIGNED, index);
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        final int bufferIndex = (int) (bytePos >>> segmentSizePower);
        final int index = (int) (bytePos & indexDivisor);
        final int delta = index + length - segmentSizeInBytes;
        final MemorySegment ms1 = segments.get(bufferIndex);
        if (delta > 0) {
            length -= delta;
            MemorySegment.copy(values, 0, ms1, JAVA_BYTE, index, length);
            final MemorySegment ms2 = segments.get(bufferIndex + 1);
            MemorySegment.copy(values, length, ms2, JAVA_BYTE, 0, delta);
        } else {
            MemorySegment.copy(values, 0, ms1, JAVA_BYTE, index, length);
        }
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        int delta = index + length - segmentSizeInBytes;
        final MemorySegment ms1 = segments.get(bufferIndex);
        if (delta > 0) {
            length -= delta;
            MemorySegment.copy(ms1, JAVA_BYTE, index, values, 0, length);

            final MemorySegment ms2 = segments.get(bufferIndex + 1);
            MemorySegment.copy(ms2, JAVA_BYTE, 0, values, length, delta);
        } else {
            // MemorySegment srcSegment, ValueLayout srcLayout, long srcOffset, Object dstArray, int dstIndex, int elementCount
            MemorySegment.copy(ms1, JAVA_BYTE, index, values, 0, length);
        }
    }

    @Override
    public void setByte(long bytePos, byte value) {
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        final MemorySegment ms1 = segments.get(bufferIndex);
        ms1.set(JAVA_BYTE, index, value);
    }

    @Override
    public byte getByte(long bytePos) {
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        final MemorySegment ms1 = segments.get(bufferIndex);
        return ms1.get(JAVA_BYTE, index);
    }

    @Override
    public long getCapacity() {
        long cap = 0;
        for (MemorySegment ms : segments) {
            cap += ms.byteSize();
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
        arena.close();
        for (int i = from; i < to; i++) {
            segments.set(i, null);
        }
    }

    @Override
    public DAType getType() {
        return DAType.MMAP_FOREIGN;
    }
}
