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

import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Helper;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;

/**
 * @author Peter Karich
 */
public abstract class AbstractDataAccess implements DataAccess {
    protected static final int SEGMENT_SIZE_MIN = 1 << 7;
    // reserve some space for downstream usage (in classes using/extending this)
    protected static final int HEADER_OFFSET = 20 * 4 + 20;
    protected static final byte[] EMPTY = new byte[1024];
    private static final int SEGMENT_SIZE_DEFAULT = 1 << 20;
    protected final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    protected final BitUtil bitUtil = BitUtil.LITTLE;
    private final String location;
    protected int[] header = new int[(HEADER_OFFSET - 20) / 4];
    protected String name;
    protected int segmentSizeInBytes;
    protected int segmentSizePower;
    protected int indexDivisor;
    protected boolean closed = false;

    public AbstractDataAccess(String name, String location, int segmentSize) {
        this.name = name;
        if (!Helper.isEmpty(location) && !location.endsWith("/"))
            throw new IllegalArgumentException("Create DataAccess object via its corresponding Directory!");

        this.location = location;
        if (segmentSize < 0)
            segmentSize = SEGMENT_SIZE_DEFAULT;
        setSegmentSize(segmentSize);
    }

    @Override
    public String getName() {
        return name;
    }

    protected String getFullName() {
        return location + name;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void setHeader(int bytePos, int value) {
        bytePos >>= 2;
        header[bytePos] = value;
    }

    @Override
    public int getHeader(int bytePos) {
        bytePos >>= 2;
        return header[bytePos];
    }

    /**
     * Writes some internal data into the beginning of the specified file.
     */
    protected void writeHeader(RandomAccessFile file, long length, int segmentSize) throws IOException {
        file.seek(0);
        file.writeUTF("GH");
        file.writeLong(length);
        file.writeInt(segmentSize);
        for (int i = 0; i < header.length; i++) {
            file.writeInt(header[i]);
        }
    }

    protected long readHeader(RandomAccessFile raFile) throws IOException {
        raFile.seek(0);
        if (raFile.length() == 0)
            return -1;

        String versionHint = raFile.readUTF();
        if (!"GH".equals(versionHint))
            throw new IllegalArgumentException("Not a GraphHopper file " + getFullName() + "! Expected 'GH' as file marker but was " + versionHint);

        long bytes = raFile.readLong();
        setSegmentSize(raFile.readInt());
        for (int i = 0; i < header.length; i++) {
            header[i] = raFile.readInt();
        }
        return bytes;
    }

    DataAccess setSegmentSize(int bytes) {
        if (bytes > 0) {
            // segment size should be a power of 2
            int tmp = (int) (Math.log(bytes) / Math.log(2));
            segmentSizeInBytes = Math.max((int) Math.pow(2, tmp), SEGMENT_SIZE_MIN);
        }
        segmentSizePower = (int) (Math.log(segmentSizeInBytes) / Math.log(2));
        indexDivisor = segmentSizeInBytes - 1;
        return this;
    }

    @Override
    public int getSegmentSize() {
        return segmentSizeInBytes;
    }

    @Override
    public String toString() {
        return getFullName();
    }

    public boolean isStoring() {
        return true;
    }

}
