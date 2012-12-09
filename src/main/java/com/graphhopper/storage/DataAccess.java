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
 * Abstraction of the underlying datastructure. Current implementations are RAM and memory mapped
 * kind. After construction and before usage you'll have to call createNew or a successfully
 * Storable.loadExisting
 *
 * @author Peter Karich
 */
public interface DataAccess extends Storable {

    /**
     * The logical name of this object.
     */
    String getId();

    /**
     * The current folder it resides. This will be initialized from the Directory and is empty if it
     * is entirely in-memory.
     */
    String getLocation();

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

    int getSegments();

    int getVersion();
}
