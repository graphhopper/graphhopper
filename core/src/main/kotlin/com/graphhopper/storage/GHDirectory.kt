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

import com.graphhopper.util.Helper
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Implements some common methods for the subclasses.
 *
 * @author Peter Karich
 */
class GHDirectory @JvmOverloads constructor(
    location: String,
    private val typeFallback: DAType,
    private val defaultSegmentSize: Int = AbstractDataAccess.SEGMENT_SIZE_DEFAULT
) : Directory {

    final override val location: String

    // first rule matches => LinkedHashMap
    private val defaultTypes = LinkedHashMap<String, DAType>()
    private val mmapPreloads = LinkedHashMap<String, Int>()
    private val map = Collections.synchronizedMap(HashMap<String, DataAccess>())

    init {
        var loc = location
        if (Helper.isEmpty(loc))
            loc = File("").absolutePath

        if (!loc.endsWith("/"))
            loc += "/"

        this.location = loc
        val dir = File(this.location)
        if (dir.exists() && !dir.isDirectory)
            throw RuntimeException("file '$dir' exists but is not a directory")
    }

    /**
     * Configure the DAType (specified by the value) of a single DataAccess object (specified by the key). For "MMAP" you
     * can prepend "preload." to the name and specify a percentage which preloads the DataAccess into physical memory of
     * the specified percentage (only applied for load, not for import).
     * As keys can be patterns the order is important and the LinkedHashMap is forced as type.
     */
    fun configure(config: LinkedHashMap<String, String>): Directory {
        for (kv in config.entries) {
            val value = kv.value.trim()
            if (kv.key.startsWith("preload."))
                try {
                    val pattern = kv.key.substring("preload.".length)
                    mmapPreloads[pattern] = Integer.parseInt(value)
                } catch (ex: NumberFormatException) {
                    throw IllegalArgumentException("DataAccess " + kv.key + " has an incorrect preload value: " + value)
                }
            else {
                val pattern = kv.key
                defaultTypes[pattern] = DAType.fromString(value)
            }
        }
        return this
    }

    /**
     * Returns the preload value or 0 if no patterns match.
     * See [configure]
     */
    @JvmName("getPreload")
    internal fun getPreload(name: String): Int {
        for ((pattern, percentage) in mmapPreloads)
            if (name.matches(pattern.toRegex())) return percentage
        return 0
    }

    fun loadMMap() {
        for (da in map.values) {
            if (da !is MMapDataAccess)
                continue
            val preload = getPreload(da.name)
            if (preload > 0)
                da.load(preload)
        }
    }

    override fun create(name: String): DataAccess {
        return create(name, getDefault(name, typeFallback))
    }

    override fun create(name: String, segmentSize: Int): DataAccess {
        return create(name, getDefault(name, typeFallback), segmentSize)
    }

    private fun getDefault(name: String, typeFallback: DAType): DAType {
        for ((pattern, type) in defaultTypes)
            if (name.matches(pattern.toRegex())) return type
        return typeFallback
    }

    override fun create(name: String, type: DAType): DataAccess {
        return create(name, type, defaultSegmentSize)
    }

    override fun create(name: String, type: DAType, segmentSize: Int): DataAccess {
        if (name != Helper.toLowerCase(name))
            throw IllegalArgumentException("Since 0.7 DataAccess objects does no longer accept upper case names")

        if (map.containsKey(name))
        // we do not allow creating two DataAccess with the same name, because on disk there can only be one DA
        // per file name
            throw IllegalStateException("DataAccess $name has already been created")

        val da: DataAccess
        if (type.isInMemory) {
            da = if (type.isInteg) {
                if (type.isSingleSegment)
                    RAMInt1SegmentDataAccess(name, location, type.isStoring, segmentSize)
                else
                    RAMIntDataAccess(name, location, type.isStoring, segmentSize)
            } else
                RAMDataAccess(name, location, type.isStoring, segmentSize)
        } else if (type.isMMap) {
            da = MMapDataAccess(name, location, type.isAllowWrites, segmentSize)
        } else {
            throw IllegalArgumentException("DAType not supported $type")
        }

        map[name] = da
        return da
    }

    override fun close() {
        for (da in map.values) {
            da.close()
        }
        map.clear()
    }

    override fun clear() {
        for (da in map.values) {
            da.close()
            removeBackingFile(da, da.name)
        }
        map.clear()
    }

    override fun remove(name: String) {
        val old = map.remove(name)
            ?: throw IllegalStateException("Couldn't remove DataAccess: $name")

        old.close()
        removeBackingFile(old, name)
    }

    private fun removeBackingFile(da: DataAccess, name: String) {
        if (da.type.isStoring)
            Helper.removeDir(File(location + name))
    }

    override val defaultType: DAType
        get() = typeFallback

    /**
     * This method returns the default DAType of the specified DataAccess (as string). If preferInts is true then this
     * method returns e.g. RAM_INT if the type of the specified DataAccess is RAM.
     */
    override fun getDefaultType(dataAccess: String, preferInts: Boolean): DAType {
        val type = getDefault(dataAccess, typeFallback)
        if (preferInts && type.isInMemory)
            return if (type.isStoring) DAType.RAM_INT_STORE else DAType.RAM_INT
        return type
    }

    val isStoring: Boolean
        get() = typeFallback.isStoring

    override fun create(): Directory {
        if (isStoring)
            File(location).mkdirs()
        return this
    }

    override fun toString(): String = location

    override fun getDAs(): Map<String, DataAccess> = map
}
