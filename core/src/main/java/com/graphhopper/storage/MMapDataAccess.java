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
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public final class MMapDataAccess extends AbstractDataAccess {

    public static final ValueLayout.OfShort UNALIGNED_SHORT = (ValueLayout.OfShort) MemoryLayout.valueLayout(short.class, ByteOrder.nativeOrder()).withBitAlignment(8);
    public static final ValueLayout.OfLong HEADER_LONG = (ValueLayout.OfLong) MemoryLayout.valueLayout(long.class, ByteOrder.BIG_ENDIAN).withBitAlignment(8);
    public static final ValueLayout.OfInt HEADER_INT = (ValueLayout.OfInt) MemoryLayout.valueLayout(int.class, ByteOrder.BIG_ENDIAN);

    private MemorySegment memorySegment;
    private MemorySegment headerHeader;
    private MemorySegment header;
    private RandomAccessFile file;

    long currentSize;

    MMapDataAccess(String name, String location, int segmentSize) {
        super(name, location, segmentSize);
    }

    @Override
    public MMapDataAccess create(long bytes) {
        try {
            file = getRandomAccessFile(bytes);
            headerHeader = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 16, MemorySession.global());
            header = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 16, HEADER_OFFSET - 16, MemorySession.global());
            currentSize = 0;
            ensureCapacity(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    private RandomAccessFile getRandomAccessFile(long bytes) throws IOException {
        File wurst = new File(getFullName());
        RandomAccessFile file = new RandomAccessFile(wurst, "rw");
        file.writeUTF("GH");
        file.writeLong(bytes + (segmentSizeInBytes - (bytes % segmentSizeInBytes)));
        file.writeInt(segmentSizeInBytes);
        return file;
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        if (bytes > currentSize) {
            long l = bytes / getSegmentSize();
            try {
                long newSize = (l + 1) * getSegmentSize();
                memorySegment = file.getChannel().map(FileChannel.MapMode.READ_WRITE, HEADER_OFFSET, HEADER_OFFSET + newSize, MemorySession.global());
                currentSize = newSize;
                headerHeader.set(HEADER_LONG, 4, currentSize);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    @Override
    public boolean loadExisting() {
        try {
            File wurst = new File(getFullName());
            if (!wurst.exists())
                return false;
            file = new RandomAccessFile(wurst, "rw");
            file.readUTF();
            currentSize = file.readLong();
            setSegmentSize(file.readInt());
            headerHeader = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 16, MemorySession.global());
            header = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 16, HEADER_OFFSET - 16, MemorySession.global());
            memorySegment = file.getChannel().map(FileChannel.MapMode.READ_WRITE, HEADER_OFFSET,  wurst.length() - HEADER_OFFSET, MemorySession.global());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public void flush() {
        memorySegment.force();
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public void setInt(long bytePos, int value) {
        memorySegment.set(JAVA_INT, bytePos, value);
    }

    @Override
    public int getInt(long bytePos) {
        return memorySegment.get(JAVA_INT, bytePos);
    }

    @Override
    public void setShort(long bytePos, short value) {
        memorySegment.set(UNALIGNED_SHORT, bytePos, value);
    }

    @Override
    public short getShort(long bytePos) {
        return memorySegment.get(UNALIGNED_SHORT, bytePos);
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        for (int i = 0; i < length; i++) {
            memorySegment.set(ValueLayout.JAVA_BYTE, bytePos + i, values[i]);
        }
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        for (int i = 0; i < length; i++) {
            values[i] = memorySegment.get(ValueLayout.JAVA_BYTE, bytePos + i);
        }
    }

    @Override
    public void setByte(long bytePos, byte value) {
        memorySegment.set(ValueLayout.JAVA_BYTE, bytePos, value);
    }

    @Override
    public byte getByte(long bytePos) {
        return memorySegment.get(ValueLayout.JAVA_BYTE, bytePos);
    }

    @Override
    public long getCapacity() {
        return memorySegment.byteSize();
    }

    @Override
    public DAType getType() {
        return DAType.MMAP;
    }

    @Override
    public void setHeader(int bytePos, int value) {
        header.set(HEADER_INT, bytePos, value);
    }

    @Override
    public int getHeader(int bytePos) {
        return header.get(HEADER_INT, bytePos);
    }

}
