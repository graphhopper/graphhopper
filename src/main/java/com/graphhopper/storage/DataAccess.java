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

/**
 * Abstraction of the underlying datastructure with a unique id and location. To ensure that the id
 * is unique use a Directory.attach or findAttach, if you don't need uniqueness call
 * Directory.create. Current implementations are RAM and memory mapped access. To have a useable
 * instance do the following:
 *
 * <pre>
 * if(!storage.loadExisting())
 *    storage.createNew(bytes)
 * </pre>
 *
 * @author Peter Karich
 */
public interface DataAccess extends Storable {

    /**
     * The logical identification of this object.
     */
    String getName();

    /**
     * Renames the underlying DataAccess object. (Flushing shouldn't be necessary before or
     * afterwards)
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
     * The first time you use DataAccess you need to call this in order to allocate space for this
     * DataAccess object. After that use ensureCapacity
     */
    void createNew(long bytes);

    /**
     * Ensures the specified capacity. The first time call createNew.
     */
    void ensureCapacity(long bytes);

    /**
     * Reduces the allocate space to the specified bytes. Warning: it'll free the space even if it
     * is in use!
     */
    void trimTo(long bytes);

    /**
     * Copies the content from this object into the specified one.
     */
    DataAccess copyTo(DataAccess da);

    /**
     * In order to increase allocated space one needs to layout the underlying storage in segments.
     * This is how you can customize the size.
     */
    DataAccess setSegmentSize(int bytes);

    /**
     * @return the size of one segment in bytes
     */
    int getSegmentSize();

    int getSegments();

    int getVersion();

    /**
     * While copying it is a big bonus to release memory as early as possible. This method frees the
     * specified segment. Warning: this object is not functional after calling this method. All
     * methods (except this one) could fail! Use only if you know what you do!
     *
     * @return false if not supported
     */
    boolean releaseSegment(int segNumber);
}
