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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Optional;

public class NewRAMDataAccess implements NewDataAccess {
    private static final String GH_FILE_MARKER = "GH";
    private final BitUtil bitUtil;
    private byte[][] segments;
    private final int bytesPerSegment;
    // these two will be derived from bytesPerSegment and are only stored for faster index calculations
    private final int log2bytesPerSegment;
    private final int offsetDivisor;

    public NewRAMDataAccess() {
        // ~1MB per segment is the default
        this(1 << 20);
    }

    public NewRAMDataAccess(int bytesPerSegment) {
        this(new byte[0][], bytesPerSegment);
    }

    private NewRAMDataAccess(byte[][] segments, int bytesPerSegment) {
        if (bytesPerSegment < 2)
            throw new IllegalArgumentException("bytesPerSegment must be >= 2");
        if (!isPowerOfTwo(bytesPerSegment))
            throw new IllegalArgumentException("bytesPerSegment must be a power of two, but got: " + bytesPerSegment);
        for (byte[] segment : segments)
            if (segment.length != bytesPerSegment)
                throw new IllegalArgumentException("found segment with invalid length: " + segment.length + ", expected: " + bytesPerSegment);
        bitUtil = BitUtil.get(ByteOrder.BIG_ENDIAN);
        this.segments = segments;
        this.bytesPerSegment = bytesPerSegment;
        this.log2bytesPerSegment = log2(bytesPerSegment);
        this.offsetDivisor = bytesPerSegment - 1;
    }

    public static NewRAMDataAccess load(String path) {
        File file = new File(path);
        if (!file.exists() || file.length() == 0)
            return null;
        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            String fileMarker = raf.readUTF();
            if (!GH_FILE_MARKER.equals(fileMarker))
                throw new IllegalArgumentException("Not a GraphHopper file, expected 'GH' file marker, but was " + fileMarker);
            int numSegments = raf.readInt();
            int bytesPerSegment = raf.readInt();

            byte[][] segments = new byte[numSegments][];
            for (int s = 0; s < numSegments; s++) {
                byte[] bytes = new byte[bytesPerSegment];
                if (raf.read(bytes) <= 0)
                    throw new IllegalStateException("segment " + s + " is empty, path: " + path);
                segments[s] = bytes;
            }
            return new NewRAMDataAccess(segments, bytesPerSegment);
        } catch (IOException e) {
            throw new UncheckedIOException("Problem while loading " + path, e);
        }
    }

    public static void flush(NewRAMDataAccess da, String path) {
        try (RandomAccessFile raf = new RandomAccessFile(path, "rw")) {
            raf.writeUTF(GH_FILE_MARKER);
            raf.writeInt(da.segments.length);
            raf.writeInt(da.bytesPerSegment);
            for (byte[] segment : da.segments)
                raf.write(segment);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't store bytes to " + path, e);
        }
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        long capacity = (long) segments.length * bytesPerSegment;
        long newBytes = bytes - capacity;
        if (newBytes <= 0)
            return false;
        int segmentsToCreate = (int) (newBytes / bytesPerSegment);
        if (newBytes % bytesPerSegment != 0)
            segmentsToCreate++;
        try {
            byte[][] newSegments = Arrays.copyOf(segments, segments.length + segmentsToCreate);
            for (int i = segments.length; i < newSegments.length; ++i)
                newSegments[i] = new byte[bytesPerSegment];
            segments = newSegments;
        } catch (OutOfMemoryError err) {
            throw new OutOfMemoryError(err.getMessage() + " - problem when allocating new memory. Old capacity: "
                    + capacity + ", new bytes: " + newBytes + ", bytesPerSegment: " + bytesPerSegment
                    + ", new segments: " + segmentsToCreate + ", existing: " + segments.length);
        }
        return true;
    }

    @Override
    public void setInt(long bytePos, int value) {
        int segment = getSegment(bytePos);
        int offset = getOffset(bytePos);
        if (offset + 4 > bytesPerSegment)
            for (int i = 0; i < 4; i++)
                if (offset + i < bytesPerSegment)
                    segments[segment][offset + i] = bitUtil.getByte(value, i);
                else
                    segments[segment + 1][offset + i - bytesPerSegment] = bitUtil.getByte(value, i);
        else
            bitUtil.fromInt(segments[segment], value, offset);
    }

    @Override
    public int getInt(long bytePos) {
        int segment = getSegment(bytePos);
        int offset = getOffset(bytePos);
        if (offset + 4 > bytesPerSegment)
            return bitUtil.getInt(
                    offset < bytesPerSegment ? segments[segment][offset] : segments[segment + 1][offset - bytesPerSegment],
                    offset + 1 < bytesPerSegment ? segments[segment][offset + 1] : segments[segment + 1][offset + 1 - bytesPerSegment],
                    offset + 2 < bytesPerSegment ? segments[segment][offset + 2] : segments[segment + 1][offset + 2 - bytesPerSegment],
                    offset + 3 < bytesPerSegment ? segments[segment][offset + 3] : segments[segment + 1][offset + 3 - bytesPerSegment]
            );
        else
            return bitUtil.toInt(segments[segment], offset);
    }

    public void flush(String path) {
        NewRAMDataAccess.flush(this, path);
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
