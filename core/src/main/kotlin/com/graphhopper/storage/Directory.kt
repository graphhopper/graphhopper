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
package com.graphhopper.storage

/**
 * Maintains a collection of DataAccess objects stored at the same location. One GraphStorage per
 * Directory as we need one to maintain one DataAccess object for nodes, edges and location2id
 * index.
 *
 * @author Peter Karich
 */
interface Directory {
    /**
     * @return an id or location in the local filesystem.
     */
    val location: String

    /**
     * Creates a new DataAccess object with the given name in the location of this Directory. Each name can only
     * be used once.
     */
    fun create(name: String): DataAccess

    /**
     * @param segmentSize segment size in bytes or -1 to use the default of the corresponding DataAccess implementation
     */
    fun create(name: String, segmentSize: Int): DataAccess

    fun create(name: String, type: DAType): DataAccess

    fun create(name: String, type: DAType, segmentSize: Int): DataAccess

    /**
     * Removes the specified object from the directory.
     */
    fun remove(name: String)

    /**
     * @return the default type of a newly created DataAccess object
     */
    val defaultType: DAType

    fun getDefaultType(dataAccess: String, preferInts: Boolean): DAType

    /**
     * Removes all contained objects from the directory and releases its resources.
     */
    fun clear()

    /**
     * Releases all allocated resources from the directory without removing backing files.
     */
    fun close()

    fun create(): Directory

    fun getDAs(): Map<String, DataAccess>
}
