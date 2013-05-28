/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

/**
 * Abstraction of the underlying datastructure with a unique id and location. To
 * ensure that the id is unique use a Directory.attach or findAttach, if you
 * don't need uniqueness call Directory.create. Current implementations are RAM
 * and memory mapped access.
 *
 * Life cycle: (1) object creation, (2) configuration (e.g. segment size), (3)
 * create or loadExisting, (4) usage, (5) close
 *
 * @author Peter Karich
 */
public interface DataAccess extends Storable<DataAccess> {

    /**
     * The logical identification of this object.
     */
    String name();

    /**
     * Renames the underlying DataAccess object. (Flushing shouldn't be
     * necessary before or afterwards)
     *
     * @throws IllegalStateException if a rename is not possible
     */
    void rename(String newName);

    /**
     * Set 4 bytes at position 'index' to the specified value
     */
    void setInt(long index, int value);

    /**
     * Get 4 bytes from position 'index'
     */
    int getInt(long index);

    /**
     * Set 4 bytes at the header space index to the specified value
     */
    void setHeader(int index, int value);

    /**
     * Get 4 bytes from the header at 'index'
     */
    int getHeader(int index);

    /**
     * The first time you use a DataAccess object after configuring it you need
     * to call this. After that first call you have to use ensureCapacity to
     * ensure that enough space is reserved.
     */
    @Override
    DataAccess create(long bytes);

    /**
     * Ensures the specified capacity. The first time you have to call create
     * instead.
     */
    void ensureCapacity(long bytes);

    /**
     * Reduces the allocate space to the specified bytes. Warning: it'll free
     * the space even if it is in use!
     */
    void trimTo(long bytes);

    /**
     * Copies the content from this object into the specified one.
     */
    DataAccess copyTo(DataAccess da);

    /**
     * In order to increase allocated space one needs to layout the underlying
     * storage in segments. This is how you can customize the size.
     */
    DataAccess segmentSize(int bytes);

    /**
     * @return the size of one segment in bytes
     */
    int segmentSize();

    /**
     * @return the number of segments.
     */
    int segments();
}
