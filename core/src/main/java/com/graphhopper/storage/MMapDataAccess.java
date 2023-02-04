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

import com.graphhopper.util.Constants;
import com.graphhopper.core.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A DataAccess implementation using a memory-mapped file, i.e. a facility of the
 * operating system to access a file like an area of RAM.
 * <p>
 * Java presents the mapped memory as a ByteBuffer, and ByteBuffer is not
 * thread-safe, which means that access to a ByteBuffer must be externally
 * synchronized.
 * <p>
 * This class itself is intended to be as thread-safe as other DataAccess
 * implementations are.
 * <p>
 * The exact behavior of memory-mapping is reported to be wildly platform-dependent.
 *
 * @author Peter Karich
 * @author Michael Zilske
 */
public final class MMapDataAccess extends AbstractDataAccess {

    private static final Logger LOGGER = LoggerFactory.getLogger(MMapDataAccess.class);

    private final boolean allowWrites;
    private RandomAccessFile raFile;
    private final List<MappedByteBuffer> segments = new ArrayList<>();

    MMapDataAccess(String name, String location, boolean allowWrites, int segmentSize) {
        super(name, location, segmentSize);
        this.allowWrites = allowWrites;
    }

    public static boolean jreIsMinimumJava9() {
        final StringTokenizer st = new StringTokenizer(System.getProperty("java.specification.version"), ".");
        int JVM_MAJOR_VERSION = Integer.parseInt(st.nextToken());
        int JVM_MINOR_VERSION;
        if (st.hasMoreTokens()) {
            JVM_MINOR_VERSION = Integer.parseInt(st.nextToken());
        } else {
            JVM_MINOR_VERSION = 0;
        }
        return JVM_MAJOR_VERSION > 1 || (JVM_MAJOR_VERSION == 1 && JVM_MINOR_VERSION >= 9);
    }

    public static void cleanMappedByteBuffer(final ByteBuffer buffer) {
        // TODO avoid reflection on every call
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                    if (jreIsMinimumJava9()) {
                        // >=JDK9 class sun.misc.Unsafe { void invokeCleaner(ByteBuffer buf) }
                        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                        // fetch the unsafe instance and bind it to the virtual MethodHandle
                        final Field f = unsafeClass.getDeclaredField("theUnsafe");
                        f.setAccessible(true);
                        final Object theUnsafe = f.get(null);
                        final Method method = unsafeClass.getDeclaredMethod("invokeCleaner", ByteBuffer.class);
                        try {
                            method.invoke(theUnsafe, buffer);
                            return null;
                        } catch (Throwable t) {
                            throw new RuntimeException(t);
                        }
                    }

                    if (buffer.getClass().getSimpleName().equals("MappedByteBufferAdapter")) {
                        if (!Constants.ANDROID)
                            throw new RuntimeException("MappedByteBufferAdapter only supported for Android at the moment");

                        // For Android 4.1 call ((MappedByteBufferAdapter)buffer).free() see #914
                        Class<?> directByteBufferClass = Class.forName("java.nio.MappedByteBufferAdapter");
                        callBufferFree(buffer, directByteBufferClass);
                    } else {
                        // <=JDK8 class DirectByteBuffer { sun.misc.Cleaner cleaner(Buffer buf) }
                        //        then call sun.misc.Cleaner.clean
                        final Class<?> directByteBufferClass = Class.forName("java.nio.DirectByteBuffer");
                        try {
                            final Method dbbCleanerMethod = directByteBufferClass.getMethod("cleaner");
                            dbbCleanerMethod.setAccessible(true);
                            // call: cleaner = ((DirectByteBuffer)buffer).cleaner()
                            final Object cleaner = dbbCleanerMethod.invoke(buffer);
                            if (cleaner != null) {
                                final Class<?> cleanerMethodReturnType = dbbCleanerMethod.getReturnType();
                                final Method cleanMethod = cleanerMethodReturnType.getDeclaredMethod("clean");
                                cleanMethod.setAccessible(true);
                                // call: ((sun.misc.Cleaner)cleaner).clean()
                                cleanMethod.invoke(cleaner);
                            }
                        } catch (NoSuchMethodException ex2) {
                            if (Constants.ANDROID)
                                // For Android 5.1.1 call ((DirectByteBuffer)buffer).free() see #933
                                callBufferFree(buffer, directByteBufferClass);
                            else
                                // ignore if method cleaner or clean is not available
                                LOGGER.warn("NoSuchMethodException | " + System.getProperty("java.version"), ex2);
                        }
                    }

                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Unable to unmap the mapped buffer", e);
        }
    }

    private static void callBufferFree(ByteBuffer buffer, Class<?> directByteBufferClass)
            throws InvocationTargetException, IllegalAccessException {
        try {
            final Method dbbFreeMethod = directByteBufferClass.getMethod("free");
            dbbFreeMethod.setAccessible(true);
            dbbFreeMethod.invoke(buffer);
        } catch (NoSuchMethodException ex2) {
            LOGGER.warn("NoSuchMethodException | " + System.getProperty("java.version"), ex2);
        }
    }

    private void initRandomAccessFile() {
        if (raFile != null) {
            return;
        }

        try {
            // raFile necessary for loadExisting and create
            raFile = new RandomAccessFile(getFullName(), allowWrites ? "rw" : "r");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public MMapDataAccess create(long bytes) {
        if (!segments.isEmpty()) {
            throw new IllegalThreadStateException("already created");
        }
        initRandomAccessFile();
        bytes = Math.max(10 * 4, bytes);
        ensureCapacity(bytes);
        return this;
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        return mapIt(HEADER_OFFSET, bytes);
    }

    private boolean mapIt(long offset, long byteCount) {
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

        long bufferStart = offset;
        int newSegments;
        int i = 0;
        long newFileLength = offset + segmentsToMap * longSegmentSize;
        try {
            // ugly remapping
            // http://stackoverflow.com/q/14011919/194609
            // This approach is probably problematic but a bit faster if done often.
            // Here we rely on the OS+file system that increasing the file
            // size has no effect on the old mappings!
            bufferStart += segments.size() * longSegmentSize;
            newSegments = segmentsToMap - segments.size();
            // rely on automatically increasing when mapping
            // raFile.setLength(newFileLength);
            for (; i < newSegments; i++) {
                segments.add(newByteBuffer(bufferStart, longSegmentSize));
                bufferStart += longSegmentSize;
            }
            return true;
        } catch (IOException ex) {
            // we could get an exception here if buffer is too small and area too large
            // e.g. I got an exception for the 65421th buffer (probably around 2**16 == 65536)
            throw new RuntimeException("Couldn't map buffer " + i + " of " + segmentsToMap + " with " + longSegmentSize
                    + " for " + name + " at position " + bufferStart + " for " + byteCount + " bytes with offset " + offset
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
                        allowWrites ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY, offset, byteCount);
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
    public boolean loadExisting() {
        if (segments.size() > 0)
            throw new IllegalStateException("already initialized");

        if (isClosed())
            throw new IllegalStateException("already closed");

        File file = new File(getFullName());
        if (!file.exists() || file.length() == 0)
            return false;

        initRandomAccessFile();
        try {
            long byteCount = readHeader(raFile);
            if (byteCount < 0)
                return false;

            mapIt(HEADER_OFFSET, byteCount - HEADER_OFFSET);
            return true;
        } catch (IOException ex) {
            throw new RuntimeException("Problem while loading " + getFullName(), ex);
        }
    }

    @Override
    public void flush() {
        if (isClosed())
            throw new IllegalStateException("already closed");

        try {
            for (MappedByteBuffer bb : segments) {
                bb.force();
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
        Helper.close(raFile);
    }

    @Override
    public final void setInt(long bytePos, int value) {
        int bufferIndex = (int) (bytePos >> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 4 > segmentSizeInBytes)
            throw new IllegalStateException("Padding required. Currently an int cannot be distributed over two segments. " + bytePos);
        ByteBuffer byteBuffer = segments.get(bufferIndex);
        synchronized (byteBuffer) {
            byteBuffer.putInt(index, value);
        }
    }

    @Override
    public final int getInt(long bytePos) {
        int bufferIndex = (int) (bytePos >> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 4 > segmentSizeInBytes)
            throw new IllegalStateException("Padding required. Currently an int cannot be distributed over two segments. " + bytePos);
        ByteBuffer byteBuffer = segments.get(bufferIndex);
        synchronized (byteBuffer) {
            return byteBuffer.getInt(index);
        }
    }

    @Override
    public final void setShort(long bytePos, short value) {
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        ByteBuffer byteBuffer = segments.get(bufferIndex);
        synchronized (byteBuffer) {
            if (index + 2 > segmentSizeInBytes) {
                ByteBuffer byteBufferNext = segments.get(bufferIndex + 1);
                synchronized (byteBufferNext) {
                    // special case if short has to be written into two separate segments
                    byteBuffer.put(index, (byte) value);
                    byteBufferNext.put(0, (byte) (value >>> 8));
                }
            } else {
                byteBuffer.putShort(index, value);
            }
        }
    }

    @Override
    public final short getShort(long bytePos) {
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        ByteBuffer byteBuffer = segments.get(bufferIndex);
        if (index + 2 > segmentSizeInBytes) {
            ByteBuffer byteBufferNext = segments.get(bufferIndex + 1);
            // never lock byteBuffer and byteBufferNext in a different order to avoid deadlocks (shouldn't happen)
            synchronized (byteBuffer) {
                synchronized (byteBufferNext) {
                    return (short) ((byteBufferNext.get(0) & 0xFF) << 8 | byteBuffer.get(index) & 0xFF);
                }
            }
        }
        synchronized (byteBuffer) {
            return byteBuffer.getShort(index);
        }
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        final int bufferIndex = (int) (bytePos >>> segmentSizePower);
        final int index = (int) (bytePos & indexDivisor);
        final int delta = index + length - segmentSizeInBytes;
        final ByteBuffer bb1 = segments.get(bufferIndex);
        synchronized (bb1) {
            bb1.position(index);
            if (delta > 0) {
                length -= delta;
                bb1.put(values, 0, length);
            } else {
                bb1.put(values, 0, length);
            }
        }
        if (delta > 0) {
            final ByteBuffer bb2 = segments.get(bufferIndex + 1);
            synchronized (bb2) {
                bb2.position(0);
                bb2.put(values, length, delta);
            }
        }
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        int delta = index + length - segmentSizeInBytes;
        final ByteBuffer bb1 = segments.get(bufferIndex);
        synchronized (bb1) {
            bb1.position(index);
            if (delta > 0) {
                length -= delta;
                bb1.get(values, 0, length);
            } else {
                bb1.get(values, 0, length);
            }
        }
        if (delta > 0) {
            final ByteBuffer bb2 = segments.get(bufferIndex + 1);
            synchronized (bb2) {
                bb2.position(0);
                bb2.get(values, length, delta);
            }
        }
    }

    @Override
    public void setByte(long bytePos, byte value) {
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        final ByteBuffer bb1 = segments.get(bufferIndex);
        synchronized (bb1) {
            bb1.position(index);
            bb1.put(value);
        }
    }

    @Override
    public byte getByte(long bytePos) {
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        final ByteBuffer bb1 = segments.get(bufferIndex);
        synchronized (bb1) {
            bb1.position(index);
            return bb1.get();
        }
    }

    @Override
    public long getCapacity() {
        long cap = 0;
        for (ByteBuffer bb : segments) {
            synchronized (bb) {
                cap += bb.capacity();
            }
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
        for (int i = from; i < to; i++) {
            ByteBuffer bb = segments.get(i);
            cleanMappedByteBuffer(bb);
            segments.set(i, null);
        }
    }

    @Override
    public DAType getType() {
        return DAType.MMAP;
    }
}
