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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.util.Arrays
import java.util.LinkedHashMap

/**
 * Writes an in-memory HashMap into a file on flush. Thread safe, see #743.
 *
 * @author Peter Karich
 */
class StorableProperties(val directory: Directory) {

    private val map = LinkedHashMap<String, String>()
    private val da: DataAccess

    init {
        // reduce size
        val segmentSize = 1 shl 15
        this.da = directory.create("properties", segmentSize)
    }

    @Synchronized
    fun loadExisting(): Boolean {
        if (!da.loadExisting())
            return false

        if (da.capacity > Integer.MAX_VALUE) {
            throw IllegalStateException("Properties file is too large: " + da.capacity)
        }
        val len = da.capacity.toInt()
        val bytes = ByteArray(len)
        val segmentSize = da.segmentSize
        var bytePos = 0
        while (bytePos < len) {
            val partLen = Math.min(bytes.size - bytePos, segmentSize)
            val part = ByteArray(partLen)
            da.getBytes(bytePos.toLong(), part, part.size)
            System.arraycopy(part, 0, bytes, bytePos, partLen)
            bytePos += segmentSize
        }
        try {
            loadProperties(map, StringReader(String(bytes, Helper.UTF_CS)))
            return true
        } catch (ex: IOException) {
            throw IllegalStateException(ex)
        }
    }

    @Synchronized
    fun flush() {
        val props = saveProperties(map)
        val bytes = props.toByteArray(Helper.UTF_CS)
        da.ensureCapacity(bytes.size.toLong())
        val segmentSize = da.segmentSize
        var bytePos = 0
        while (bytePos < bytes.size) {
            val partLen = Math.min(bytes.size - bytePos, segmentSize)
            val part = Arrays.copyOfRange(bytes, bytePos, bytePos + partLen)
            da.setBytes(bytePos.toLong(), part, part.size)
            bytePos += segmentSize
        }
        da.flush()
        // todo: would not be needed if the properties file used a format that is compatible with common text tools
        if (directory.defaultType.isStoring) {
            try {
                BufferedWriter(FileWriter(directory.location + "/properties.txt")).use { writer ->
                    writer.write(props)
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    @Synchronized
    fun remove(key: String): StorableProperties {
        map.remove(key)
        return this
    }

    @Synchronized
    fun putAll(externMap: Map<String, String>): StorableProperties {
        map.putAll(externMap)
        return this
    }

    @Synchronized
    fun put(key: String, value: String): StorableProperties {
        map[key] = value
        return this
    }

    /**
     * Before it saves this value it creates a string out of it.
     */
    @Synchronized
    fun put(key: String, value: Any): StorableProperties {
        if (key != Helper.toLowerCase(key))
            throw IllegalArgumentException("Do not use upper case keys ($key) for StorableProperties since 0.7")

        map[key] = value.toString()
        return this
    }

    @Synchronized
    fun get(key: String): String {
        if (key != Helper.toLowerCase(key))
            throw IllegalArgumentException("Do not use upper case keys ($key) for StorableProperties since 0.7")
        return map.getOrDefault(key, "")
    }

    @Synchronized
    fun getAll(): Map<String, String> = map

    @Synchronized
    fun close() {
        da.close()
    }

    @Synchronized
    fun isClosed(): Boolean = da.isClosed

    @Synchronized
    fun create(size: Long): StorableProperties {
        da.create(size)
        return this
    }

    @Synchronized
    fun getCapacity(): Long = da.capacity

    @Synchronized
    fun containsVersion(): Boolean {
        return map.containsKey("nodes.version") ||
                map.containsKey("edges.version") ||
                map.containsKey("geometry.version") ||
                map.containsKey("location_index.version") ||
                map.containsKey("string_index.version")
    }

    @Synchronized
    override fun toString(): String = da.toString()

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(StorableProperties::class.java)

        @JvmStatic
        @JvmName("saveProperties")
        internal fun saveProperties(map: Map<String, String>): String {
            val builder = StringBuilder()
            for (e in map.entries) {
                builder.append(e.key)
                builder.append('=')
                builder.append(e.value)
                builder.append('\n')
            }
            return builder.toString()
        }

        @JvmStatic
        @JvmName("loadProperties")
        @Throws(IOException::class)
        internal fun loadProperties(map: MutableMap<String, String>, tmpReader: Reader) {
            BufferedReader(tmpReader).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!
                    if (l.startsWith("//") || l.startsWith("#")) {
                        continue
                    }

                    if (Helper.isEmpty(l)) {
                        continue
                    }

                    val index = l.indexOf("=")
                    if (index < 0) {
                        LOGGER.warn("Skipping configuration at line:$l")
                        continue
                    }

                    val field = l.substring(0, index)
                    val value = l.substring(index + 1)
                    map[field.trim()] = value.trim()
                }
            }
        }
    }
}
