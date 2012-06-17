/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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

import de.jetsli.graph.util.Helper;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Testing a memory mapped buffer with nearly unlimited storage capabilities on 64bit systems.
 *
 * @author Peter Karich, info@jetsli.de
 */
public class FatBuffer implements Closeable {

    private static final int MAPPING_SIZE = 1 << 24;
    private final int itemSize;
    private final RandomAccessFile raf;
    private final List<MappedByteBuffer> mappings = new ArrayList<MappedByteBuffer>();

    public FatBuffer(String filename, int size) throws IOException {
        this(filename, size, 4);
    }

    public FatBuffer(String filename, int size, int itemSize) throws FileNotFoundException {
        this.itemSize = itemSize;
        this.raf = new RandomAccessFile(filename, "rw");
        try {
            for (long offset = 0; offset < size; offset += MAPPING_SIZE) {
                long size2 = Math.min(size - offset, MAPPING_SIZE);
                MappedByteBuffer mbb = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, offset, size2);
                // ByteOrder.nativeOrder()
                mbb.order(ByteOrder.BIG_ENDIAN);
                mappings.add(mbb);
            }
        } catch (Exception e) {
            Helper.close(raf);
            throw new RuntimeException("Cannot create memory mapping for " + filename, e);
        }
    }

    public byte[] get(long index) {
        index *= itemSize;
        MappedByteBuffer mbb = mappings.get((int) (index / MAPPING_SIZE));
        byte[] bytes = new byte[itemSize];
        mbb.position((int) (index % MAPPING_SIZE));
        mbb.get(bytes);
        return bytes;
    }

    public void put(long index, byte[] bytes) {
        index *= itemSize;
        MappedByteBuffer mbb = mappings.get((int) (index / MAPPING_SIZE));
        mbb.position((int) (index % MAPPING_SIZE));
        mbb.put(bytes);
    }

    public void flush() {
        for (MappedByteBuffer mbb : mappings) {
            mbb.force();
        }
    }

    @Override public void close() {
        for (MappedByteBuffer mbb : mappings) {
            // necessary? mbb.force();
            clean(mbb);
        }
        Helper.close(raf.getChannel());
    }

    private void clean(MappedByteBuffer mapping) {
        Helper.cleanMappedByteBuffer(mapping);
    }
}
