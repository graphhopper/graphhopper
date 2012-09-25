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

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author Peter Karich
 */
public abstract class AbstractDataAccess implements DataAccess {

    protected static final String DATAACESS_MARKER = "GHDA1";
    // reserve some space for downstream usage (in classes using/exting this)
    protected static final int HEADER_SPACE = 20 * 4 + 20;
    protected int header[] = new int[(HEADER_SPACE - 20) / 4];

    @Override
    public void close() {
        flush();
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
    protected void writeHeader(RandomAccessFile file, long length) throws IOException {
        file.seek(0);
        file.writeUTF(DATAACESS_MARKER);
        file.writeLong(length);
        for (int i = 0; i < header.length; i++) {
            file.writeInt(header[i]);
        }
    }

    protected long readHeader(RandomAccessFile raFile) throws IOException {
        long bytes = raFile.readLong();
        for (int i = 0; i < header.length; i++) {
            header[i] = raFile.readInt();
        }
        return bytes;
    }
}
