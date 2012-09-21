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
import de.jetsli.graph.util.NotThreadSafe;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Peter Karich
 */
@NotThreadSafe
public class MMapDataAccess extends AbstractDataAccess {

    private String location;
    private RandomAccessFile raFile;
    private MappedByteBuffer bBuffer;
    private ByteOrder order;
    private float increaseFactor = 1.5f;
    private transient boolean closed = false;

    public MMapDataAccess(String location) {
        this.location = location;
        try {
            // raFile necessary for loadExisting and alloc
            raFile = new RandomAccessFile(location, "rw");
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Makes it possible to force the order. E.g. if we create the file on a host system and copy it
     * to a different like android. http://en.wikipedia.org/wiki/Endianness
     */
    public MMapDataAccess setByteOrder(ByteOrder order) {
        this.order = order;
        return this;
    }

    @Override
    public void ensureCapacity(long bytes) {
        if (!mapIt(HEADER_INT, bytes))
            throw new IllegalStateException("problem while file mapping " + location);
    }

    protected boolean mapIt(long start, long bytes) {
        try {
            if (bBuffer != null) {
                if (bytes <= bBuffer.capacity())
                    return true;

                bytes = (long) (increaseFactor * bytes);
                raFile.setLength(bytes + start);
            }
            bBuffer = raFile.getChannel().map(FileChannel.MapMode.READ_WRITE, start, bytes);
            if (order != null)
                bBuffer.order(order);
            return true;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean loadExisting() {
        try {
            if (closed)
                return false;
            raFile.seek(0);
            if (!raFile.readUTF().equals(DATAACESS_MARKER))
                return false;
            long bytes = raFile.readLong();
            if (mapIt(HEADER_INT, bytes))
                return true;
        } catch (Exception ex) {
        }
        return false;
    }

    @Override
    public DataAccess flush() {
        try {
            bBuffer.force();
            raFile.seek(0);
            raFile.writeUTF(DATAACESS_MARKER);
            raFile.writeLong(raFile.length());
            return this;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setInt(int intIndex, int value) {
        bBuffer.putInt(intIndex * 4, value);
    }

    @Override
    public int getInt(int intIndex) {
        bBuffer.capacity();
        return bBuffer.getInt(intIndex * 4);
    }

    @Override
    public DataAccess close() {
        super.close();
        Helper.close(raFile);
        closed = true;
        return this;
    }

    public static DataAccess load(String location, int byteHint) {
        DataAccess da = new MMapDataAccess(location);
        if (da.loadExisting())
            return da;
        da.ensureCapacity(byteHint);
        return da;
    }
}
