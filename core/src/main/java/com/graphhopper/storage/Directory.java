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
import java.util.Collection;

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
     */
    ByteOrder getByteOrder();

    /**
     * Tries to find the object with that name if not existent it creates one and associates the
     * location with it. A name is unique in one Directory.
     */
    DataAccess find(String name);

    DataAccess find(String name, DAType type);

    /**
     * Renames the specified DataAccess object into one.
     */
    // DataAccess rename( DataAccess da, String newName );

    /**
     * Removes the specified object from the directory.
     */
    void remove(DataAccess da);

    /**
     * @return the default type of a newly created DataAccess object
     */
    DAType getDefaultType();

    /**
     * Removes all contained objects from the directory and releases its resources.
     */
    void clear();

    /**
     * Returns all created directories.
     */
    Collection<DataAccess> getAll();

    Directory create();
}
