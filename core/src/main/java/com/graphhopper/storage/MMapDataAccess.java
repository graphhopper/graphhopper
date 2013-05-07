/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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
 * This is a data structure which uses the operating system to synchronize
 * between disc and memory. Do not use this from multiple threads!
 *
 * @author Peter Karich
 */
@NotThreadSafe
public class MMapDataAccess extends AbstractDataAccess {

    private RandomAccessFile raFile;
    private List<ByteBuffer> segments = new ArrayList<ByteBuffer>();
    private ByteOrder order;
    private boolean cleanAndRemap = false;
    private transient boolean closed = false;
    private transient int segmentSizePower;
    private transient int indexDivisor;

    MMapDataAccess() {
        this(null, null);
        throw new IllegalStateException("reserved for direct mapped memory");
    }

    MMapDataAccess(String name, String location) {
        super(name, location);
    }

    MMapDataAccess cleanAndRemap(boolean cleanAndRemap) {
        this.cleanAndRemap = cleanAndRemap;
        return this;
    }

    private void initRandomAccessFile() {
        if (raFile != null)
            return;

        try {
            // raFile necessary for loadExisting and create
            raFile = new RandomAccessFile(fullName(), "rw");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public MMapDataAccess create(long bytes) {
        if (!segments.isEmpty())
            throw new IllegalThreadStateException("already created");
        initRandomAccessFile();
        bytes = Math.max(10 * 4, bytes);
        segmentSize(segmentSizeInBytes);
        ensureCapacity(bytes);
        return this;
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
     * Makes it possible to force the order. E.g. if we create the file on a
     * host system and copy it to a different like android.
     * http://en.wikipedia.org/wiki/Endianness
     */
    public MMapDataAccess byteOrder(ByteOrder order) {
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
            if (cleanAndRemap) {
                newSegments = segmentsToMap;
                clean(0, segments.size());
                segments.clear();
            } else {
                // This approach is probably problematic but a bit faster if done often.
                // Here we rely on the OS+file system that increasing the file 
                // size has no effect on the old mappings!
                bufferStart += segments.size() * longSegmentSize;
                newSegments = segmentsToMap - segments.size();            
            }
            // rely on automatically increasing when mapping
//            raFile.setLength(newFileLength);
            for (; i < newSegments; i++) {
                segments.add(newByteBuffer(bufferStart, longSegmentSize));
                bufferStart += longSegmentSize;
            }
        } catch (IOException ex) {
            // we could get an exception here if buffer is too small and area too large
            // e.g. I got an exception for the 65421th buffer (probably around 2**16 == 65536)
            throw new RuntimeException("Couldn't map buffer " + i + " of " + segmentsToMap
                    + " at position " + bufferStart + " for " + byteCount
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
        for (int trial = 0; trial < 1;) {
            try {
                buf = raFile.getChannel().map(FileChannel.MapMode.READ_WRITE, offset, byteCount);
                break;
            } catch (IOException tmpex) {
                ioex = tmpex;
                trial++;
                cleanHack();
                try {
                    Thread.sleep(5);
                } catch (InterruptedException iex) {
                }
            }
        }
        if (buf == null) {
            if (ioex == null)
                throw new AssertionError("internal problem as ioex shouldn't be null");
            throw ioex;
        }
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
        File file = new File(fullName());
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
            throw new RuntimeException("Problem while loading " + fullName(), ex);
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
        clean(0, segments.size());
        segments.clear();
        Helper.close(raFile);
        closed = true;
    }

    private void cleanHack() {
        // trying to force the release of the mapped ByteBuffer
        System.gc();
    }

    @Override
    public final void setInt(long longIndex, int value) {
        // convert longIndex to byte index => *4
        longIndex <<= 2;
        int bufferIndex = (int) (longIndex >>> segmentSizePower);
        int index = (int) (longIndex & indexDivisor);
        segments.get(bufferIndex).putInt(index, value);
    }

    @Override
    public final int getInt(long longIndex) {
        longIndex <<= 2;
        int bufferIndex = (int) (longIndex >>> segmentSizePower);
        int index = (int) (longIndex & indexDivisor);
        return segments.get(bufferIndex).getInt(index);
    }

    @Override
    public DataAccess segmentSize(int bytes) {
        super.segmentSize(bytes);
        segmentSizePower = (int) (Math.log(segmentSizeInBytes) / Math.log(2));
        indexDivisor = segmentSizeInBytes - 1;
        return this;
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
    public int segments() {
        return segments.size();
    }

    /**
     * Cleans up MappedByteBuffers. Be sure you bring the segments list in a
     * consistent state afterwards.
     *
     * @param from inclusive
     * @param to exclusive
     */
    private void clean(int from, int to) {
        for (int i = from; i < to; i++) {
            ByteBuffer bb = segments.get(i);
            Helper.cleanMappedByteBuffer(bb);
            segments.set(i, null);
        }
        cleanHack();
    }

    @Override
    public void trimTo(long capacity) {
        if (capacity < segmentSizeInBytes)
            capacity = segmentSizeInBytes;
        int remainingSegNo = (int) (capacity / segmentSizeInBytes);
        if (capacity % segmentSizeInBytes != 0)
            remainingSegNo++;

        clean(remainingSegNo, segments.size());
        segments = new ArrayList<ByteBuffer>(segments.subList(0, remainingSegNo));
               
        try {
            // windows does not allow changing the length of an open files
            if(!Constants.WINDOWS)                
                // reduce file size
                raFile.setLength(HEADER_OFFSET + remainingSegNo * segmentSizeInBytes);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }        
    }

    boolean releaseSegment(int segNumber) {
        ByteBuffer segment = segments.get(segNumber);
        if (segment instanceof MappedByteBuffer)
            ((MappedByteBuffer) segment).force();

        Helper.cleanMappedByteBuffer(segment);
        segments.set(segNumber, null);
        cleanHack();
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
