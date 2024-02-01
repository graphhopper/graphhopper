package com.graphhopper.storage;

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

public class OffHeapDataAccess extends AbstractDataAccess {

    private final Arena arena = Arena.ofShared();
    private final List<MemorySegment> segments = new ArrayList<>();

    OffHeapDataAccess(String name, String location, int segmentSize) {
        super(name, location, segmentSize);
    }

    @Override
    public OffHeapDataAccess create(long bytes) {
        if (!segments.isEmpty()) {
            throw new IllegalThreadStateException("already created");
        }
        bytes = Math.max(10 * 4, bytes);
        ensureCapacity(bytes);
        return this;
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        return allocate(bytes);
    }

    private boolean allocate(long byteCount) {
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

        int newSegments = segmentsToMap - segments.size();
        for (int i = 0; i < newSegments; i++) {
            segments.add(arena.allocate(longSegmentSize));
        }
        return true;
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

        try {
            try (RandomAccessFile raFile = new RandomAccessFile(getFullName(), "r")) {
                long byteCount = readHeader(raFile) - HEADER_OFFSET;
                if (byteCount < 0)
                    return false;

                raFile.seek(HEADER_OFFSET);
                // raFile.readInt() <- too slow
                int segmentCount = (int) (byteCount / segmentSizeInBytes);
                if (byteCount % segmentSizeInBytes != 0)
                    segmentCount++;

                if (!allocate((long) segmentCount * segmentSizeInBytes))
                    throw new IllegalArgumentException("Cannot allocate memory " + byteCount);

                // TODO NOW simpler/faster?
                //  raFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, byteCount);

                byte[] bytes = new byte[segmentSizeInBytes];
                for (int s = 0; s < segmentCount; s++) {
                    int read = raFile.read(bytes);
                    MemorySegment.copy(bytes, 0, segments.get(s), JAVA_BYTE, 0, read);

                    if (read < segmentSizeInBytes)
                        break;
                }
                return true;
            }
        } catch (IOException ex) {
            throw new RuntimeException("Problem while loading " + getFullName(), ex);
        }
    }

    @Override
    public void flush() {
        if (isClosed())
            throw new IllegalStateException("already closed");

        try {
            try (RandomAccessFile raFile = new RandomAccessFile(getFullName(), "rw")) {
                long len = getCapacity();
                writeHeader(raFile, len, segmentSizeInBytes);
                long offset = HEADER_OFFSET;
                FileChannel channel = raFile.getChannel();
                for (int s = 0; s < segments.size(); s++) {
                    offset += channel.write(segments.get(s).asByteBuffer(), offset);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't store bytes to " + toString(), ex);
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

