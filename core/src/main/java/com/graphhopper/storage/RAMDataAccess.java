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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * This is an in-memory byte-based data structure with the possibility to be stored on flush().
 * Read thread-safe.
 * <p>
 *
 * @author Peter Karich
 */
public class RAMDataAccess extends AbstractDataAccess {
    private byte[][] segments = new byte[0][];
    private boolean store;
    // we could also use UNSAFE but it is not really faster (see #3005)
    private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN).withInvokeExactBehavior();
    private static final VarHandle SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN).withInvokeExactBehavior();

    RAMDataAccess(String name, String location, boolean store, int segmentSize) {
        super(name, location, segmentSize);
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
    public RAMDataAccess create(long bytes) {
        if (segments.length > 0)
            throw new IllegalThreadStateException("already created");

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
            try (RandomAccessFile raFile = new RandomAccessFile(getFullName(), "r")) {
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
            try (RandomAccessFile raFile = new RandomAccessFile(getFullName(), "rw")) {
                long len = getCapacity();
                writeHeader(raFile, len, segmentSizeInBytes);
                raFile.seek(HEADER_OFFSET);
                // raFile.writeInt() <- too slow, so copy into byte array
                for (int s = 0; s < segments.length; s++) {
                    byte[] area = segments[s];
                    raFile.write(area);
                }
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
        if (index + 3 >= segmentSizeInBytes) {
            // seldom and special case if int has to be written into two separate segments
            byte[] b1 = segments[bufferIndex], b2 = segments[bufferIndex + 1];
            if (index + 1 >= segmentSizeInBytes) {
                bitUtil.fromUInt3(b2, value >>> 8, 0);
                b1[index] = (byte) value;
            } else if (index + 2 >= segmentSizeInBytes) {
                bitUtil.fromShort(b2, (short) (value >>> 16), 0);
                bitUtil.fromShort(b1, (short) value, index);
            } else {
                // index + 3 >= segmentSizeInBytes
                b2[0] = (byte) (value >>> 24);
                bitUtil.fromUInt3(b1, value, index);
            }
        } else {
            INT.set(segments[bufferIndex], index, value);
        }
    }

    @Override
    public final int getInt(long bytePos) {
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 3 >= segmentSizeInBytes) {
            byte[] b1 = segments[bufferIndex], b2 = segments[bufferIndex + 1];
            if (index + 1 >= segmentSizeInBytes)
                return (b2[2] & 0xFF) << 24 | (b2[1] & 0xFF) << 16 | (b2[0] & 0xFF) << 8 | (b1[index] & 0xFF);
            if (index + 2 >= segmentSizeInBytes)
                return (b2[1] & 0xFF) << 24 | (b2[0] & 0xFF) << 16 | (b1[index + 1] & 0xFF) << 8 | (b1[index] & 0xFF);
            // index + 3 >= segmentSizeInBytes
            return (b2[0] & 0xFF) << 24 | (b1[index + 2] & 0xFF) << 16 | (b1[index + 1] & 0xFF) << 8 | (b1[index] & 0xFF);
        }
        return (int) INT.get(segments[bufferIndex], index);
    }

    @Override
    public final void setShort(long bytePos, short value) {
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 1 >= segmentSizeInBytes) {
            // seldom and special case if short has to be written into two separate segments
            segments[bufferIndex][index] = (byte) (value);
            segments[bufferIndex + 1][0] = (byte) (value >>> 8);
        } else {
            SHORT.set(segments[bufferIndex], index, value);
        }
    }

    @Override
    public final short getShort(long bytePos) {
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 1 >= segmentSizeInBytes)
            return (short) ((segments[bufferIndex + 1][0] & 0xFF) << 8 | (segments[bufferIndex][index] & 0xFF));

        return (short) SHORT.get(segments[bufferIndex], index);
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        assert segments.length > 0 : "call create or loadExisting before usage!";
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
        assert segments.length > 0 : "call create or loadExisting before usage!";
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
    public final void setByte(long bytePos, byte value) {
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        segments[bufferIndex][index] = value;
    }

    @Override
    public final byte getByte(long bytePos) {
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        return segments[bufferIndex][index];
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
    public DAType getType() {
        if (isStoring())
            return DAType.RAM_STORE;
        return DAType.RAM;
    }
}
