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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class NewMMapDataAccess implements NewDataAccess {
    private static final String GH_FILE_MARKER = "GH";
    private final String path;
    private final boolean readOnly;
    private final RandomAccessFile raFile;
    private final ByteOrder byteOrder;
    private final List<MappedByteBuffer> segments;
    private final BitUtil bitUtil;
    private final int bytesPerSegment;
    // these two will be derived from bytesPerSegment and are only stored for faster index calculations
    private final int log2bytesPerSegment;
    private final int offsetDivisor;

    public static class Builder {
        private String path = "";
        private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
        // ~1MB per segment is the default
        private int bytesPerSegment = 1 << 20;
        private boolean readOnly = false;

        Builder setPath(String path) {
            this.path = path;
            return this;
        }

        Builder setByteOrder(ByteOrder byteOrder) {
            this.byteOrder = byteOrder;
            return this;
        }

        Builder setBytesPerSegment(int bytesPerSegment) {
            this.bytesPerSegment = bytesPerSegment;
            return this;
        }

        Builder setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        NewMMapDataAccess build() {
            return new NewMMapDataAccess(path, byteOrder, bytesPerSegment, readOnly);
        }
    }

    private NewMMapDataAccess(String path, ByteOrder byteOrder, int bytesPerSegment, boolean readOnly) {
        if (bytesPerSegment < 2)
            throw new IllegalArgumentException("bytesPerSegment must be >= 2");
        if (!isPowerOfTwo(bytesPerSegment))
            throw new IllegalArgumentException("bytesPerSegment must be a power of two, but got: " + bytesPerSegment);
        this.path = path;
        this.readOnly = readOnly;
        try {
            raFile = new RandomAccessFile(path, readOnly ? "r" : "rw");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.byteOrder = byteOrder;
        segments = new ArrayList<>();
        bitUtil = BitUtil.get(byteOrder);
        this.bytesPerSegment = bytesPerSegment;
        this.log2bytesPerSegment = log2(bytesPerSegment);
        this.offsetDivisor = bytesPerSegment - 1;
    }

    public static NewMMapDataAccess load(String path, boolean readOnly) {
        File file = new File(path);
        if (!file.exists() || file.length() == 0)
            return null;
        int numSegments;
        int bytesPerSegment;
        ByteOrder byteOrder;
        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            String fileMarker = raf.readUTF();
            if (!GH_FILE_MARKER.equals(fileMarker))
                throw new IllegalArgumentException("Not a GraphHopper file, expected 'GH' file marker, but was " + fileMarker);
            numSegments = raf.readInt();
            bytesPerSegment = raf.readInt();
            byteOrder = raf.readInt() == 1 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        } catch (IOException e) {
            throw new UncheckedIOException("Problem while loading " + path, e);
        }
        NewMMapDataAccess da = new NewMMapDataAccess(path, byteOrder, bytesPerSegment, readOnly);
        da.mapSegments((long) numSegments * bytesPerSegment);
        return da;
    }

    public static void flush(NewMMapDataAccess da) {
        try {
            for (MappedByteBuffer bb : da.segments)
                bb.force();
            da.raFile.seek(0);
            da.raFile.writeUTF(GH_FILE_MARKER);
            da.raFile.writeInt(da.segments.size());
            da.raFile.writeInt(da.bytesPerSegment);
            da.raFile.writeInt(da.byteOrder == ByteOrder.BIG_ENDIAN ? 1 : 0);

            // this could be necessary too
            // http://stackoverflow.com/q/14011398/194609
            da.raFile.getFD().sync();
            // equivalent to raFile.getChannel().force(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        return mapSegments(bytes);
    }

    @Override
    public void flush() {
        NewMMapDataAccess.flush(this);
    }

    private boolean mapSegments(long byteCount) {
        long capacity = (long) segments.size() * bytesPerSegment;
        long newBytes = byteCount - capacity;
        if (newBytes <= 0)
            return false;
        long longBytesPerSegment = bytesPerSegment;
        int segmentsToMap = (int) (byteCount / longBytesPerSegment);
        if (segmentsToMap < 0)
            throw new IllegalStateException("Too many segments needs to be allocated. Increase segmentSize.");

        if (byteCount % longBytesPerSegment != 0)
            segmentsToMap++;

        if (segmentsToMap == 0)
            throw new IllegalStateException("0 segments are not allowed.");

        // todonow
        int offset = 20;
        long bufferStart = offset;
        int i = 0;
        long newFileLength = offset + segmentsToMap * longBytesPerSegment;
        try {
            // ugly remapping
            // http://stackoverflow.com/q/14011919/194609
            // This approach is probably problematic but a bit faster if done often.
            // Here we rely on the OS+file system that increasing the file
            // size has no effect on the old mappings!
            bufferStart += segments.size() * longBytesPerSegment;
            int newSegments = segmentsToMap - segments.size();
            // rely on automatically increasing when mapping
            // raFile.setLength(newFileLength);
            for (; i < newSegments; i++) {
                segments.add(newByteBuffer(bufferStart, longBytesPerSegment));
                bufferStart += longBytesPerSegment;
            }
            return true;
        } catch (IOException ex) {
            // we could get an exception here if buffer is too small and area too large
            // e.g. I got an exception for the 65421th buffer (probably around 2**16 == 65536)
            throw new RuntimeException("Couldn't map buffer " + i + " of " + segmentsToMap + " with " + longBytesPerSegment
                    + " for " + path + " at position " + bufferStart + " for " + byteCount + " bytes with offset " + offset
                    + ", new fileLength:" + newFileLength + ", " + Helper.getMemInfo(), ex);
        }
    }

    private MappedByteBuffer newByteBuffer(long offset, long byteCount) throws IOException {
        // If we request a buffer larger than the file length, it will automatically increase the file length!
        // Will this cause problems? http://stackoverflow.com/q/14011919/194609
        // For trimTo we need to reset the file length later to reduce that size
        MappedByteBuffer buf = null;
        IOException ioex = null;
        // One retry if it fails. It could fail e.g. if previously buffer wasn't yet unmapped from the jvm
        for (int trial = 0; trial < 1; ) {
            try {
                buf = raFile.getChannel().map(
                        readOnly ? FileChannel.MapMode.READ_ONLY : FileChannel.MapMode.READ_WRITE, offset, byteCount);
                break;
            } catch (IOException tmpex) {
                ioex = tmpex;
                trial++;
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
    public void setInt(long bytePos, int value) {
        int segment = getSegment(bytePos);
        int offset = getOffset(bytePos);
        // todonow: synchronized?
        if (offset + 4 > bytesPerSegment)
            for (int i = 0; i < 4; i++)
                if (offset + i < bytesPerSegment)
                    segments.get(segment).put(offset + i, bitUtil.getByte(value, i));
                else
                    segments.get(segment + 1).put(offset + i - bytesPerSegment, bitUtil.getByte(value, i));
        else
            segments.get(segment).putInt(offset, value);
    }

    @Override
    public int getInt(long bytePos) {
        int segment = getSegment(bytePos);
        int offset = getOffset(bytePos);
        // todonow: synchronized
        if (offset + 4 > bytesPerSegment)
            return bitUtil.getInt(
                    offset < bytesPerSegment ? segments.get(segment).get(offset) : segments.get(segment + 1).get(offset - bytesPerSegment),
                    offset + 1 < bytesPerSegment ? segments.get(segment).get(offset + 1) : segments.get(segment + 1).get(offset + 1 - bytesPerSegment),
                    offset + 2 < bytesPerSegment ? segments.get(segment).get(offset + 2) : segments.get(segment + 1).get(offset + 2 - bytesPerSegment),
                    offset + 3 < bytesPerSegment ? segments.get(segment).get(offset + 3) : segments.get(segment + 1).get(offset + 3 - bytesPerSegment)
            );
        else
            return segments.get(segment).getInt(offset);
    }

    private int getSegment(long bytePos) {
        return (int) bytePos >>> log2bytesPerSegment;
    }

    private int getOffset(long bytePos) {
        return (int) bytePos & offsetDivisor;
    }

    private static boolean isPowerOfTwo(int x) {
        return x != 0 && (x & (x - 1)) == 0;
    }

    private static int log2(int x) {
        if (x <= 0)
            throw new IllegalArgumentException("x must be < 0, got: " + x);
        return 31 - Integer.numberOfLeadingZeros(x);
    }
}
