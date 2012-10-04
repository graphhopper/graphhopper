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
package de.jetsli.graph.storage;

import de.jetsli.graph.util.BitUtil;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * This is an in-memory data structure but with the possibility to be stored on flush().
 *
 * @author Peter Karich
 */
public class RAMDataAccess extends AbstractDataAccess {

    private String id;
    private int[][] segments = new int[0][];
    private float increaseFactor = 1.5f;
    private boolean closed = false;
    private boolean store;
    private transient int segmentSizeIntsPower;
    private transient int indexDivisor;

    public RAMDataAccess() {
        this("", false);
    }

    public RAMDataAccess(String id) {
        this(id, false);
    }

    public RAMDataAccess(String id, boolean store) {
        this.id = id;
        this.store = store;
        if (id == null)
            throw new IllegalStateException("RAMDataAccess id cannot be null");
    }

    public RAMDataAccess setStore(boolean store) {
        this.store = store;
        return this;
    }

    @Override
    public void copyTo(DataAccess da) {
        if (da instanceof RAMDataAccess) {
            RAMDataAccess rda = (RAMDataAccess) da;
            // TODO we could reuse rda segments!
            rda.segments = new int[segments.length][];
            for (int i = 0; i < segments.length; i++) {
                int[] area = segments[i];
                rda.segments[i] = Arrays.copyOf(area, area.length);
            }
            rda.increaseFactor = increaseFactor;
            rda.setSegmentSize(segmentSize);
            // do leave id, store and close unchanged
        } else
            super.copyTo(da);
    }

    @Override
    public void createNew(long bytes) {
        if (segments.length > 0)
            throw new IllegalThreadStateException("already created");

        // initialize transient values
        setSegmentSize(segmentSize);
        ensureCapacity(Math.max(10, bytes));
    }

    @Override
    public void ensureCapacity(long bytes) {
        long todoBytes = bytes - capacity();
        if (todoBytes <= 0)
            return;

        int segmentsToCreate = (int) (todoBytes / segmentSize);
        if (todoBytes % segmentSize != 0)
            segmentsToCreate++;
        // System.out.println(id + " new segs:" + segmentsToCreate);
        int[][] newSegs = Arrays.copyOf(segments, segments.length + segmentsToCreate);
        for (int i = segments.length; i < newSegs.length; i++) {
            newSegs[i] = new int[1 << segmentSizeIntsPower];
        }
        segments = newSegs;
    }

    @Override
    public boolean loadExisting() {
        if (segments.length > 0)
            throw new IllegalStateException("already initialized");
        if (!store || closed)
            return false;
        try {
            RandomAccessFile raFile = new RandomAccessFile(id, "r");
            try {
                raFile.seek(0);
                if (!raFile.readUTF().equals(DATAACESS_MARKER))
                    return false;

                long byteCount = readHeader(raFile) - HEADER_OFFSET;
                byte[] bytes = new byte[segmentSize];
                raFile.seek(HEADER_OFFSET);
                // raFile.readInt() <- too slow                
                int segmentCount = (int) (byteCount / segmentSize);
                if (byteCount % segmentSize != 0)
                    segmentCount++;
                segments = new int[segmentCount][];
                for (int s = 0; s < segmentCount; s++) {
                    int read = raFile.read(bytes) / 4;
                    int area[] = new int[read];
                    for (int j = 0; j < read; j++) {
                        // TODO different system have different default byte order!
                        area[j] = BitUtil.toInt(bytes, j * 4);
                    }
                    segments[s] = area;
                }
                return true;
            } finally {
                raFile.close();
            }
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void flush() {
        if (closed)
            throw new IllegalStateException("already closed");
        if (!store)
            return;
        try {
            RandomAccessFile raFile = new RandomAccessFile(id, "rw");
            try {
                long len = capacity();
                writeHeader(raFile, len, segmentSize);
                raFile.seek(HEADER_OFFSET);
                // raFile.writeInt() <- too slow, so copy into byte array
                for (int s = 0; s < segments.length; s++) {
                    int area[] = segments[s];
                    int intLen = area.length;
                    byte[] byteArea = new byte[intLen * 4];
                    for (int i = 0; i < intLen; i++) {
                        // TODO different system have different default byte order!
                        BitUtil.fromInt(byteArea, area[i], i * 4);
                    }
                    raFile.write(byteArea);
                }
            } finally {
                raFile.close();
            }
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't store integers to " + id, ex);
        }
    }

    @Override
    public void setInt(long intIndex, int value) {
        int bufferIndex = (int) (intIndex >>> segmentSizeIntsPower);
        int index = (int) (intIndex & indexDivisor);
        segments[bufferIndex][(int) index] = value;
    }

    @Override
    public int getInt(long intIndex) {
        int bufferIndex = (int) (intIndex >>> segmentSizeIntsPower);
        int index = (int) (intIndex & indexDivisor);
        return segments[bufferIndex][(int) index];
    }

    @Override
    public void close() {
        super.close();
        segments = new int[0][];
        closed = true;
    }

    @Override
    public long capacity() {
        return getSegments() * segmentSize;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public int getSegments() {
        return segments.length;
    }

    @Override
    public DataAccess setSegmentSize(int bytes) {
        super.setSegmentSize(bytes);
        segmentSizeIntsPower = (int) (Math.log(segmentSize / 4) / Math.log(2));
        indexDivisor = segmentSize / 4 - 1;
        return this;
    }      
}
