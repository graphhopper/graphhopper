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
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * This is an in-memory data structure based on an integer array. With the possibility to be stored
 * on flush().
 * <p>
 *
 * @author Peter Karich
 */
class RAMIntDataAccess extends AbstractDataAccess {
    private int[][] segments = new int[0][];
    private boolean closed = false;
    private boolean store;
    private transient int segmentSizeIntsPower;

    RAMIntDataAccess(String name, String location, boolean store, ByteOrder order) {
        super(name, location, order);
        this.store = store;
    }

    /**
     * @param store true if in-memory data should be saved when calling flush
     */
    public RAMIntDataAccess setStore(boolean store) {
        this.store = store;
        return this;
    }

    @Override
    public boolean isStoring() {
        return store;
    }

    @Override
    public DataAccess copyTo(DataAccess da) {
        if (da instanceof RAMIntDataAccess) {
            copyHeader(da);
            RAMIntDataAccess rda = (RAMIntDataAccess) da;
            // TODO PERFORMANCE we could reuse rda segments!
            rda.segments = new int[segments.length][];
            for (int i = 0; i < segments.length; i++) {
                int[] area = segments[i];
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
    public RAMIntDataAccess create(long bytes) {
        if (segments.length > 0)
            throw new IllegalThreadStateException("already created");

        // initialize transient values
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
            int[][] newSegs = Arrays.copyOf(segments, segments.length + segmentsToCreate);
            for (int i = segments.length; i < newSegs.length; i++) {
                newSegs[i] = new int[1 << segmentSizeIntsPower];
            }
            segments = newSegs;
            return true;
        } catch (OutOfMemoryError err) {
            throw new OutOfMemoryError(err.getMessage() + " - problem when allocating new memory. Old capacity: "
                    + cap + ", new bytes:" + newBytes + ", segmentSizeIntsPower:" + segmentSizeIntsPower
                    + ", new segments:" + segmentsToCreate + ", existing:" + segments.length);
        }
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
        if (!file.exists() || file.length() == 0) {
            return false;
        }
        try {
            RandomAccessFile raFile = new RandomAccessFile(getFullName(), "r");
            try {
                long byteCount = readHeader(raFile) - HEADER_OFFSET;
                if (byteCount < 0) {
                    return false;
                }
                byte[] bytes = new byte[segmentSizeInBytes];
                raFile.seek(HEADER_OFFSET);
                // raFile.readInt() <- too slow                
                int segmentCount = (int) (byteCount / segmentSizeInBytes);
                if (byteCount % segmentSizeInBytes != 0)
                    segmentCount++;

                segments = new int[segmentCount][];
                for (int s = 0; s < segmentCount; s++) {
                    int read = raFile.read(bytes) / 4;
                    int area[] = new int[read];
                    for (int j = 0; j < read; j++) {
                        area[j] = bitUtil.toInt(bytes, j * 4);
                    }
                    segments[s] = area;
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
        if (closed) {
            throw new IllegalStateException("already closed");
        }
        if (!store) {
            return;
        }
        try {
            RandomAccessFile raFile = new RandomAccessFile(getFullName(), "rw");
            try {
                long len = getCapacity();
                writeHeader(raFile, len, segmentSizeInBytes);
                raFile.seek(HEADER_OFFSET);
                // raFile.writeInt() <- too slow, so copy into byte array
                for (int s = 0; s < segments.length; s++) {
                    int area[] = segments[s];
                    int intLen = area.length;
                    byte[] byteArea = new byte[intLen * 4];
                    for (int i = 0; i < intLen; i++) {
                        bitUtil.fromInt(byteArea, area[i], i * 4);
                    }
                    raFile.write(byteArea);
                }
            } finally {
                raFile.close();
            }
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't store integers to " + toString(), ex);
        }
    }

    @Override
    public final void setInt(long bytePos, int value) {
        assert segmentSizeIntsPower > 0 : "call create or loadExisting before usage!";
        bytePos >>>= 2;
        int bufferIndex = (int) (bytePos >>> segmentSizeIntsPower);
        int index = (int) (bytePos & indexDivisor);
        segments[bufferIndex][index] = value;
    }

    @Override
    public final int getInt(long bytePos) {
        assert segmentSizeIntsPower > 0 : "call create or loadExisting before usage!";
        bytePos >>>= 2;
        int bufferIndex = (int) (bytePos >>> segmentSizeIntsPower);
        int index = (int) (bytePos & indexDivisor);
        return segments[bufferIndex][index];
    }

    @Override
    public final void setShort(long bytePos, short value) {
        assert segmentSizeIntsPower > 0 : "call create or loadExisting before usage!";
        if (bytePos % 4 != 0 && bytePos % 4 != 2)
            throw new IllegalMonitorStateException("bytePos of wrong multiple for RAMInt " + bytePos);

        long tmpIndex = bytePos >>> 2;
        int bufferIndex = (int) (tmpIndex >>> segmentSizeIntsPower);
        int index = (int) (tmpIndex & indexDivisor);
        int oldVal = segments[bufferIndex][index];
        if (tmpIndex * 4 == bytePos)
            segments[bufferIndex][index] = oldVal & 0xFFFF0000 | value & 0x0000FFFF;
        else
            segments[bufferIndex][index] = oldVal & 0x0000FFFF | value << 16;
    }

    @Override
    public final short getShort(long bytePos) {
        assert segmentSizeIntsPower > 0 : "call create or loadExisting before usage!";
        if (bytePos % 4 != 0 && bytePos % 4 != 2)
            throw new IllegalMonitorStateException("bytePos of wrong multiple for RAMInt " + bytePos);

        long tmpIndex = bytePos >> 2;
        int bufferIndex = (int) (tmpIndex >> segmentSizeIntsPower);
        int index = (int) (tmpIndex & indexDivisor);
        if (tmpIndex * 4 == bytePos)
            return (short) (segments[bufferIndex][index] & 0x0000FFFFL);
        else
            return (short) (segments[bufferIndex][index] >> 16);
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        throw new UnsupportedOperationException(toString() + " does not support byte based acccess. Use RAMDataAccess instead");
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        throw new UnsupportedOperationException(toString() + " does not support byte based acccess. Use RAMDataAccess instead");
    }

    @Override
    public void close() {
        super.close();
        segments = new int[0][];
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
    public DataAccess setSegmentSize(int bytes) {
        super.setSegmentSize(bytes);
        segmentSizeIntsPower = (int) (Math.log(segmentSizeInBytes / 4) / Math.log(2));
        indexDivisor = segmentSizeInBytes / 4 - 1;
        return this;
    }

    @Override
    public void trimTo(long capacity) {
        if (capacity < segmentSizeInBytes) {
            capacity = segmentSizeInBytes;
        }
        int remainingSegments = (int) (capacity / segmentSizeInBytes);
        if (capacity % segmentSizeInBytes != 0) {
            remainingSegments++;
        }

        segments = Arrays.copyOf(segments, remainingSegments);
    }

    boolean releaseSegment(int segNumber) {
        segments[segNumber] = null;
        return true;
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
    protected boolean isIntBased() {
        return true;
    }

    @Override
    public DAType getType() {
        if (isStoring())
            return DAType.RAM_INT_STORE;
        return DAType.RAM_INT;
    }
}
