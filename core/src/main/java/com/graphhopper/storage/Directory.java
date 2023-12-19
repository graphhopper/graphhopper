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

import java.nio.ByteOrder;
import java.util.Map;

/**
 * Maintains a collection of DataAccess objects stored at the same location. One GraphStorage per
 * Directory as we need one to maintain one DataAccess object for nodes, edges and location2id
 * index.
 * <p>
 *
 * @author Peter Karich
 */
public interface Directory {
    /**
     * @return an id or location in the local filesystem.
     */
    String getLocation();

    /**
     * @return the order in which the data is stored
     * @deprecated
     */
    @Deprecated
    default ByteOrder getByteOrder() {
        return  ByteOrder.LITTLE_ENDIAN;
    }

    /**
     * Creates a new DataAccess object with the given name in the location of this Directory. Each name can only
     * be used once.
     */
    DataAccess create(String name);

    /**
     * @deprecated use {@link #create(String)} instead.
     */
    @Deprecated
    default DataAccess find(String name) {
        return create(name);
    }

    /**
     * @param segmentSize segment size in bytes or -1 to use the default of the corresponding DataAccess implementation
     */
    DataAccess create(String name, int segmentSize);

    /**
     * @deprecated use {@link #create(String, int)} instead.
     */
    @Deprecated
    default DataAccess find(String name, int segmentSize) {
        return create(name, segmentSize);
    }

    DataAccess create(String name, DAType type);

    /**
     * @deprecated use {@link #create(String, DAType)} instead.
     */
    @Deprecated
    default DataAccess find(String name, DAType type) {
        return create(name, type);
    }

    DataAccess create(String name, DAType type, int segmentSize);

    /**
     * @deprecated use {@link #create(String, DAType, int)} instead.
     */
    @Deprecated
    default DataAccess find(String name, DAType type, int segmentSize) {
        return create(name, type, segmentSize);
    }

    /**
     * Removes the specified object from the directory.
     */
    void remove(String name);

    /**
     * @deprecated use {@link #remove(String)} instead.
     */
    @Deprecated
    default void remove(DataAccess da) {
        remove(da.getName());
    }
    /**
     * @return the default type of a newly created DataAccess object
     */
    DAType getDefaultType();

    DAType getDefaultType(String dataAccess, boolean preferInts);

    /**
     * Removes all contained objects from the directory and releases its resources.
     */
    void clear();

    /**
     * Releases all allocated resources from the directory without removing backing files.
     */
    void close();

    Directory create();
}
