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

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author Peter Karich
 */
public abstract class AbstractDataAccess implements DataAccess {

    private static final int SEGMENT_SIZE_MIN = 1 << 7;
    private static final int SEGMENT_SIZE_DEFAULT = 16 * 1 << 20;
    protected static final String DATAACESS_MARKER = "GHDA1";
    // reserve some space for downstream usage (in classes using/exting this)
    protected static final int HEADER_OFFSET = 20 * 4 + 20;
    protected int header[] = new int[(HEADER_OFFSET - 20) / 4];
    protected static byte[] EMPTY = new byte[1024];
    protected int segmentSize = SEGMENT_SIZE_DEFAULT;

    @Override
    public void close() {
        // do not call flush() on close - let others decide
    }

    @Override
    public void setHeader(int index, int value) {
        header[index] = value;
    }

    @Override
    public int getHeader(int index) {
        return header[index];
    }

    /**
     * @return the remaining space in bytes
     */
    protected void writeHeader(RandomAccessFile file, long length, int segmentSize) throws IOException {
        file.seek(0);
        file.writeUTF(DATAACESS_MARKER);
        file.writeLong(length);
        file.writeInt(segmentSize);
        for (int i = 0; i < header.length; i++) {
            file.writeInt(header[i]);
        }
    }

    protected long readHeader(RandomAccessFile raFile) throws IOException {
        long bytes = raFile.readLong();
        setSegmentSize(raFile.readInt());
        for (int i = 0; i < header.length; i++) {
            header[i] = raFile.readInt();
        }
        return bytes;
    }

    @Override
    public DataAccess copyTo(DataAccess da) {
        for (int h = 0; h < header.length; h++) {
            da.setHeader(h, getHeader(h));
        }
        da.ensureCapacity(capacity());
        long max = capacity() / 4;
        for (long l = 0; l < max; l++) {
            da.setInt(l, getInt(l));
        }
        return da;
    }

    @Override
    public DataAccess setSegmentSize(int bytes) {
        int tmp = (int) (Math.log(bytes) / Math.log(2));
        segmentSize = Math.max((int) Math.pow(2, tmp), SEGMENT_SIZE_MIN);
        return this;
    }
}
