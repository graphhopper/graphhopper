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

import java.io.Closeable;

/**
 * Life cycle: (1) object creation, (2) configuration (e.g. segment size), (3) create or
 * loadExisting, (4) usage and calling ensureCapacity if necessary, (5) close
 *
 * @author Peter Karich
 */
public interface DataAccess extends Closeable {
    /**
     * The logical identification of this object.
     */
    String getName();

    /**
     * Set 4 bytes at position 'bytePos' to the specified value
     */
    void setInt(long bytePos, int value);

    /**
     * Get 4 bytes from position 'bytePos'
     */
    int getInt(long bytePos);

    /**
     * Set 2 bytes at position 'index' to the specified value
     */
    void setShort(long bytePos, short value);

    /**
     * Get 2 bytes from position 'index'
     */
    short getShort(long bytePos);

    /**
     * Set bytes from position 'index' to the specified values
     */
    void setBytes(long bytePos, byte[] values, int length);

    /**
     * Get bytes from position 'index'
     *
     * @param values acts as output
     */
    void getBytes(long bytePos, byte[] values, int length);

    void setByte(long currentPointer, byte value);

    byte getByte(long currentPointer);

    /**
     * Set 4 bytes at the header space index to the specified value
     */
    void setHeader(int bytePos, int value);

    /**
     * Get 4 bytes from the header at 'index'
     */
    int getHeader(int bytePos);

    /**
     * The first time you use a DataAccess object after configuring it you need to call this method.
     * After that first call you have to use ensureCapacity to ensure that enough space is reserved.
     */
    DataAccess create(long bytes);

    /**
     * This method makes sure that the underlying data is written to the storage. Keep in mind that
     * a disc normally has an IO cache so that flush() is (less) probably not save against power
     * loses.
     */
    void flush();

    /**
     * This method makes sure that the underlying used resources are released. WARNING: it does NOT
     * flush on close!
     */
    @Override
    void close();

    boolean isClosed();

    /**
     * @return true if successfully loaded from persistent storage.
     */
    boolean loadExisting();

    /**
     * @return the allocated storage size in bytes
     */
    long getCapacity();

    /**
     * Ensures that the capacity of this object is at least the specified bytes. The first time you
     * have to call 'create' instead.
     * <p>
     *
     * @return true if size was increased
     * @see #create(long)
     */
    boolean ensureCapacity(long bytes);

    /**
     * @return the size of one segment in bytes
     */
    int getSegmentSize();

    /**
     * @return the number of segments.
     */
    int getSegments();

    /**
     * @return the data access type of this object.
     */
    DAType getType();
}
