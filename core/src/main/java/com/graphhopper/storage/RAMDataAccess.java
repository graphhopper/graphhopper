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

import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * This is an in-memory byte-based data structure with the possibility to be stored on flush().
 * Thread safe.
 * <p>
 *
 * @author Peter Karich
 */
public class RAMDataAccess extends AbstractDataAccess {
    private byte[][] segments = new byte[0][];
    private boolean store;

    RAMDataAccess(String name, String location, boolean store, ByteOrder order) {
        super(name, location, order);
        this.store = store;
    }

    /**
     * @param store true if in-memory data should be saved when calling flush
     */
    public RAMDataAccess store(boolean store) {
        this.store = store;
        return this;
    }

    @Override
    public boolean isStoring() {
        return store;
    }

    @Override
    public DataAccess copyTo(DataAccess da) {
        if (da instanceof RAMDataAccess) {
            copyHeader(da);
            RAMDataAccess rda = (RAMDataAccess) da;
            // TODO PERFORMANCE we could reuse rda segments!
            rda.segments = new byte[segments.length][];
            for (int i = 0; i < segments.length; i++) {
                byte[] area = segments[i];
                rda.segments[i] = Arrays.copyOf(area, area.length);
            }
            rda.setSegmentSize(segmentSizeInBytes);
            // leave id, store and close unchanged
            return da;
        } else {
            return super.copyTo(da);
        }
    }

    @Override
    public RAMDataAccess create(long bytes) {
        if (segments.length > 0)
            throw new IllegalThreadStateException("already created");
        
        setSegmentSize(segmentSizeInBytes);
        ensureCapacity(Math.max(10 * 4, bytes));
        return this;
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        if (bytes < 0)
            throw new IllegalArgumentException("new capacity has to be strictly positive");

        long cap = getCapacity();
        long newBytes = bytes - cap;
        if (newBytes <= 0)
            return false;

        int segmentsToCreate = (int) (newBytes / segmentSizeInBytes);
        if (newBytes % segmentSizeInBytes != 0)
            segmentsToCreate++;

        try {
            byte[][] newSegs = Arrays.copyOf(segments, segments.length + segmentsToCreate);
            for (int i = segments.length; i < newSegs.length; i++) {
                newSegs[i] = new byte[1 << segmentSizePower];
            }
            segments = newSegs;
        } catch (OutOfMemoryError err) {
            throw new OutOfMemoryError(err.getMessage() + " - problem when allocating new memory. Old capacity: "
                    + cap + ", new bytes:" + newBytes + ", segmentSizeIntsPower:" + segmentSizePower
                    + ", new segments:" + segmentsToCreate + ", existing:" + segments.length);
        }
        return true;
    }

    @Override
    public boolean loadExisting() {
        if (segments.length > 0)
            throw new IllegalStateException("already initialized");

        if (isClosed())
            throw new IllegalStateException("already closed");

        if (!store)
            return false;

        File file = new File(getFullName());
        if (!file.exists() || file.length() == 0)
            return false;

        try {
            RandomAccessFile raFile = new RandomAccessFile(getFullName(), "r");
            try {
                long byteCount = readHeader(raFile) - HEADER_OFFSET;
                if (byteCount < 0)
                    return false;

                raFile.seek(HEADER_OFFSET);
                // raFile.readInt() <- too slow                
                int segmentCount = (int) (byteCount / segmentSizeInBytes);
                if (byteCount % segmentSizeInBytes != 0)
                    segmentCount++;

                segments = new byte[segmentCount][];
                for (int s = 0; s < segmentCount; s++) {
                    byte[] bytes = new byte[segmentSizeInBytes];
                    int read = raFile.read(bytes);
                    if (read <= 0)
                        throw new IllegalStateException("segment " + s + " is empty? " + toString());

                    segments[s] = bytes;
                }
                return true;
            } finally {
                raFile.close();
            }
        } catch (IOException ex) {
            throw new RuntimeException("Problem while loading " + getFullName(), ex);
        }
    }

    @Override
    public void flush() {
        if (closed)
            throw new IllegalStateException("already closed");

        if (!store)
            return;

        try {
            RandomAccessFile raFile = new RandomAccessFile(getFullName(), "rw");
            try {
                long len = getCapacity();
                writeHeader(raFile, len, segmentSizeInBytes);
                raFile.seek(HEADER_OFFSET);
                // raFile.writeInt() <- too slow, so copy into byte array
                for (int s = 0; s < segments.length; s++) {
                    byte area[] = segments[s];
                    raFile.write(area);
                }
            } finally {
                raFile.close();
            }
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't store bytes to " + toString(), ex);
        }
    }

    @Override
    public final void setInt(long bytePos, int value) {
        assert segmentSizePower > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        assert index + 4 <= segmentSizeInBytes : "integer cannot be distributed over two segments";
        bitUtil.fromInt(segments[bufferIndex], value, index);
    }

    @Override
    public final int getInt(long bytePos) {
        assert segmentSizePower > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        assert index + 4 <= segmentSizeInBytes : "integer cannot be distributed over two segments";
        if (bufferIndex > segments.length) {
            LoggerFactory.getLogger(getClass()).error(getName() + ", segments:" + segments.length
                    + ", bufIndex:" + bufferIndex + ", bytePos:" + bytePos
                    + ", segPower:" + segmentSizePower);
        }
        return bitUtil.toInt(segments[bufferIndex], index);
    }

    @Override
    public final void setShort(long bytePos, short value) {
        assert segmentSizePower > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        assert index + 2 <= segmentSizeInBytes : "integer cannot be distributed over two segments";
        bitUtil.fromShort(segments[bufferIndex], value, index);
    }

    @Override
    public final short getShort(long bytePos) {
        assert segmentSizePower > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        assert index + 2 <= segmentSizeInBytes : "integer cannot be distributed over two segments";
        return bitUtil.toShort(segments[bufferIndex], index);
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        assert segmentSizePower > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        byte[] seg = segments[bufferIndex];
        int delta = index + length - segmentSizeInBytes;
        if (delta > 0) {
            length -= delta;
            System.arraycopy(values, 0, seg, index, length);
            seg = segments[bufferIndex + 1];
            System.arraycopy(values, length, seg, 0, delta);
        } else {
            System.arraycopy(values, 0, seg, index, length);
        }
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        assert segmentSizePower > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        byte[] seg = segments[bufferIndex];
        int delta = index + length - segmentSizeInBytes;
        if (delta > 0) {
            length -= delta;
            System.arraycopy(seg, index, values, 0, length);
            seg = segments[bufferIndex + 1];
            System.arraycopy(seg, 0, values, length, delta);
        } else {
            System.arraycopy(seg, index, values, 0, length);
        }
    }

    @Override
    public void close() {
        super.close();
        segments = new byte[0][];
        closed = true;
    }

    @Override
    public long getCapacity() {
        return (long) getSegments() * segmentSizeInBytes;
    }

    @Override
    public int getSegments() {
        return segments.length;
    }

    @Override
    public void trimTo(long capacity) {
        if (capacity > getCapacity()) {
            throw new IllegalStateException("Cannot increase capacity (" + getCapacity() + ") to " + capacity
                    + " via trimTo. Use ensureCapacity instead. ");
        }

        if (capacity < segmentSizeInBytes)
            capacity = segmentSizeInBytes;

        int remainingSegments = (int) (capacity / segmentSizeInBytes);
        if (capacity % segmentSizeInBytes != 0) {
            remainingSegments++;
        }

        segments = Arrays.copyOf(segments, remainingSegments);
    }

    @Override
    public void rename(String newName) {
        if (!checkBeforeRename(newName)) {
            return;
        }
        if (store) {
            super.rename(newName);
        }

        // in every case set the name
        name = newName;
    }

    @Override
    public DAType getType() {
        if (isStoring())
            return DAType.RAM_STORE;
        return DAType.RAM;
    }
}
