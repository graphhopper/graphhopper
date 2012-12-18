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

import com.graphhopper.util.Helper;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author Peter Karich
 */
public abstract class AbstractDataAccess implements DataAccess {

    private static final int SEGMENT_SIZE_MIN = 1 << 7;
    private static final int SEGMENT_SIZE_DEFAULT = 1 << 20;
    // reserve some space for downstream usage (in classes using/exting this)
    protected static final int HEADER_OFFSET = 20 * 4 + 20;
    protected static final byte[] EMPTY = new byte[1024];
    protected int header[] = new int[(HEADER_OFFSET - 20) / 4];
    private final String location;
    protected int segmentSizeInBytes = SEGMENT_SIZE_DEFAULT;
    protected String name;

    public AbstractDataAccess(String name, String location) {
        this.name = name;
        if (!location.isEmpty() && !location.endsWith("/"))
            throw new IllegalArgumentException("Create DataAccess object via its corresponding Directory!");
        this.location = location;
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
        file.writeUTF("GH");
        // make changes to file format only with major version changes
        file.writeInt(getVersion());
        file.writeLong(length);
        file.writeInt(segmentSize);
        for (int i = 0; i < header.length; i++) {
            file.writeInt(header[i]);
        }
    }

    @Override
    public int getVersion() {
        return Helper.VERSION_FILE;
    }

    protected long readHeader(RandomAccessFile raFile) throws IOException {
        raFile.seek(0);
        if (raFile.length() == 0)
            return -1;
        String versionHint = raFile.readUTF();
        if (!"GH".equals(versionHint))
            throw new IllegalArgumentException("Not a GraphHopper file! Expected 'GH' as file marker but was " + versionHint);
        // use a separate version field
        int majorVersion = raFile.readInt();
        if (majorVersion != getVersion())
            throw new IllegalArgumentException("This GraphHopper file has the wrong version! "
                    + "Expected " + getVersion() + " but was " + majorVersion);
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
        segmentSizeInBytes = Math.max((int) Math.pow(2, tmp), SEGMENT_SIZE_MIN);
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

    @Override
    public void rename(String newName) {
        File file = new File(location + name);
        if (file.exists())
            try {
                if (!file.renameTo(new File(location + newName)))
                    throw new IllegalStateException("Couldn't rename this RAMDataAccess object to " + newName);
                name = newName;
            } catch (Exception ex) {
                throw new IllegalStateException("Couldn't rename this RAMDataAccess object!", ex);
            }
        else
            throw new IllegalStateException("File does not exist!? " + getFullName()
                    + " Make sure that you flushed before renaming. Otherwise it could make problems"
                    + " for memory mapped DataAccess objects");
    }

    protected boolean checkBeforeRename(String newName) {
        if (newName == null || newName.isEmpty())
            throw new IllegalArgumentException("newName mustn't be empty!");
        if (newName.equals(name))
            return false;
        if (new File(location + newName).exists())
            throw new IllegalArgumentException("file newName already exists!");
        return true;
    }
}
